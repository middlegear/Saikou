package ani.saikou.settings.about

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build.BRAND
import android.os.Build.DEVICE
import android.os.Build.SUPPORTED_ABIS
import android.os.Build.VERSION.CODENAME
import android.os.Build.VERSION.RELEASE
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import ani.saikou.BuildConfig
import ani.saikou.R
import ani.saikou.copyToClipboard
import ani.saikou.currContext
import ani.saikou.databinding.ActivityAboutSettingsBinding
import ani.saikou.initActivity
import ani.saikou.loadData
import ani.saikou.navBarHeight
import ani.saikou.openLinkInBrowser
import ani.saikou.others.CustomBottomDialog
import ani.saikou.pop
import ani.saikou.saveData
import ani.saikou.settings.DevelopersDialogFragment
import ani.saikou.settings.FAQActivity
import ani.saikou.settings.ForksDialogFragment
import ani.saikou.snackString
import ani.saikou.statusBarHeight
import ani.saikou.toast
import ani.saikou.updater.UpdateActivity
import kotlinx.coroutines.launch

class AboutSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutSettingsBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)


        binding.aboutMainLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        // Back button navigation
        binding.aboutSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // App Version Text & Copy Architecture Info
        binding.settingsVersion.text = getString(R.string.version_current, BuildConfig.VERSION_NAME)
        binding.settingsVersion.setOnLongClickListener {
            fun getArch(): String {
                SUPPORTED_ABIS.forEach {
                    when (it) {
                        "arm64-v8a" -> return "aarch64"
                        "armeabi-v7a" -> return "arm"
                        "x86_64" -> return "x86_64"
                        "x86" -> return "i686"
                    }
                }
                return System.getProperty("os.arch") ?: System.getProperty("os.product.cpu.abi")
                ?: "Unknown Architecture"
            }

            val info = """
                Saikou Version: ${BuildConfig.VERSION_NAME}
                Device: $BRAND $DEVICE
                Architecture: ${getArch()}
                OS Version: $CODENAME $RELEASE ($SDK_INT)
            """.trimIndent()

            copyToClipboard(info, false)
            toast(getString(R.string.copied_device_info))
            true
        }

        // FAQ Button
        binding.settingsFAQ.setOnClickListener {
            startActivity(Intent(this, FAQActivity::class.java))
        }

        // App Updates Check Switch
        binding.settingsCheckUpdate.isChecked = loadData("check_update") ?: true
        binding.settingsCheckUpdate.setOnCheckedChangeListener { _, isChecked ->
            saveData("check_update", isChecked)
            if (!isChecked) {
                snackString(getString(R.string.long_click_to_check_update))
            }
        }
        binding.settingsCheckUpdate.setOnLongClickListener {
            UpdateActivity.launch(this, forceCheck = true)
            true
        }

        // Developers Dialog
        binding.settingsDev.setOnClickListener {
            DevelopersDialogFragment().show(supportFragmentManager, "dialog")
        }

        // Forks & Credits Dialog
        binding.settingsForks.setOnClickListener {
            ForksDialogFragment().show(supportFragmentManager, "dialog")
        }

        // Disclaimer Bottom Dialog
        binding.settingsDisclaimer.setOnClickListener {
            val title = getString(R.string.disclaimer)
            val text = TextView(this).apply {
                setText(R.string.full_disclaimer)
            }

            CustomBottomDialog.newInstance().apply {
                setTitleText(title)
                addView(text)
                setNegativeButton(currContext()!!.getString(R.string.close)) {
                    dismiss()
                }
                show(supportFragmentManager, "dialog")
            }
        }

        // Donation (Buy Me a Coffee) Animation & Link
        lifecycleScope.launch {
            binding.settingBuyMeCoffee.pop()
        }
        binding.settingBuyMeCoffee.setOnClickListener {
            lifecycleScope.launch {
                it.pop()
            }
            openLinkInBrowser("https://www.buymeacoffee.com/brahmkshatriya")
        }

        // Social Media / Community Icons
        binding.loginDiscord.setOnClickListener {
            openLinkInBrowser(getString(R.string.discord))
        }
        binding.loginTelegram.setOnClickListener {
            openLinkInBrowser(getString(R.string.telegram))
        }
        binding.loginGithub.setOnClickListener {
            openLinkInBrowser(getString(R.string.github))
        }
    }
}