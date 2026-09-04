package ani.saikou.settings.anime

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.saikou.R
import ani.saikou.databinding.ActivityAnimeSettingsBinding
import ani.saikou.initActivity
import ani.saikou.loadData
import ani.saikou.navBarHeight
import ani.saikou.parsers.AnimeSources
import ani.saikou.saveData
import ani.saikou.settings.player.PlayerSettingsActivity
import ani.saikou.settings.UserInterfaceSettings
import ani.saikou.statusBarHeight
import ani.saikou.torrserver.TorrServerActivity

class AnimeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnimeSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAnimeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)


        binding.animeMainLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        // Back button navigation
        binding.animeSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupAnimeSource()
        setupSwitches()
        setupEpisodeViewModes()
        setupNavigationButtons()
    }

    override fun onResume() {
        super.onResume()
        updateAnimeSourceDropdown()
    }

    private fun setupAnimeSource() {
        updateAnimeSourceDropdown()
        binding.animeSource.setOnItemClickListener { _, _, position, _ ->
            saveData("settings_def_anime_source", position)
            binding.animeSource.clearFocus()
        }
    }

    private fun updateAnimeSourceDropdown() {
        val currentSource = loadData<Int>("settings_def_anime_source")?.let {
            if (it >= AnimeSources.names.size) 0 else it
        } ?: 0

        val currentAdapter = binding.animeSource.adapter
        if (currentAdapter == null || currentAdapter.count != AnimeSources.names.size) {
            val newAdapter = ArrayAdapter(this, R.layout.item_dropdown, AnimeSources.names)
            binding.animeSource.setAdapter(newAdapter)
        }

        binding.animeSource.setText(AnimeSources.names[currentSource], false)
    }

    private fun setupSwitches() {
//        // Prefer Dub preference disabled cause sources
//        binding.settingsPreferDub.isChecked = loadData("settings_prefer_dub") ?: false
//        binding.settingsPreferDub.setOnCheckedChangeListener { _, isChecked ->
//            saveData("settings_prefer_dub", isChecked)
//        }

        // Show YouTube Trailer preference inside UserInterfaceSettings
        val uiSettings: UserInterfaceSettings = loadData("ui_settings", toast = false)
            ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }

        binding.settingsShowYt.isChecked = uiSettings.showYtButton
        binding.settingsShowYt.setOnCheckedChangeListener { _, isChecked ->
            uiSettings.showYtButton = isChecked
            saveData("ui_settings", uiSettings)
        }
    }

    private fun setupEpisodeViewModes() {
        val uiSettings: UserInterfaceSettings = loadData("ui_settings", toast = false)
            ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }

        var previousEp: View = when (uiSettings.animeDefaultView) {
            0 -> binding.settingsEpList
            1 -> binding.settingsEpGrid
            2 -> binding.settingsEpCompact
            else -> binding.settingsEpList
        }
        previousEp.alpha = 1f

        fun setUiEpMode(mode: Int, current: View) {
            previousEp.alpha = 0.33f
            previousEp = current
            current.alpha = 1f
            uiSettings.animeDefaultView = mode
            saveData("ui_settings", uiSettings)
        }

        binding.settingsEpList.setOnClickListener {
            setUiEpMode(0, it)
        }

        binding.settingsEpGrid.setOnClickListener {
            setUiEpMode(1, it)
        }

        binding.settingsEpCompact.setOnClickListener {
            setUiEpMode(2, it)
        }
    }

    private fun setupNavigationButtons() {
        binding.settingsPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerSettingsActivity::class.java))
        }

        binding.settingsTorrent.setOnClickListener {
            startActivity(Intent(this, TorrServerActivity::class.java))
        }
    }
}