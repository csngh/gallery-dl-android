import AudioToolbox
import AVFoundation
import CoreMedia

/// Decodes the Opus sound of a WebM file with the decoder that comes with iOS, into 32-bit float samples at 48 kHz.
/// Mono and stereo only. Vorbis, the older WebM sound format, is not handled here.
final class WebMAudioDecoder {
    let channels: Int
    private var converter: AudioConverterRef?
    private let format: AudioStreamBasicDescription
    private var description: CMAudioFormatDescription?
    private static let noMoreInput: OSStatus = 0x6E6F6D6F
    /// An Opus packet is at most 120 ms, which is 5760 frames at 48 kHz.
    private let maxFrames = 5760

    init?(track: WebMAudioTrack) {
        guard track.codec == "A_OPUS", track.channels == 1 || track.channels == 2 else { return nil }
        channels = track.channels
        var input = AudioStreamBasicDescription(mSampleRate: 48_000, mFormatID: kAudioFormatOpus, mFormatFlags: 0, mBytesPerPacket: 0,
                                                mFramesPerPacket: 960, mBytesPerFrame: 0, mChannelsPerFrame: UInt32(track.channels),
                                                mBitsPerChannel: 0, mReserved: 0)
        var output = AudioStreamBasicDescription(mSampleRate: 48_000, mFormatID: kAudioFormatLinearPCM,
                                                 mFormatFlags: kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked,
                                                 mBytesPerPacket: UInt32(4 * track.channels), mFramesPerPacket: 1,
                                                 mBytesPerFrame: UInt32(4 * track.channels), mChannelsPerFrame: UInt32(track.channels),
                                                 mBitsPerChannel: 32, mReserved: 0)
        var made: AudioConverterRef?
        guard AudioConverterNew(&input, &output, &made) == noErr, let made else { return nil }
        converter = made
        format = output
        CMAudioFormatDescriptionCreate(allocator: nil, asbd: &output, layoutSize: 0, layout: nil, magicCookieSize: 0, magicCookie: nil,
                                       extensions: nil, formatDescriptionOut: &description)
        guard description != nil else { return nil }
    }

    deinit { if let converter { AudioConverterDispose(converter) } }

    /// Forgets what came before, for after a seek.
    func reset() { if let converter { AudioConverterReset(converter) } }

    private final class Input {
        var pointer: UnsafeMutableRawPointer
        var size: Int
        var consumed = false
        /// The converter keeps hold of this description while it decodes, so it needs an address that stays put.
        let packet = UnsafeMutablePointer<AudioStreamPacketDescription>.allocate(capacity: 1)
        init(pointer: UnsafeMutableRawPointer, size: Int) { self.pointer = pointer; self.size = size }
        deinit { packet.deallocate() }
    }

    /// Decodes one packet into a sample buffer shown at `time`.
    func decode(_ bytes: UnsafeBufferPointer<UInt8>, time: Double) -> CMSampleBuffer? {
        guard let converter, let base = bytes.baseAddress, let description else { return nil }
        let input = Input(pointer: UnsafeMutableRawPointer(mutating: base), size: bytes.count)
        let callback: AudioConverterComplexInputDataProc = { _, packets, buffers, descriptions, user in
            let input = Unmanaged<Input>.fromOpaque(user!).takeUnretainedValue()
            // Answering "nothing left" with zero packets and no error would end the stream for good; a made-up error code
            // tells the converter only that this call has no more input, so it can be used again for the next packet.
            if input.consumed { packets.pointee = 0; return WebMAudioDecoder.noMoreInput }
            input.consumed = true
            input.packet.pointee = AudioStreamPacketDescription(mStartOffset: 0, mVariableFramesInPacket: 0, mDataByteSize: UInt32(input.size))
            buffers.pointee.mNumberBuffers = 1
            buffers.pointee.mBuffers.mData = input.pointer
            buffers.pointee.mBuffers.mDataByteSize = UInt32(input.size)
            buffers.pointee.mBuffers.mNumberChannels = 0
            packets.pointee = 1
            descriptions?.pointee = input.packet
            return noErr
        }

        let frameBytes = 4 * channels
        let capacity = maxFrames * frameBytes
        let memory = UnsafeMutableRawPointer.allocate(byteCount: capacity, alignment: 16)
        defer { memory.deallocate() }
        var out = AudioBufferList(mNumberBuffers: 1, mBuffers: AudioBuffer(mNumberChannels: UInt32(channels), mDataByteSize: UInt32(capacity), mData: memory))
        var frames = UInt32(maxFrames)
        let status = withExtendedLifetime(input) { AudioConverterFillComplexBuffer(converter, callback, Unmanaged.passUnretained(input).toOpaque(), &frames, &out, nil) }
        guard status == noErr || status == Self.noMoreInput, frames > 0 else { return nil }

        let length = Int(frames) * frameBytes
        var block: CMBlockBuffer?
        guard CMBlockBufferCreateWithMemoryBlock(allocator: nil, memoryBlock: nil, blockLength: length, blockAllocator: nil, customBlockSource: nil,
                                                 offsetToData: 0, dataLength: length, flags: 0, blockBufferOut: &block) == noErr, let block,
              CMBlockBufferAssureBlockMemory(block) == noErr,
              CMBlockBufferReplaceDataBytes(with: memory, blockBuffer: block, offsetIntoDestination: 0, dataLength: length) == noErr else { return nil }
        var sample: CMSampleBuffer?
        guard CMAudioSampleBufferCreateReadyWithPacketDescriptions(allocator: nil, dataBuffer: block, formatDescription: description, sampleCount: CMItemCount(frames),
                                                                   presentationTimeStamp: CMTime(seconds: time, preferredTimescale: 48_000),
                                                                   packetDescriptions: nil, sampleBufferOut: &sample) == noErr else { return nil }
        return sample
    }
}
