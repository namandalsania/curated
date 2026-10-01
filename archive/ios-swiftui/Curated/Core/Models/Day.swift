import Foundation

struct Day: Identifiable, Codable {
    let id: UUID
    let tripId: UUID
    var dayIndex: Int
    var date: Date?
}
