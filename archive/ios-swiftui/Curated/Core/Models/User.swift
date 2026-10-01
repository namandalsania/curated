import Foundation

struct User: Identifiable, Codable {
    let id: UUID
    var username: String
    var displayName: String
    var avatarURL: URL?
    var bio: String?
    let createdAt: Date
}
