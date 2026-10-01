import Foundation

struct Bookmark: Codable {
    let userId: UUID
    let tripId: UUID
    let createdAt: Date
}
