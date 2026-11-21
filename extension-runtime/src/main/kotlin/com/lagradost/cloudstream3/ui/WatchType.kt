package com.lagradost.cloudstream3.ui

enum class WatchType(val internalId: Int) {
    WATCHING(0),
    COMPLETED(1),
    ONHOLD(2),
    DROPPED(3),
    PLANTOWATCH(4),
    NONE(5);

    companion object {
        fun fromInternalId(id: Int?) = entries.find { value -> value.internalId == id } ?: NONE
    }
}

enum class SyncWatchType(val internalId: Int) {
    NONE(-1),
    WATCHING(0),
    COMPLETED(1),
    ONHOLD(2),
    DROPPED(3),
    PLANTOWATCH(4),
    REWATCHING(5);

    companion object {
        fun fromInternalId(id: Int?) = entries.find { value -> value.internalId == id } ?: NONE
    }
}
