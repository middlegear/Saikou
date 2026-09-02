package ani.saikou.settings

import android.content.Intent
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.updateLayoutParams
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import ani.saikou.R
import ani.saikou.databinding.ActivitySettingBinding
import ani.saikou.initActivity
import ani.saikou.loadData
import ani.saikou.navBarHeight
import ani.saikou.saveData
import ani.saikou.setSafeOnClickListener
import ani.saikou.settings.about.AboutSettingsActivity
import ani.saikou.settings.accounts.AccountsActivity
import ani.saikou.settings.anime.AnimeSettingsActivity
import ani.saikou.settings.common.CommonSettingsActivity
import ani.saikou.settings.manga.MangaSettingsActivity
import ani.saikou.settings.notifications.NotificationSettingsActivity
import ani.saikou.snackString
import ani.saikou.startMainActivity
import ani.saikou.statusBarHeight
import ani.saikou.updater.UpdateActivity

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding

    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            startMainActivity(this@SettingActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        setupLogoBehavior()
        setupNavigation()
        setupThemeSelection()
        setupRowClickListeners()
    }

    private fun setupLogoBehavior() {

        (binding.settingsLogo.drawable as? Animatable)?.start()

        val tipsArray = resources.getStringArray(R.array.tips)
        binding.settingsLogo.setSafeOnClickListener {
            (binding.settingsLogo.drawable as? Animatable)?.start()
            if (tipsArray.isNotEmpty()) {
                val randomTip = tipsArray[(Math.random() * tipsArray.size).toInt()]
                snackString(randomTip, this)
            }
        }

        binding.settingsLogo.setOnLongClickListener {
            UpdateActivity.launch(this, forceCheck = true)
            true
        }
    }
    private fun setupNavigation() {
        onBackPressedDispatcher.addCallback(this, restartMainActivity)

        binding.settingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupThemeSelection() {
        val uiSettings: UserInterfaceSettings = loadData("ui_settings", toast = false)
            ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }

        var previous: View = when (uiSettings.darkMode) {
            null -> binding.settingsUiAuto
            true -> binding.settingsUiDark
            false -> binding.settingsUiLight
        }
        previous.alpha = 1f

        fun updateThemeUI(mode: Boolean?, current: View) {
            previous.alpha = 0.33f
            previous = current
            current.alpha = 1f

            uiSettings.darkMode = mode
            saveData("ui_settings", uiSettings)

            when (mode) {
                true -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                false -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                null -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        binding.settingsUiAuto.setOnClickListener { updateThemeUI(null, it) }
        binding.settingsUiLight.setOnClickListener { updateThemeUI(false, it) }
        binding.settingsUiDark.setOnClickListener { updateThemeUI(true, it) }
    }

    private fun setupRowClickListeners() {
        fun navigateTo(destination: Class<*>) {
            startActivity(Intent(this, destination))
        }

        // 1. Account Row & Chevron
        val openAccount = View.OnClickListener { navigateTo(AccountsActivity::class.java) }
        binding.settingsAccountRow.setOnClickListener(openAccount)
        binding.btnAccountChevron.setOnClickListener(openAccount)

        // 2. Common Row & Chevron
        val openCommon = View.OnClickListener { navigateTo(CommonSettingsActivity::class.java) }
        binding.settingsCommonRow.setOnClickListener(openCommon)
        binding.btnCommonChevron.setOnClickListener(openCommon)

        // 3. Anime Row & Chevron
        val openAnime = View.OnClickListener { navigateTo(AnimeSettingsActivity::class.java) }
        binding.settingsAnimeRow.setOnClickListener(openAnime)
        binding.btnAnimeChevron.setOnClickListener(openAnime)

        // 4. Manga Row & Chevron
        val openManga = View.OnClickListener { navigateTo(MangaSettingsActivity::class.java) }
        binding.settingsMangaRow.setOnClickListener(openManga)
        binding.btnMangaChevron.setOnClickListener(openManga)

        // 5. Notifications Row & Chevron(fix notifications then enable this)
//        val openNotifications = View.OnClickListener { navigateTo(NotificationSettingsActivity::class.java) }
//        binding.settingsNotificationsRow.setOnClickListener(openNotifications)
//        binding.btnNotificationsChevron.setOnClickListener(openNotifications)

        // 6. Updater Row & Chevron
        val openAppUpdater = View.OnClickListener { navigateTo(UpdateActivity::class.java) }
        binding.settingsUpdaterRow.setOnClickListener(openAppUpdater)
        binding.btnUpdaterChevron.setOnClickListener(openAppUpdater)

        // 7. About Row & Chevron
        val openAbout = View.OnClickListener { navigateTo(AboutSettingsActivity::class.java) }
        binding.settingsAboutRow.setOnClickListener(openAbout)
        binding.btnAboutChevron.setOnClickListener(openAbout)
    }
}