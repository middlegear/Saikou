package ani.saikou.settings.player

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import ani.saikou.R
import ani.saikou.databinding.ActivityPlayerSettingsBinding
import ani.saikou.initActivity
import ani.saikou.loadData
import ani.saikou.media.Media
import ani.saikou.navBarHeight
import ani.saikou.others.getSerialized
import ani.saikou.parsers.Subtitle
import ani.saikou.saveData
import ani.saikou.snackString
import ani.saikou.statusBarHeight
import ani.saikou.toast
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

class PlayerSettingsActivity : AppCompatActivity() {
    lateinit var binding: ActivityPlayerSettingsBinding
    private val player = "player_settings"

    var media: Media? = null
    var subtitle: Subtitle? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        try {
            media = intent.getSerialized("media")
            subtitle = intent.getSerialized("subtitle")
        } catch (e: Exception) {
            toast(e.toString())
        }

        binding.playerSettingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        val settings = loadData<PlayerSettings>(player, toast = false)
            ?: PlayerSettings().apply { saveData(player, this) }

        binding.playerSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        //Time Stamp
        binding.playerSettingsTimeStamps.isChecked = settings.timeStampsEnabled
        binding.playerSettingsTimeStamps.setOnCheckedChangeListener { _, isChecked ->
            settings.timeStampsEnabled = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsTimeStampsProvider.isChecked =
            settings.useAlternativeTimestampProvider
        binding.playerSettingsTimeStampsProvider.setOnCheckedChangeListener { _, isChecked ->
            settings.useAlternativeTimestampProvider = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsShowTimeStamp.isChecked = settings.showTimeStampButton
        binding.playerSettingsShowTimeStamp.setOnCheckedChangeListener { _, isChecked ->
            settings.showTimeStampButton = isChecked
            saveData(player, settings)
        }


        //Auto
        binding.playerSettingsAutoSkipOpEd.isChecked = settings.autoSkipOPED
        binding.playerSettingsAutoSkipOpEd.setOnCheckedChangeListener { _, isChecked ->
            settings.autoSkipOPED = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsAutoPlay.isChecked = settings.autoPlay
        binding.playerSettingsAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            settings.autoPlay = isChecked
            saveData(player, settings)
        }
        binding.playerSettingsAutoSkip.isChecked = settings.autoSkipFiller
        binding.playerSettingsAutoSkip.setOnCheckedChangeListener { _, isChecked ->
            settings.autoSkipFiller = isChecked
            saveData(player, settings)
        }

        //Update Progress
        binding.playerSettingsAskUpdateProgress.isChecked = settings.askIndividual
        binding.playerSettingsAskUpdateProgress.setOnCheckedChangeListener { _, isChecked ->
            settings.askIndividual = isChecked
            saveData(player, settings)
        }
        binding.playerSettingsAskUpdateHentai.isChecked = settings.updateForH
        binding.playerSettingsAskUpdateHentai.setOnCheckedChangeListener { _, isChecked ->
            settings.updateForH = isChecked
            if (isChecked) snackString(getString(R.string.very_bold))
            saveData(player, settings)
        }
        binding.playerSettingsCompletePercentage.value =
            (settings.watchPercentage * 100).roundToInt().toFloat()
        binding.playerSettingsCompletePercentage.addOnChangeListener { _, value, _ ->
            settings.watchPercentage = value / 100
            saveData(player, settings)
        }

        //Behaviour
        binding.playerSettingsAlwaysContinue.isChecked = settings.alwaysContinue
        binding.playerSettingsAlwaysContinue.setOnCheckedChangeListener { _, isChecked ->
            settings.alwaysContinue = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsPauseVideo.isChecked = settings.focusPause
        binding.playerSettingsPauseVideo.setOnCheckedChangeListener { _, isChecked ->
            settings.focusPause = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsPlayerVerticalSwipeGestures.isChecked = settings.verticalSwipe
        binding.playerSettingsPlayerVerticalSwipeGestures.setOnCheckedChangeListener { _, isChecked ->
            settings.verticalSwipe = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsDoubleTapGestures.isChecked = settings.doubleTap
        binding.playerSettingsDoubleTapGestures.setOnCheckedChangeListener { _, isChecked ->
            settings.doubleTap = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsPlayerLongPressForwardGestures.isChecked = settings.holdToFastForward
        binding.playerSettingsPlayerLongPressForwardGestures.setOnCheckedChangeListener { _, isChecked ->
            settings.holdToFastForward = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsLoadingScreen.isChecked = settings.customLoadingScreen
        binding.playerSettingsLoadingScreen.setOnCheckedChangeListener { _, isChecked ->
            settings.customLoadingScreen = isChecked
            saveData(player, settings)
        }

        binding.playerSettingsSeekTime.value = settings.seekTime.toFloat()
        binding.playerSettingsSeekTime.addOnChangeListener { _, value, _ ->
            settings.seekTime = value.toInt()
            saveData(player, settings)
        }

        binding.exoSkipTime.setText(settings.skipTime.toString())
        binding.exoSkipTime.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.exoSkipTime.clearFocus()
            }
            false
        }
        binding.exoSkipTime.addTextChangedListener {
            val time = binding.exoSkipTime.text.toString().toIntOrNull()
            if (time != null) {
                settings.skipTime = time
                saveData(player, settings)
            }
        }

//        //Other
//        binding.playerSettingsPiP.apply {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                visibility = View.VISIBLE
//                isChecked = settings.pip
//                setOnCheckedChangeListener { _, isChecked ->
//                    settings.pip = isChecked
//                    saveData(player, settings)
//                }
//            } else visibility = View.GONE
//        }
//
//        binding.playerSettingsCast.isChecked = settings.cast
//        binding.playerSettingsCast.setOnCheckedChangeListener { _, isChecked ->
//            settings.cast = isChecked
//            saveData(player, settings)
//        }

        fun restartApp() {
            Snackbar.make(
                binding.root,
                R.string.restart_app, Snackbar.LENGTH_SHORT
            ).apply {
                val mainIntent =
                    Intent.makeRestartActivityTask(
                        context.packageManager.getLaunchIntentForPackage(
                            context.packageName
                        )!!.component
                    )
                setAction("Do it!") {
                    context.startActivity(mainIntent)
                    Runtime.getRuntime().exit(0)
                }
                show()
            }
        }

        fun toggleButton(button: Button, toggle: Boolean) {
            button.isClickable = toggle
            button.alpha = when (toggle) {
                true -> 1f
                false -> 0.5f
            }
        }

//        fun toggleSubOptions(isChecked: Boolean) {
//            toggleButton(binding.videoSubColorPrimary, isChecked)
//            toggleButton(binding.videoSubColorSecondary, isChecked)
//            toggleButton(binding.videoSubOutline, isChecked)
//            toggleButton(binding.videoSubFont, isChecked)
//            binding.subtitleFontSizeCard.isEnabled = isChecked
//            binding.subtitleFontSizeCard.isClickable = isChecked
//            binding.subtitleFontSizeCard.alpha = when (isChecked) {
//                true  -> 1f
//                false -> 0.5f
//            }
//            binding.subtitleFontSize.isEnabled = isChecked
//            binding.subtitleFontSize.isClickable = isChecked
//            binding.subtitleFontSize.alpha = when (isChecked) {
//                true  -> 1f
//                false -> 0.5f
//            }
//            ActivityPlayerSettingsBinding.bind(binding.root).subtitleFontSizeText.isEnabled = isChecked
//            ActivityPlayerSettingsBinding.bind(binding.root).subtitleFontSizeText.isClickable = isChecked
//            ActivityPlayerSettingsBinding.bind(binding.root).subtitleFontSizeText.alpha = when (isChecked) {
//                true  -> 1f
//                false -> 0.5f
//            }
//        }
//        binding.subSwitch.isChecked = settings.subtitles
//        binding.subSwitch.setOnCheckedChangeListener { _, isChecked ->
//            settings.subtitles = isChecked
//            saveData(player, settings)
//            toggleSubOptions(isChecked)
//            restartApp()
//        }
//        val colorsPrimary =
//            arrayOf("Black", "Dark Gray", "Gray", "Light Gray", "White", "Red", "Yellow", "Green", "Cyan", "Blue", "Magenta")
//        val primaryColorDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle(getString(R.string.primary_sub_color))
//        binding.videoSubColorPrimary.setOnClickListener {
//            primaryColorDialog.setSingleChoiceItems(colorsPrimary, settings.primaryColor) { dialog, count ->
//                settings.primaryColor = count
//                saveData(player, settings)
//                dialog.dismiss()
//            }.show()
//        }
//        val colorsSecondary = arrayOf(
//            "Black",
//            "Dark Gray",
//            "Gray",
//            "Light Gray",
//            "White",
//            "Red",
//            "Yellow",
//            "Green",
//            "Cyan",
//            "Blue",
//            "Magenta",
//            "Transparent"
//        )
//        val secondaryColorDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle(getString(R.string.outline_sub_color))
//        binding.videoSubColorSecondary.setOnClickListener {
//            secondaryColorDialog.setSingleChoiceItems(colorsSecondary, settings.secondaryColor) { dialog, count ->
//                settings.secondaryColor = count
//                saveData(player, settings)
//                dialog.dismiss()
//            }.show()
//        }
//        val typesOutline = arrayOf("Outline", "Shine", "Drop Shadow", "None")
//        val outlineDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle(getString(R.string.outline_type))
//        binding.videoSubOutline.setOnClickListener {
//            outlineDialog.setSingleChoiceItems(typesOutline, settings.outline) { dialog, count ->
//                settings.outline = count
//                saveData(player, settings)
//                dialog.dismiss()
//            }.show()
//        }
//        val colorsSubBackground = arrayOf(
//            "Transparent",
//            "Black",
//            "Dark Gray",
//            "Gray",
//            "Light Gray",
//            "White",
//            "Red",
//            "Yellow",
//            "Green",
//            "Cyan",
//            "Blue",
//            "Magenta"
//        )
//        val subBackgroundDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle(getString(R.string.outline_sub_color))
//        binding.videoSubColorBackground.setOnClickListener {
//            subBackgroundDialog.setSingleChoiceItems(colorsSubBackground, settings.subBackground) { dialog, count ->
//                settings.subBackground = count
//                saveData(player, settings)
//                dialog.dismiss()
//            }.show()
//        }
//
//        val colorsSubWindow = arrayOf(
//            "Transparent",
//            "Black",
//            "Dark Gray",
//            "Gray",
//            "Light Gray",
//            "White",
//            "Red",
//            "Yellow",
//            "Green",
//            "Cyan",
//            "Blue",
//            "Magenta"
//        )
//        val subWindowDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle(getString(R.string.outline_sub_color))
//        binding.videoSubColorWindow.setOnClickListener {
//            subWindowDialog.setSingleChoiceItems(colorsSubWindow, settings.subWindow) { dialog, count ->
//                settings.subWindow = count
//                saveData(player, settings)
//                dialog.dismiss()
//            }.show()
//        }
//        val fonts = arrayOf("Poppins Semi Bold", "Poppins Bold", "Poppins", "Poppins Thin")
//        val fontDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle(getString(R.string.subtitle_font))
//        binding.videoSubFont.setOnClickListener {
//            fontDialog.setSingleChoiceItems(fonts, settings.font) { dialog, count ->
//                settings.font = count
//                saveData(player, settings)
//                dialog.dismiss()
//            }.show()
//        }
//        binding.subtitleFontSize.setText(settings.fontSize.toString())
//        binding.subtitleFontSize.setOnEditorActionListener { _, actionId, _ ->
//            if (actionId == EditorInfo.IME_ACTION_DONE) {
//                binding.subtitleFontSize.clearFocus()
//            }
//            false
//        }
//        binding.subtitleFontSize.addTextChangedListener {
//            val size = binding.subtitleFontSize.text.toString().toIntOrNull()
//            if (size != null) {
//                settings.fontSize = size
//                saveData(player, settings)
//            }
//        }
//        toggleSubOptions(settings.subtitles)
    }
}