package com.sdvsync.cinderbox

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CinderboxLayoutEvents {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changed = changes.asSharedFlow()

    fun notifyChanged() {
        changes.tryEmit(Unit)
    }
}
