import Foundation

enum TripStatus: String, Codable {
    case draft
    case published
}

enum TripVisibility: String, Codable {
    case `public`
    case unlisted
    case `private`
}

enum BudgetTag: String, Codable {
    case budget
    case midRange = "mid_range"
    case luxury
}

enum SeasonTag: String, Codable {
    case spring
    case summer
    case fall
    case winter
}

struct Trip: Identifiable, Codable {
    let id: UUID
    let authorId: UUID
    var title: String
    var destination: String
    var startDate: Date
    var endDate: Date
    var coverPhotoURL: URL?
    var budgetTag: BudgetTag?
    var seasonTag: SeasonTag?
    var status: TripStatus
    var visibility: TripVisibility
    let createdAt: Date
    var updatedAt: Date

    // Hydrated by queries for feed/list rendering — not columns on the row itself
    var stopCount: Int?
    var author: User?
}
