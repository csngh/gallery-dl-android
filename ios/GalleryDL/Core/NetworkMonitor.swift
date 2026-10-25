import Network

/// Tells whether the phone is on an unmetered connection, for the Wi-Fi only option.
@MainActor
final class NetworkMonitor: ObservableObject {
    @Published private(set) var isUnmetered = true
    @Published private(set) var isConnected = true
    var onChange: (() -> Void)?

    private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self else { return }
                // Wi-Fi and wired networks are unmetered; cellular and personal hotspots are "expensive".
                self.isConnected = path.status == .satisfied
                self.isUnmetered = path.status == .satisfied && !path.isExpensive && !path.isConstrained
                self.onChange?()
            }
        }
        monitor.start(queue: DispatchQueue(label: "app.gallerydl.network"))
    }
}
