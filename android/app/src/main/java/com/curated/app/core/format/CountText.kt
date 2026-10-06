package com.curated.app.core.format

/** The nouns the app counts, singular and plural. */
enum class Noun(val one: String, val many: String) {
    COMMENT("comment", "comments"),
    STOP("stop", "stops"),
    PLACE("place", "places"),
    DAY("day", "days"),
    PHOTO("photo", "photos"),
    LIKE("like", "likes"),
    TRIP("trip", "trips"),
    PERSON("person", "people")
}

/** "1 comment", "2 comments", "0 comments". */
fun countText(count: Int, noun: Noun): String = "$count ${if (count == 1) noun.one else noun.many}"
