package ani.saikou.media.anime.mpv.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import ani.saikou.media.anime.mpv.Decoder

object MpvPreferences {

    private const val PREFS_NAME = "mpv_prefs"
    private const val KEY_HWDEC = "pref_mpv_hwdec"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    fun getStoredDecoder(context: Context): Decoder? {
        val value = prefs(context).getString(KEY_HWDEC, null) ?: return null
        return Decoder.entries.firstOrNull { it.value == value }
    }

    fun setDecoder(context: Context, decoder: Decoder) {
        prefs(context).edit { putString(KEY_HWDEC, decoder.value) }
    }


    fun defaultDecoder(): Decoder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Decoder.HWPlus
        else Decoder.Auto

    fun resolveDecoder(context: Context): Decoder =
        getStoredDecoder(context) ?: defaultDecoder()
}