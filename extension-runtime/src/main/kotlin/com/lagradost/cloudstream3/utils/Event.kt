package com.lagradost.cloudstream3.utils

class Event<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    operator fun plusAssign(listener: (T) -> Unit) {
        listeners.add(listener)
    }

    operator fun minusAssign(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    operator fun invoke(value: T) {
        for (listener in listeners) {
            try {
                listener.invoke(value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
