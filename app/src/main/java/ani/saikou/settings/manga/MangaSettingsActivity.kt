package ani.saikou.settings.manga

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.saikou.R
import ani.saikou.databinding.ActivityMangaSettingsBinding
import ani.saikou.initActivity
import ani.saikou.loadData
import ani.saikou.navBarHeight
import ani.saikou.parsers.MangaSources
import ani.saikou.saveData
import ani.saikou.settings.ReaderSettingsActivity
import ani.saikou.settings.UserInterfaceSettings
import ani.saikou.statusBarHeight

class MangaSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMangaSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMangaSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)


        binding.mangaMainLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        // Back button navigation
        binding.mangaSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupMangaSource()
        setupChapterViewModes()
        setupNavigationButtons()
    }

    override fun onResume() {
        super.onResume()
        updateMangaSourceDropdown()
    }

    private fun setupMangaSource() {
        updateMangaSourceDropdown()
        binding.mangaSource.setOnItemClickListener { _, _, position, _ ->
            saveData("settings_def_manga_source", position)
            binding.mangaSource.clearFocus()
        }
    }

    private fun updateMangaSourceDropdown() {
        val currentSource = loadData<Int>("settings_def_manga_source")?.let {
            if (it >= MangaSources.names.size) 0 else it
        } ?: 0

        val currentAdapter = binding.mangaSource.adapter
        if (currentAdapter == null || currentAdapter.count != MangaSources.names.size) {
            val newAdapter = ArrayAdapter(this, R.layout.item_dropdown, MangaSources.names)
            binding.mangaSource.setAdapter(newAdapter)
        }

        binding.mangaSource.setText(MangaSources.names[currentSource], false)
    }

    private fun setupChapterViewModes() {
        val uiSettings: UserInterfaceSettings = loadData("ui_settings", toast = false)
            ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }

        var previousChp: View = when (uiSettings.mangaDefaultView) {
            0 -> binding.settingsChpList
            1 -> binding.settingsChpCompact
            else -> binding.settingsChpList
        }
        previousChp.alpha = 1f

        fun setUiChpMode(mode: Int, current: View) {
            previousChp.alpha = 0.33f
            previousChp = current
            current.alpha = 1f
            uiSettings.mangaDefaultView = mode
            saveData("ui_settings", uiSettings)
        }

        binding.settingsChpList.setOnClickListener {
            setUiChpMode(0, it)
        }

        binding.settingsChpCompact.setOnClickListener {
            setUiChpMode(1, it)
        }
    }

    private fun setupNavigationButtons() {
        binding.settingsReader.setOnClickListener {
            startActivity(Intent(this, ReaderSettingsActivity::class.java))
        }
    }
}