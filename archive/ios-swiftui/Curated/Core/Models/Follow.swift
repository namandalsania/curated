import Foundation

struct Follow: Codable {
    let followerId: UUID
    let followingId: UUID
    let createdAt: Date
}
