import CoreImage
import CoreVideo
import Foundation

/// Turns the frames of a WebM file into pictures with libvpx. Not thread-safe: use one decoder from one thread at a time.
final class WebMDecoder {
    let file: WebMFile
    private let video: WebMVideoTrack
    private var decoder: OpaquePointer?
    private var pool: CVPixelBufferPool?
    private var poolSize = (width: 0, height: 0, fullRange: false)

    init?(file: WebMFile, threads: Int = 2) {
        guard let video = file.video, let decoder = vpxdec_create(video.isVP9 ? 1 : 0, Int32(threads)) else { return nil }
        self.file = file
        self.video = video
        self.decoder = decoder
    }

    deinit { vpxdec_destroy(decoder) }

    /// Decodes the packet at `index`. Returns the picture it shows, or nil when it shows none (or cannot be decoded).
    /// The picture is in the NV12 layout the display layer and Core Image take directly.
    func decode(_ index: Int) -> CVPixelBuffer? {
        let packet = file.video(index)
        let data = file.bytes(of: packet)
        guard let base = data.baseAddress, vpxdec_decode(decoder, base, data.count) == 0 else { return nil }
        var picture = VpxPicture()
        guard vpxdec_picture(decoder, &picture) == 1 else { return nil }
        return makeBuffer(picture)
    }

    private func makeBuffer(_ picture: VpxPicture) -> CVPixelBuffer? {
        let width = Int(picture.width), height = Int(picture.height)
        let fullRange = picture.fullRange != 0
        if pool == nil || poolSize != (width, height, fullRange) {
            let attributes: [CFString: Any] = [
                kCVPixelBufferPixelFormatTypeKey: fullRange ? kCVPixelFormatType_420YpCbCr8BiPlanarFullRange : kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
                kCVPixelBufferWidthKey: width,
                kCVPixelBufferHeightKey: height,
                kCVPixelBufferIOSurfacePropertiesKey: [String: Any](),
            ]
            var made: CVPixelBufferPool?
            guard CVPixelBufferPoolCreate(nil, nil, attributes as CFDictionary, &made) == kCVReturnSuccess else { return nil }
            pool = made
            poolSize = (width, height, fullRange)
        }
        var output: CVPixelBuffer?
        guard let pool, CVPixelBufferPoolCreatePixelBuffer(nil, pool, &output) == kCVReturnSuccess, let buffer = output else { return nil }

        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let yBase = CVPixelBufferGetBaseAddressOfPlane(buffer, 0), let uvBase = CVPixelBufferGetBaseAddressOfPlane(buffer, 1) else { return nil }

        let yStride = CVPixelBufferGetBytesPerRowOfPlane(buffer, 0)
        for row in 0..<height {
            memcpy(yBase + row * yStride, picture.planes.0! + row * Int(picture.strides.0), width)
        }
        // The picture keeps blue and red in two separate planes; the buffer wants them interleaved in one.
        let uvStride = CVPixelBufferGetBytesPerRowOfPlane(buffer, 1)
        let chromaWidth = (width + 1) / 2, chromaHeight = (height + 1) / 2
        for row in 0..<chromaHeight {
            let u = picture.planes.1! + row * Int(picture.strides.1)
            let v = picture.planes.2! + row * Int(picture.strides.2)
            let out = (uvBase + row * uvStride).assumingMemoryBound(to: UInt8.self)
            for x in 0..<chromaWidth {
                out[2 * x] = u[x]
                out[2 * x + 1] = v[x]
            }
        }

        // Without this the colours of HD video come out slightly off, because standard and high definition differ.
        let matrix = picture.colorSpace == 1 ? kCVImageBufferYCbCrMatrix_ITU_R_601_4 : (picture.colorSpace == 2 ? kCVImageBufferYCbCrMatrix_ITU_R_709_2 : (height >= 720 ? kCVImageBufferYCbCrMatrix_ITU_R_709_2 : kCVImageBufferYCbCrMatrix_ITU_R_601_4))
        CVBufferSetAttachment(buffer, kCVImageBufferYCbCrMatrixKey, matrix, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_ITU_R_709_2, .shouldPropagate)
        return buffer
    }
}

/// Reads still pictures out of a WebM video for the library: the tile thumbnail and the frames of the live preview.
enum WebMFrames {
    private static let context = CIContext()

    /// One picture from near `time`, scaled to fit `edge` points on its longest side.
    static func image(of url: URL, near time: Double, edge: CGFloat) -> CGImage? {
        guard let file = WebMFile(url: url), let decoder = WebMDecoder(file: file) else { return nil }
        let target = min(max(0, time), max(0, file.duration - 0.1))
        var index = file.keyFrameIndex(atOrBefore: target)
        var last: CVPixelBuffer?
        let count = file.videoCount
        while index < count {
            if let buffer = decoder.decode(index) { last = buffer }
            // Decoding stops at the first picture at or after the wanted time.
            if file.video(index).time >= target, last != nil { break }
            index += 1
        }
        guard let last else { return nil }
        return cgImage(last, edge: edge)
    }

    /// `count` pictures one `1 / fps` apart starting at `start`, decoded in one pass from the nearest key frame.
    static func images(of url: URL, from start: Double, count: Int, fps: Double, edge: CGFloat, cancelled: () -> Bool = { false }) -> [CGImage] {
        guard let file = WebMFile(url: url), let decoder = WebMDecoder(file: file) else { return [] }
        var result: [CGImage] = []
        var next = start
        var index = file.keyFrameIndex(atOrBefore: start)
        let total = file.videoCount
        while index < total, result.count < count, !cancelled() {
            let time = file.video(index).time
            if let buffer = decoder.decode(index), time + 0.0005 >= next {
                next += 1 / fps
                if let image = cgImage(buffer, edge: edge) { result.append(image) }
            }
            index += 1
        }
        return result
    }

    private static func cgImage(_ buffer: CVPixelBuffer, edge: CGFloat) -> CGImage? {
        var image = CIImage(cvPixelBuffer: buffer)
        let scale = edge / max(image.extent.width, image.extent.height)
        if scale < 1 { image = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale)) }
        return context.createCGImage(image, from: image.extent)
    }
}
