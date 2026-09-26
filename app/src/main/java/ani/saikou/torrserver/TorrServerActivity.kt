package ani.saikou.torrserver

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import ani.saikou.R
import ani.saikou.databinding.ActivityTorrentSettingsBinding
import ani.saikou.loadData
import ani.saikou.others.CustomBottomDialog
import ani.saikou.saveData
import ani.saikou.torrserver.utils.TorrentProfile
import ani.saikou.torrserver.utils.TorrentSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin

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


        if (!settings.enableDHT) {
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

        binding.torrentSubSettingsContainer.visibility = if (settings.enableTorrentServer) View.VISIBLE else View.GONE

        setupTorrentServerListener(settings)

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
        binding.torrentEnableServer.isChecked = settings.enableTorrentServer
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

    private fun setupTorrentServerListener(settings: TorrentSettings) {
        binding.torrentEnableServer.setOnCheckedChangeListener(null)

        binding.torrentEnableServer.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                binding.torrentEnableServer.setOnCheckedChangeListener(null)

                warning(
                    context = this,
                    onConfirm = {
                        settings.enableTorrentServer = true
                        saveData(torrentKey, settings)
                        binding.torrentSubSettingsContainer.visibility = View.VISIBLE

                        TorrServerService.startOrStop(this, settings)
                        setupTorrentServerListener(settings)
                    },
                    onCancel = {
                        settings.enableTorrentServer = false
                        saveData(torrentKey, settings)
                        buttonView.isChecked = false
                        binding.torrentSubSettingsContainer.visibility = View.GONE

                        TorrServerService.startOrStop(this, settings)
                        setupTorrentServerListener(settings)
                    },
                    onDismiss = {
                        settings.enableTorrentServer = false
                        saveData(torrentKey, settings)
                        buttonView.isChecked = false
                        binding.torrentSubSettingsContainer.visibility = View.GONE

                        TorrServerService.startOrStop(this, settings)
                        setupTorrentServerListener(settings)
                    }
                ).show(supportFragmentManager, "torrent_warning")
            } else {
                settings.enableTorrentServer = false
                saveData(torrentKey, settings)
                binding.torrentSubSettingsContainer.visibility = View.GONE

                TorrServerService.startOrStop(this, settings)
            }
        }

        binding.torrentEnableServer.isChecked = settings.enableTorrentServer
    }

    private fun warning(
        context: Context,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        onDismiss: () -> Unit = {}
    ) = CustomBottomDialog().apply {
        title = context.getString(R.string.warning)
        val md = context.getString(R.string.torrent_warning)

        addView(TextView(context).apply {
            val markWon = Markwon.builder(context)
                .usePlugin(SoftBreakAddsNewLinePlugin.create())
                .build()
            markWon.setMarkdown(this, md)
        })

        setNegativeButton(context.getString(R.string.cancel)) {
            onCancel()
            dismiss()
        }

        setPositiveButton(context.getString(android.R.string.ok)) {
            onConfirm()
            dismiss()
        }

        setOnDismissListener {
            onDismiss()
        }
    }
}