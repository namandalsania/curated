package com.curated.app.core.plan

import com.curated.app.core.model.PlanItem

/**
 * The rules for arranging a plan's items, as pure functions: each takes the
 * current items and returns the new full list, with positions renumbered
 * 0, 1, 2... within every day. The editor applies the result to the screen
 * at once and persists only what [changedSince] reports.
 */
object PlanOrdering {

    /** Items for days 1..[dayCount], each in position order. */
    fun byDay(items: List<PlanItem>, dayCount: Int): List<List<PlanItem>> =
        (1..dayCount).map { day -> items.filter { it.dayNumber == day }.sortedBy { it.position } }

    /** Swap an item with its neighbor in the same day; [delta] is -1 (up) or +1 (down). No-op at either end. */
    fun moveWithinDay(items: List<PlanItem>, itemId: String, delta: Int): List<PlanItem> {
        val item = items.firstOrNull { it.id == itemId } ?: return items
        val day = items.filter { it.dayNumber == item.dayNumber }.sortedBy { it.position }.toMutableList()
        val from = day.indexOfFirst { it.id == itemId }
        val to = from + delta
        if (to !in day.indices) return items
        day.add(to, day.removeAt(from))
        return replaceDay(items, item.dayNumber, day)
    }

    /** Move an item to the end of [targetDay]. */
    fun moveToDay(items: List<PlanItem>, itemId: String, targetDay: Int): List<PlanItem> {
        val item = items.firstOrNull { it.id == itemId } ?: return items
        if (item.dayNumber == targetDay) return items
        val source = items.filter { it.dayNumber == item.dayNumber && it.id != itemId }.sortedBy { it.position }
        val target = items.filter { it.dayNumber == targetDay }.sortedBy { it.position } + item.copy(dayNumber = targetDay)
        return replaceDay(replaceDay(items.filter { it.id != itemId }, item.dayNumber, source), targetDay, target)
    }

    /** Take an item out of the plan (the place stays saved). */
    fun remove(items: List<PlanItem>, itemId: String): List<PlanItem> {
        val item = items.firstOrNull { it.id == itemId } ?: return items
        val rest = items.filter { it.id != itemId }
        return replaceDay(rest, item.dayNumber, rest.filter { it.dayNumber == item.dayNumber }.sortedBy { it.position })
    }

    /**
     * Append new items to the end of [day], skipping places already in the
     * plan - including ones a collaborator added from their own saves.
     */
    fun append(items: List<PlanItem>, newItems: List<PlanItem>, day: Int): List<PlanItem> {
        val inPlan = items.mapTo(HashSet()) { it.placeKey }
        val fresh = newItems.filter { it.placeKey !in inPlan }.distinctBy { it.placeKey }
        if (fresh.isEmpty()) return items
        val existing = items.filter { it.dayNumber == day }.sortedBy { it.position }
        return replaceDay(items, day, existing + fresh.map { it.copy(dayNumber = day) })
    }

    /**
     * Delete [day]: its items leave the plan, and every later day shifts up
     * one so days stay numbered 1..n. Returns the new items.
     */
    fun removeDay(items: List<PlanItem>, day: Int): List<PlanItem> =
        items.filter { it.dayNumber != day }
            .map { if (it.dayNumber > day) it.copy(dayNumber = it.dayNumber - 1) else it }

    /**
     * What to write going from [old] to [new]: brand-new items, items whose
     * day or position changed, and removed ids. Moves are kept separate so
     * they can go through one atomic call that can only touch day/position.
     */
    fun changedSince(old: List<PlanItem>, new: List<PlanItem>): PlanChanges {
        val oldById = old.associateBy { it.id }
        val newIds = new.mapTo(HashSet()) { it.id }
        return PlanChanges(
            inserts = new.filter { it.id !in oldById },
            moves = new.filter { item ->
                val before = oldById[item.id]
                before != null && (before.dayNumber != item.dayNumber || before.position != item.position)
            },
            deletedIds = old.map { it.id }.filter { it !in newIds }
        )
    }

    /** Replace [day]'s items with [ordered], renumbering positions from 0. */
    private fun replaceDay(items: List<PlanItem>, day: Int, ordered: List<PlanItem>): List<PlanItem> =
        items.filter { it.dayNumber != day } + ordered.mapIndexed { index, it -> it.copy(dayNumber = day, position = index) }
}

data class PlanChanges(val inserts: List<PlanItem>, val moves: List<PlanItem>, val deletedIds: List<String>) {
    val isEmpty: Boolean get() = inserts.isEmpty() && moves.isEmpty() && deletedIds.isEmpty()
}
