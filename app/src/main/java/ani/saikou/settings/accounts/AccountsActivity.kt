package ani.saikou.settings.accounts

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ani.saikou.R
import ani.saikou.connections.anilist.Anilist
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
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.launch

class AccountsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountsSettingsBinding
    private lateinit var viewModel: DiscordViewModel

    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            startMainActivity(this@AccountsActivity)
        }
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