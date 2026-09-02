package ani.saikou.settings.notifications

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.saikou.R
import ani.saikou.databinding.ActivityMangaSettingsBinding
import ani.saikou.databinding.ActivityNotificationsSettingsBinding

import ani.saikou.initActivity
import ani.saikou.loadData
import ani.saikou.navBarHeight
import ani.saikou.saveData
import ani.saikou.statusBarHeight
import ani.saikou.subcriptions.Notifications

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNotificationsSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)


        binding.notificationsMainLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        // Back button navigation
        binding.notificationSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupNotificationSwitches()
    }

    private fun setupNotificationSwitches() {
        // Master Enable Notifications Switch
        val isMasterEnabled = loadData("subscription_checking_notifications") ?: true
        binding.settingsEnableNotifications.isChecked = isMasterEnabled

        binding.settingsEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveData("subscription_checking_notifications", isChecked)
            if (isChecked) {
                Notifications.createChannel(
                    this,
                    null,
                    "subscription_checking",
                    getString(R.string.checking_subscriptions),
                    false
                )
            } else {
                Notifications.deleteChannel(this, "subscription_checking")
            }
            updateChildSwitchStates(isChecked)
        }

        binding.settingsEnableNotifications.setOnLongClickListener {
            Notifications.openSettings(this, null)
            true
        }

        // Anime Release Notifications Switch
        binding.settingsAnimeReleaseNotifications.isChecked = loadData("anime_notifications") ?: true
        binding.settingsAnimeReleaseNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveData("anime_notifications", isChecked)
        }

        // Manga Release Notifications Switch
        binding.settingsMangaReleaseNotifications.isChecked = loadData("manga_notifications") ?: true
        binding.settingsMangaReleaseNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveData("manga_notifications", isChecked)
        }

        // Initial view state sync based on master toggle
        updateChildSwitchStates(isMasterEnabled)
    }

    private fun updateChildSwitchStates(isEnabled: Boolean) {
        binding.settingsAnimeReleaseNotifications.isEnabled = isEnabled
        binding.settingsMangaReleaseNotifications.isEnabled = isEnabled

        val alpha = if (isEnabled) 1.0f else 0.4f
        binding.settingsAnimeReleaseNotifications.alpha = alpha
        binding.settingsMangaReleaseNotifications.alpha = alpha
    }
}