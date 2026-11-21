package com.lagradost.cloudstream3.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build

object Globals {
    var beneneCount = 0

    const val PHONE: Int = 0b001
    const val TV: Int = 0b010
    const val EMULATOR: Int = 0b100

    private var layoutId: Int = PHONE

    fun Context.updateTv() {
        layoutId = PHONE
    }

    fun isLandscape(): Boolean =
        Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun isLayout(flags: Int): Boolean {
        return (layoutId and flags) != 0
    }
}
