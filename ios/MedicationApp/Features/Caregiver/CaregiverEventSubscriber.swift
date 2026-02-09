import Foundation

struct DoseRecordEvent: Equatable, Decodable {
    let id: String
    let patientId: String
    let displayName: String
    let scheduledAt: Date
    let takenAt: Date
    let withinTime: Bool
    let medicationName: String?
    let isPrn: Bool?
}

struct InventoryAlertEvent: Equatable, Decodable {
    let id: String
    let patientId: String
    let medicationId: String
    let type: String
    let remaining: Int
    let threshold: Int
    let patientDisplayName: String?
    let medicationName: String?
}

@MainActor
final class CaregiverEventSubscriber: ObservableObject {
    @Published private(set) var latestEvent: DoseRecordEvent?
    @Published private(set) var latestInventoryAlert: InventoryAlertEvent?

    private let sessionStore: SessionStore
    private let decoder: JSONDecoder
    private var webSocketTask: URLSessionWebSocketTask?
    private var listenTask: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?
    private var messageRef = 0

    init(sessionStore: SessionStore) {
        self.sessionStore = sessionStore
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        self.decoder = decoder
    }

    func start() {
        guard isAuthorized else {
            print("CaregiverEventSubscriber: start() blocked - not authorized")
            return
        }
        guard webSocketTask == nil else {
            print("CaregiverEventSubscriber: start() blocked - already running")
            return
        }
        guard let config = supabaseConfig() else {
            print("CaregiverEventSubscriber: missing SUPABASE config")
            return
        }
        guard let accessToken = caregiverAccessToken else {
            print("CaregiverEventSubscriber: missing caregiver access token")
            handleUnauthorized()
            return
        }
        guard let websocketURL = websocketURL(for: config) else { return }
        print("CaregiverEventSubscriber: start() connect \(websocketURL)")
        var request = URLRequest(url: websocketURL)
        request.addValue(config.anonKey, forHTTPHeaderField: "apikey")
        request.addValue("Bearer \(config.anonKey)", forHTTPHeaderField: "Authorization")
        let task = URLSession.shared.webSocketTask(with: request)
        webSocketTask = task
        task.resume()
        listenTask = Task { @MainActor [weak self] in
            await self?.receiveMessages()
        }
        heartbeatTask = Task { @MainActor [weak self] in
            await self?.sendHeartbeatLoop()
        }
        Task { @MainActor [weak self] in
            await self?.sendJoin(accessToken: accessToken)
        }
    }

    func stop() {
        listenTask?.cancel()
        heartbeatTask?.cancel()
        listenTask = nil
        heartbeatTask = nil
        if let task = webSocketTask {
            task.cancel(with: .goingAway, reason: nil)
        }
        webSocketTask = nil
    }

    func handleIncomingPayload(_ data: Data) {
        guard isAuthorized else { return }
        if let event = try? decoder.decode(DoseRecordEvent.self, from: data) {
            handleIncomingEvent(event)
        } else if let event = try? decoder.decode(InventoryAlertEvent.self, from: data) {
            handleIncomingInventoryEvent(event)
        }
    }

    func handleIncomingEvent(_ event: DoseRecordEvent) {
        guard isAuthorized else { return }
        latestEvent = event
    }

    func handleIncomingInventoryEvent(_ event: InventoryAlertEvent) {
        guard isAuthorized else { return }
        latestInventoryAlert = event
    }

    func handleUnauthorized() {
        latestEvent = nil
        latestInventoryAlert = nil
        stop()
    }

    func resetForRevokedAccess() {
        latestEvent = nil
        latestInventoryAlert = nil
    }

    private var isAuthorized: Bool {
        sessionStore.mode == .caregiver && sessionStore.caregiverToken != nil
    }

    private var caregiverAccessToken: String? {
        guard let token = sessionStore.caregiverToken else { return nil }
        if token.hasPrefix("caregiver-") {
            return String(token.dropFirst("caregiver-".count))
        }
        return token
    }

    private func receiveMessages() async {
        while let task = webSocketTask, !Task.isCancelled {
            do {
                let message = try await task.receive()
                handle(message)
            } catch {
                stop()
                return
            }
        }
    }

    private func handle(_ message: URLSessionWebSocketTask.Message) {
        switch message {
        case .string(let text):
            handlePayload(text.data(using: .utf8))
        case .data(let data):
            handlePayload(data)
        @unknown default:
            break
        }
    }

    private func handlePayload(_ data: Data?) {
        guard let data else { return }
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let event = object["event"] as? String else { return }
        print("CaregiverEventSubscriber: event=\(event)")
        if event == "phx_reply" {
            if let payload = object["payload"] as? [String: Any],
               let status = payload["status"] as? String,
               status != "ok" {
                print("CaregiverEventSubscriber: phx_reply non-ok status=\(status)")
                handleUnauthorized()
            }
            return
        }
        if event == "phx_error" {
            print("CaregiverEventSubscriber: phx_error received, stopping subscriber")
            handleUnauthorized()
            return
        }
        if event == "postgres_changes" {
            if let record = extractRecord(from: object) {
                print("CaregiverEventSubscriber: record=\(record)")
                if let recordData = try? JSONSerialization.data(withJSONObject: record) {
                    if let event = try? decoder.decode(DoseRecordEvent.self, from: recordData) {
                        print("CaregiverEventSubscriber: decoded DoseRecordEvent id=\(event.id) withinTime=\(event.withinTime) isPrn=\(event.isPrn ?? false) medicationName=\(event.medicationName ?? "nil")")
                        handleIncomingEvent(event)
                        return
                    }
                    if let event = try? decoder.decode(InventoryAlertEvent.self, from: recordData) {
                        print("CaregiverEventSubscriber: decoded InventoryAlertEvent id=\(event.id) type=\(event.type)")
                        handleIncomingInventoryEvent(event)
                        return
                    }
                }
                if let event = parseDoseRecordEvent(from: record) {
                    print("CaregiverEventSubscriber: parsed DoseRecordEvent id=\(event.id) withinTime=\(event.withinTime) isPrn=\(event.isPrn ?? false) medicationName=\(event.medicationName ?? "nil")")
                    handleIncomingEvent(event)
                } else {
                    print("CaregiverEventSubscriber: failed to parse record payload")
                }
            }
        }
    }

    private func parseDoseRecordEvent(from record: [String: Any]) -> DoseRecordEvent? {
        guard let id = record["id"] as? String,
              let patientId = record["patientId"] as? String,
              let displayName = record["displayName"] as? String,
              let scheduledAtRaw = record["scheduledAt"] as? String,
              let takenAtRaw = record["takenAt"] as? String,
              let scheduledAt = parseDate(scheduledAtRaw),
              let takenAt = parseDate(takenAtRaw) else {
            return nil
        }
        let withinTimeValue = record["withinTime"]
        let withinTime = (withinTimeValue as? Bool)
            ?? (withinTimeValue as? NSNumber)?.boolValue
            ?? false
        let medicationName = record["medicationName"] as? String
        let isPrnValue = record["isPrn"]
        let isPrn = (isPrnValue as? Bool) ?? (isPrnValue as? NSNumber)?.boolValue
        return DoseRecordEvent(
            id: id,
            patientId: patientId,
            displayName: displayName,
            scheduledAt: scheduledAt,
            takenAt: takenAt,
            withinTime: withinTime,
            medicationName: medicationName,
            isPrn: isPrn
        )
    }

    private func parseDate(_ value: String) -> Date? {
        if let date = Self.isoFormatter.date(from: value) {
            return date
        }
        if !value.contains("Z") && !value.contains("+") {
            if let date = Self.isoFormatter.date(from: "\(value)Z") {
                return date
            }
            if let date = Self.noZoneFormatter.date(from: value) {
                return date
            }
            if let date = Self.noZoneFormatterNoFraction.date(from: value) {
                return date
            }
        }
        return Self.noZoneFormatter.date(from: value) ?? Self.noZoneFormatterNoFraction.date(from: value)
    }

    private static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let noZoneFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.posixLocale
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
        return formatter
    }()

    private static let noZoneFormatterNoFraction: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.posixLocale
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter
    }()

    private func extractRecord(from payload: [String: Any]) -> [String: Any]? {
        if let payload = payload["payload"] as? [String: Any] {
            if let record = payload["record"] as? [String: Any] {
                return record
            }
            if let data = payload["data"] as? [String: Any],
               let record = data["record"] as? [String: Any] {
                return record
            }
        }
        return nil
    }

    private func sendJoin(accessToken: String) async {
        let doseRecordJoin: [String: Any] = [
            "topic": "realtime:public:DoseRecordEvent",
            "event": "phx_join",
            "payload": [
                "config": [
                    "broadcast": ["self": false],
                    "presence": ["key": ""],
                    "postgres_changes": [
                        ["event": "INSERT", "schema": "public", "table": "DoseRecordEvent"]
                    ]
                ],
                "access_token": accessToken
            ],
            "ref": nextRef()
        ]
        await send(message: doseRecordJoin)

        let inventoryJoin: [String: Any] = [
            "topic": "realtime:public:inventory_alert_events",
            "event": "phx_join",
            "payload": [
                "config": [
                    "broadcast": ["self": false],
                    "presence": ["key": ""],
                    "postgres_changes": [
                        ["event": "INSERT", "schema": "public", "table": "inventory_alert_events"]
                    ]
                ],
                "access_token": accessToken
            ],
            "ref": nextRef()
        ]
        await send(message: inventoryJoin)
    }

    private func sendHeartbeatLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(AppConstants.websocketHeartbeatInterval))
            await sendHeartbeat()
        }
    }

    private func sendHeartbeat() async {
        let heartbeatPayload: [String: Any] = [
            "topic": "phoenix",
            "event": "heartbeat",
            "payload": [:],
            "ref": nextRef()
        ]
        await send(message: heartbeatPayload)
    }

    private func send(message: [String: Any]) async {
        guard let task = webSocketTask else { return }
        guard let data = try? JSONSerialization.data(withJSONObject: message, options: []),
              let text = String(data: data, encoding: .utf8) else { return }
        do {
            try await task.send(.string(text))
        } catch {
            stop()
        }
    }

    private func nextRef() -> String {
        messageRef += 1
        return String(messageRef)
    }

    private func websocketURL(for config: SupabaseConfig) -> URL? {
        let realtimeURL = config.url.appendingPathComponent("realtime/v1/websocket")
        var components = URLComponents(url: realtimeURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "apikey", value: config.anonKey),
            URLQueryItem(name: "vsn", value: "1.0.0")
        ]
        return components?.url
    }

    private func supabaseConfig() -> SupabaseConfig? {
        let env = ProcessInfo.processInfo.environment
        let info = Bundle.main.infoDictionary
        let urlString = env["SUPABASE_URL"] ?? info?["SUPABASE_URL"] as? String
        let anonKey = env["SUPABASE_ANON_KEY"] ?? info?["SUPABASE_ANON_KEY"] as? String
        guard let urlString, let anonKey, let url = URL(string: urlString) else {
            return nil
        }
        return SupabaseConfig(url: url, anonKey: anonKey)
    }
}

private struct SupabaseConfig {
    let url: URL
    let anonKey: String
}
