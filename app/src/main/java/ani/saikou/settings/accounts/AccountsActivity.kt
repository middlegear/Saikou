package ani.saikou.settings.accounts

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ani.saikou.R
import ani.saikou.connections.anilist.Anilist
import ani.saikou.connections.anilist.room.debrid.DebridRepository
import ani.saikou.connections.discord.auth.DiscordRepository
import ani.saikou.connections.discord.auth.DiscordViewModel
import ani.saikou.connections.discord.rpc.RpcRepository
import ani.saikou.connections.mal.MAL
import ani.saikou.databinding.ActivityAccountsSettingsBinding
import ani.saikou.initActivity
import ani.saikou.loadImage
import ani.saikou.navBarHeight
import ani.saikou.others.CustomBottomDialog
import ani.saikou.startMainActivity
import ani.saikou.statusBarHeight
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.launch

class AccountsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountsSettingsBinding
    private lateinit var viewModel: DiscordViewModel

    private val debridRepository by lazy { DebridRepository(this) }

    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            startMainActivity(this@AccountsActivity)
        }
    }

    private val debridProviders = listOf(
        "Real-Debrid",
        "Premiumize",
        "AllDebrid",
        "DebridLink",
        "EasyDebrid",
        "Offcloud",
        "TorBox",
        "Put.io",
        "HighWay",
    )

    private val poppinsBold by lazy {
        runCatching { ResourcesCompat.getFont(this, R.font.poppins_bold) }.getOrNull()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAccountsSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        val discord = DiscordRepository(this)
        val rpc = RpcRepository(this)
        viewModel = DiscordViewModel(discord, rpc)

        binding.accountsMainLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        onBackPressedDispatcher.addCallback(this, restartMainActivity)

        binding.accountsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupAccountHelp()
        setupDebridObserver()
        setupDiscordStateObserver()
    }

    override fun onResume() {
        super.onResume()
        reloadAccounts()
        viewModel.loadDiscordUser()
    }

    private fun reloadAccounts() {
        if (Anilist.token != null) {
            binding.settingsAnilistLogin.setText(R.string.logout)
            binding.settingsAnilistLogin.setOnClickListener {
                Anilist.removeSavedToken(it.context)
                restartMainActivity.isEnabled = true
                reloadAccounts()
            }
            binding.settingsAnilistUsername.visibility = View.VISIBLE
            binding.settingsAnilistUsername.text = Anilist.username
            binding.settingsAnilistAvatar.loadImage(Anilist.avatar)

            binding.settingsMALLoginRequired.visibility = View.GONE
            binding.settingsMALLogin.visibility = View.VISIBLE
            binding.settingsMALUsername.visibility = View.VISIBLE

            if (MAL.token != null) {
                binding.settingsMALLogin.setText(R.string.logout)
                binding.settingsMALLogin.setOnClickListener {
                    MAL.removeSavedToken(it.context)
                    restartMainActivity.isEnabled = true
                    reloadAccounts()
                }
                binding.settingsMALUsername.visibility = View.VISIBLE
                binding.settingsMALUsername.text = MAL.username
                binding.settingsMALAvatar.loadImage(MAL.avatar)
            } else {
                binding.settingsMALAvatar.setImageResource(R.drawable.ic_round_person_24)
                binding.settingsMALUsername.visibility = View.GONE
                binding.settingsMALLogin.setText(R.string.login)
                binding.settingsMALLogin.setOnClickListener {
                    MAL.loginIntent(this)
                }
            }
        } else {
            binding.settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
            binding.settingsAnilistUsername.visibility = View.GONE
            binding.settingsAnilistLogin.setText(R.string.login)
            binding.settingsAnilistLogin.setOnClickListener {
                Anilist.loginIntent(this)
            }
            binding.settingsMALLoginRequired.visibility = View.VISIBLE
            binding.settingsMALLogin.visibility = View.GONE
            binding.settingsMALUsername.visibility = View.GONE
        }
    }

    // ------------------------------------------------------------------ Debrid

    private fun setupDebridObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                debridRepository.observeActive().collect { active ->
                    renderDebridAccount(active?.provider)
                }
            }
        }
    }

    private fun renderDebridAccount(activeProvider: String?) {
        if (activeProvider != null) {
            binding.settingsDebridUsername.text = activeProvider
            binding.settingsDebridLogin.text = getString(R.string.logout)
            binding.settingsDebridAvatarText.text = activeProvider.take(2).uppercase()

            binding.settingsDebridLogin.setOnClickListener {
                lifecycleScope.launch { debridRepository.remove(activeProvider) }
            }
        } else {
            binding.settingsDebridUsername.text = "Debrid Service"
            binding.settingsDebridLogin.text = getString(R.string.login)
            binding.settingsDebridAvatarText.text = "-"

            binding.settingsDebridLogin.setOnClickListener {
                showDebridSingleChoicePopup()
            }
        }


        binding.settingsDebridContainer.setOnClickListener {
            showDebridSingleChoicePopup()
        }
    }


    private fun showDebridSingleChoicePopup() {
        lifecycleScope.launch {
            val optionsList = listOf("None") + debridProviders
            val activeProvider = debridRepository.getActive()?.provider

            val checkedItem = if (activeProvider == null) {
                0
            } else {
                val idx = debridProviders.indexOf(activeProvider)
                if (idx != -1) idx + 1 else 0
            }

            MaterialAlertDialogBuilder(this@AccountsActivity)
                .setTitle("Select Debrid Provider")
                .setSingleChoiceItems(optionsList.toTypedArray(), checkedItem) { dialog, which ->
                    dialog.dismiss()

                    lifecycleScope.launch {
                        if (which == 0) {
                            debridRepository.removeActive()
                        } else {
                            val selectedProvider = debridProviders[which - 1]
                            if (debridRepository.getAccount(selectedProvider) != null) {
                                debridRepository.setActive(selectedProvider)
                            } else {
                                showDebridCredentialDialog(selectedProvider)
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private suspend fun showDebridCredentialDialog(provider: String) {
        val existing = debridRepository.getAccount(provider)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val fields = mutableListOf<TextInputEditText>()

        if (provider == "Put.io") {
            val clientId = createCredentialField(
                "Client ID",
                existing?.clientId.orEmpty(),
            )

            val token = createCredentialField(
                "Token",
                existing?.token.orEmpty(),
                password = true,
            )

            container.addView(clientId.first)
            container.addView(token.first)

            fields.add(clientId.second)
            fields.add(token.second)

            MaterialAlertDialogBuilder(this)
                .setTitle(provider)
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val clientIdValue = fields[0].text?.toString()?.trim()
                    val tokenValue = fields[1].text?.toString()?.trim()

                    if (!clientIdValue.isNullOrEmpty() && !tokenValue.isNullOrEmpty()) {
                        lifecycleScope.launch {
                            debridRepository.saveAndActivate(
                                provider,
                                clientId = clientIdValue,
                                token = tokenValue,
                            )
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()

            return
        }

        val apiKey = createCredentialField(
            "API Key",
            existing?.apiKey.orEmpty(),
            password = true,
        )

        container.addView(apiKey.first)
        fields.add(apiKey.second)

        MaterialAlertDialogBuilder(this)
            .setTitle(provider)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val value = fields.first().text?.toString()?.trim()

                if (!value.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        debridRepository.saveAndActivate(provider, apiKey = value)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCredentialField(
        hint: String,
        value: String,
        password: Boolean = false,
    ): Pair<TextInputLayout, TextInputEditText> {
        val input = TextInputEditText(this).apply {
            setText(value)
            textSize = 14f
            poppinsBold?.let { typeface = it }

            inputType = if (password) {
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        val layout = TextInputLayout(this).apply {
            this.hint = hint
            addView(input)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 12
            }
        }

        return layout to input
    }

    // ----------------------------------------------------------------- Discord

    private fun setupDiscordStateObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isLoggedIn) {
                        binding.settingsDiscordUsername.visibility = View.VISIBLE
                        binding.settingsDiscordUsername.text = state.username
                        binding.settingsDiscordAvatar.loadImage(state.avatarUrl)

                        binding.settingsDiscordLogin.text = getString(R.string.logout)
                        binding.settingsDiscordLogin.setOnClickListener {
                            viewModel.logout()
                        }

                        binding.settingsDiscordRPCSwitch.apply {
                            isChecked = state.isRpcEnabled
                            setOnCheckedChangeListener { _, isChecked ->
                                viewModel.setRpcEnabled(isChecked)
                            }
                            visibility = View.VISIBLE
                        }

                        binding.settingsDiscordRPCText.visibility = View.VISIBLE
                    } else {
                        binding.settingsDiscordUsername.visibility = View.GONE
                        binding.settingsDiscordAvatar.setImageResource(R.drawable.ic_round_person_24)
                        binding.settingsDiscordLogin.text = getString(R.string.login)
                        binding.settingsDiscordLogin.setOnClickListener {
                            DiscordRepository(this@AccountsActivity)
                                .warning(this@AccountsActivity)
                                .show(supportFragmentManager, "discord_warning")
                        }

                        binding.settingsDiscordRPCSwitch.visibility = View.GONE
                        binding.settingsDiscordRPCText.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupAccountHelp() {
        binding.settingsAccountHelp.setOnClickListener { view ->
            val title = getString(R.string.account_help)
            val full = getString(R.string.full_account_help)
            CustomBottomDialog.newInstance().apply {
                setTitleText(title)
                addView(
                    TextView(view.context).apply {
                        val markWon = Markwon.builder(view.context)
                            .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                        markWon.setMarkdown(this, full)
                    }
                )
            }.show(supportFragmentManager, "dialog")
        }
    }
}