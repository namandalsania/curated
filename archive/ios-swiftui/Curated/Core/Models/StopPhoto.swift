import Foundation

struct StopPhoto: Identifiable, Codable {
    let id: UUID
    let stopId: UUID
    var storagePath: String
    var takenAt: Date?
    var orderIndex: Int
    let createdAt: Date
}
