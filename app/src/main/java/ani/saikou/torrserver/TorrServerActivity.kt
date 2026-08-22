package ani.saikou.torrserver

import android.content.Context
import android.os.Bundle
import android.view.View
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
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin

class TorrServerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTorrentSettingsBinding
    private val torrentKey = "torrent_settings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTorrentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var settings = loadData<TorrentSettings>(torrentKey, toast = false) ?: TorrentSettings().apply {
            saveData(torrentKey, this)
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


        binding.torrentBufferSize.setOnClickListener {
            showBufferSizeDialog(settings)
        }


        binding.torrentEnableDHT.setOnCheckedChangeListener { _, isChecked ->
            settings.enableDHT = isChecked
            markAsCustomAndSave(settings)
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
        binding.torrentBufferSizeValue.text = "${settings.bufferSizeMb} MB"
        binding.torrentEnableDHT.isChecked = settings.enableDHT
        binding.torrentEnableEncryption.isChecked = settings.enableEncryption
        binding.torrentEnableStats.isChecked = settings.enableStatics
    }

    private fun markAsCustomAndSave(settings: TorrentSettings) {
        settings.profile = TorrentProfile.BALANCED
        binding.torrentProfileValue.text = settings.profile.displayName
        saveData(torrentKey, settings)
        TorrServerService.startOrStop(this, settings)
    }

    private fun showProfileSelectionDialog(settings: TorrentSettings, onProfileChanged: (TorrentSettings) -> Unit) {
        val profiles = TorrentProfile.entries.toTypedArray()
        val labels = profiles.map { it.displayName }.toTypedArray()
        val currentIndex = profiles.indexOf(settings.profile).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Server Presets")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedProfile = profiles[which]
                val updatedSettings = selectedProfile.applyTo(settings)
                saveData(torrentKey, updatedSettings)
                onProfileChanged(updatedSettings)

                TorrServerService.startOrStop(this, updatedSettings)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
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

    private fun showBufferSizeDialog(settings: TorrentSettings) {
        val options = intArrayOf( 64, 128, 256,)
        val labels = options.map { "$it MB" }.toTypedArray()
        val currentIndex = options.indexOf(settings.bufferSizeMb).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Video Buffer Size (RAM)")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                settings.bufferSizeMb = options[which]
                markAsCustomAndSave(settings)
                binding.torrentBufferSizeValue.text = "${options[which]} MB"
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
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