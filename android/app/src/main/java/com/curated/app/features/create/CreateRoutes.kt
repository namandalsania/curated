package com.curated.app.features.create

object CreateRoutes {
    const val GRAPH = "create_graph"
    const val NEW_TRIP = "create/new_trip"
    const val LIVE_START = "create/live_start"
    const val IMPORT_REVIEW = "create/import_review"
    const val IMPORT_PHOTOS = "create/import_photos"
    const val BUILDER = "create/builder"
    const val ADD_PLACE_PATTERN = "create/add_place/{dayIndex}"
    const val PUBLISH = "create/publish"
    const val LIVE = "create/live"
    const val POST_DAY_PATTERN = "create/post_day/{dayIndex}"

    fun addPlace(dayIndex: Int) = "create/add_place/$dayIndex"

    /** Opens an existing draft straight in the builder. */
    fun resumeDraft(tripId: String) = "create/builder?tripId=$tripId"

    /** Opens a live trip. */
    fun live(tripId: String) = "$LIVE?tripId=$tripId"

    fun postDay(dayIndex: Int) = "create/post_day/$dayIndex"
}
