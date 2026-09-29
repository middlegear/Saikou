package ani.saikou.torrserver

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import ani.saikou.R
import ani.saikou.databinding.ActivityTorrentSettingsBinding
import ani.saikou.loadData
import ani.saikou.saveData
import ani.saikou.torrserver.utils.TorrentProfile
import ani.saikou.torrserver.utils.TorrentSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TorrServerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTorrentSettingsBinding
    private val torrentKey = TorrServerService.TORRENT_SETTINGS_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTorrentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var settings = loadData<TorrentSettings>(torrentKey, toast = false) ?: TorrentSettings().apply {
            saveData(torrentKey, this)
        }

        if (!settings.enableTorrentServer || !settings.enableDHT) {
            settings.enableTorrentServer = true
            settings.enableDHT = true
            saveData(torrentKey, settings)
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
        binding.torrentSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        updateUiFromSettings(settings)


        binding.torrentSubSettingsContainer.visibility = View.VISIBLE



        binding.torrentProfile.setOnClickListener {
            showProfileSelectionDialog(settings) { updated ->
                settings = updated
                updateUiFromSettings(settings)
            }
        }

        binding.torrentServerPort.setOnClickListener {
            showPortInputDialog(settings)
        }

        binding.torrentProxyUrl.setOnClickListener {
            showProxyUrlInputDialog(settings)
        }

        binding.torrentEnableEncryption.setOnCheckedChangeListener { _, isChecked ->
            settings.enableEncryption = isChecked
            markAsCustomAndSave(settings)
        }

        binding.torrentEnableStats.setOnCheckedChangeListener { _, isChecked ->
            settings.enableStatics = isChecked
            saveData(torrentKey, settings)
            TorrServerService.startOrStop(this, settings)
        }
    }

    private fun updateUiFromSettings(settings: TorrentSettings) {
        binding.torrentProfileValue.text = settings.profile.displayName
        binding.torrentServerPortValue.text = settings.serverPort.toString()
        binding.torrentProxyUrlValue.text = settings.proxyUrl.ifEmpty { getString(R.string.torrent_proxy_url_default) }
        binding.torrentEnableEncryption.isChecked = settings.enableEncryption
        binding.torrentEnableStats.isChecked = settings.enableStatics
    }

    private fun showPortInputDialog(settings: TorrentSettings) {
        val input = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(settings.serverPort.toString())
            setSingleLine()
        }

        val textInputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.torrent_server_port)
            addView(input)
        }

        val container = FrameLayout(this).apply {
            val paddingHorizontal = (24 * resources.displayMetrics.density).toInt()
            val paddingTop = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0)
            addView(textInputLayout)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_server_port)
            .setView(container)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val portInt = input.text.toString().toIntOrNull()
                if (portInt != null && portInt in 1024..65535) {
                    settings.serverPort = portInt
                    markAsCustomAndSave(settings)
                    updateUiFromSettings(settings)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showProxyUrlInputDialog(settings: TorrentSettings) {
        val input = TextInputEditText(this).apply {
            setText(settings.proxyUrl)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }

        val textInputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.torrent_proxy_url_default)
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            addView(input)
        }

        val container = FrameLayout(this).apply {
            val paddingHorizontal = (24 * resources.displayMetrics.density).toInt()
            val paddingTop = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0)
            addView(textInputLayout)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_proxy_url)
            .setView(container)
            .setPositiveButton(R.string.save) { dialog, _ ->
                settings.proxyUrl = input.text?.toString()?.trim().orEmpty()
                markAsCustomAndSave(settings)
                updateUiFromSettings(settings)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun markAsCustomAndSave(settings: TorrentSettings) {
        settings.profile = TorrentProfile.BALANCED
        settings.enableDHT = true
        binding.torrentProfileValue.text = settings.profile.displayName
        saveData(torrentKey, settings)
        TorrServerService.startOrStop(this, settings)
    }

    private fun showProfileSelectionDialog(settings: TorrentSettings, onProfileChanged: (TorrentSettings) -> Unit) {
        val profiles = TorrentProfile.entries.toTypedArray()
        val labels = profiles.map { it.displayName }.toTypedArray()
        val currentIndex = profiles.indexOf(settings.profile).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.torrent_profile_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedProfile = profiles[which]
                val updatedSettings = selectedProfile.applyTo(settings).apply {
                    enableDHT = true
                }
                saveData(torrentKey, updatedSettings)
                onProfileChanged(updatedSettings)

                TorrServerService.startOrStop(this, updatedSettings)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}