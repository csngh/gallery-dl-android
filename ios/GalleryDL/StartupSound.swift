import AVFoundation

/// The short download sound (res/raw/splash_download.wav). The ambient category follows the silent switch and
/// mixes with other audio, so it never interrupts music and stays quiet when the phone is on silent.
final class StartupSound {
    static let shared = StartupSound()
    private var player: AVAudioPlayer?

    func play() {
        guard let url = Bundle.main.url(forResource: "splash_download", withExtension: "wav") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.prepareToPlay()
        player?.play()
    }
}
