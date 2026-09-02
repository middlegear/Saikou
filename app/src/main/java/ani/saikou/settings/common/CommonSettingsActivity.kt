package ani.saikou.settings.common

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import ani.saikou.R
import ani.saikou.databinding.ActivityCommonSettingsBinding
import ani.saikou.initActivity
import ani.saikou.initializeNetwork
import ani.saikou.loadData
import ani.saikou.navBarHeight
import ani.saikou.saveData
import ani.saikou.settings.UserInterfaceSettings
import ani.saikou.settings.UserInterfaceSettingsActivity
import ani.saikou.snackString
import ani.saikou.startMainActivity
import ani.saikou.statusBarHeight

class CommonSettingsActivity : AppCompatActivity() {

    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = startMainActivity(this@CommonSettingsActivity)
    }

    private lateinit var binding: ActivityCommonSettingsBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCommonSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        binding.commonMainLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        onBackPressedDispatcher.addCallback(this, restartMainActivity)

        binding.commonSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Startup Tab Selection
        val uiSettings: UserInterfaceSettings = loadData("ui_settings", toast = false)
            ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }

        var previousStartTab: View = when (uiSettings.defaultStartUpTab) {
            0 -> binding.uiSettingsAnime
            1 -> binding.uiSettingsHome
            2 -> binding.uiSettingsManga
            else -> binding.uiSettingsHome
        }
        previousStartTab.alpha = 1f

        fun setStartupTab(mode: Int, current: View) {
            previousStartTab.alpha = 0.33f
            previousStartTab = current
            current.alpha = 1f
            uiSettings.defaultStartUpTab = mode
            saveData("ui_settings", uiSettings)
            initActivity(this)
        }

        binding.uiSettingsAnime.setOnClickListener { setStartupTab(0, it) }
        binding.uiSettingsHome.setOnClickListener { setStartupTab(1, it) }
        binding.uiSettingsManga.setOnClickListener { setStartupTab(2, it) }

        // Navigation to User Interface Sub-Settings
        binding.settingsUi.setOnClickListener {
            startActivity(Intent(this, UserInterfaceSettingsActivity::class.java))
        }

        // DNS Selection
        val dnsProviders = listOf("None", "Google", "Cloudflare", "AdGuard")
        val currentDnsIndex = loadData<Int>("settings_dns") ?: 0
        if (currentDnsIndex in dnsProviders.indices) {
            binding.settingsDns.setText(dnsProviders[currentDnsIndex], false)
        }
        binding.settingsDns.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, dnsProviders))
        binding.settingsDns.setOnItemClickListener { _, _, position, _ ->
            saveData("settings_dns", position)
            initializeNetwork(this)
            binding.settingsDns.clearFocus()
        }

        // Download Manager Chooser
        val managers = arrayOf("Default", "1DM", "ADM")
        val downloadManagerDialog = AlertDialog.Builder(this, R.style.DialogTheme).setTitle("Download Manager")
        var downloadManager = loadData<Int>("settings_download_manager") ?: 0

        binding.settingsDownloadManager.setOnClickListener {
            downloadManagerDialog.setSingleChoiceItems(managers, downloadManager) { dialog, count ->
                downloadManager = count
                saveData("settings_download_manager", downloadManager)
                dialog.dismiss()
            }.show()
        }

        // External SD Storage Switch
        binding.settingsDownloadInSd.isChecked = loadData("sd_dl") ?: false
        binding.settingsDownloadInSd.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val arrayOfFiles = ContextCompat.getExternalFilesDirs(this, null)
                if (arrayOfFiles.size > 1 && arrayOfFiles[1] != null) {
                    saveData("sd_dl", true)
                } else {
                    binding.settingsDownloadInSd.isChecked = false
                    saveData("sd_dl", false)
                    snackString(getString(R.string.noSdFound))
                }
            } else {
                saveData("sd_dl", false)
            }
        }

        // Continue Media Switch
        binding.settingsContinueMedia.isChecked = loadData("continue_media") ?: true
        binding.settingsContinueMedia.setOnCheckedChangeListener { _, isChecked ->
            saveData("continue_media", isChecked)
        }

        // Recently Updated Filter Switch
        binding.settingsRecentlyListOnly.isChecked = loadData("recently_list_only") ?: false
        binding.settingsRecentlyListOnly.setOnCheckedChangeListener { _, isChecked ->
            saveData("recently_list_only", isChecked)
        }
    }
}