import Foundation

enum StopCategory: String, Codable {
    case food
    case sight
    case hotel
    case transport
    case other
}

struct Stop: Identifiable, Codable {
    let id: UUID
    var dayId: UUID?
    let tripId: UUID
    var name: String
    var category: StopCategory
    var latitude: Double
    var longitude: Double
    var orderInDay: Int
    var caption: String?
    var cost: Decimal?
    var tips: String?
    var placeName: String?
    let createdAt: Date
}
