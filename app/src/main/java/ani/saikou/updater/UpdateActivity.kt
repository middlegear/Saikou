package ani.saikou.updater

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import ani.saikou.compose.SaikouTheme
import ani.saikou.saveData
import ani.saikou.updater.ui.AppUpdateContent

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateActivity : AppCompatActivity() {

    private var pendingApkFile: File? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (canRequestPackageInstalls()) {
            pendingApkFile?.let { installApk(it) }
        } else {
            Toast.makeText(this, "Permission required to install updates", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forceCheck = intent.getBooleanExtra(EXTRA_FORCE_CHECK, false)
        val currentState = AppUpdater.updateState.value

        if (forceCheck || currentState is UpdateState.Idle || currentState is UpdateState.Error) {
            lifecycleScope.launch(Dispatchers.IO) {
                AppUpdater.check(this@UpdateActivity, force = forceCheck)
            }
        }

        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    SaikouTheme {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            val state by AppUpdater.updateState.collectAsState()

                            AppUpdateContent(
                                state = state,
                                onCheckForUpdates = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        AppUpdater.check(this@UpdateActivity, force = true)
                                    }
                                },
                                onStartDownload = { url, version ->
                                    AppUpdater.startDownload(
                                        applicationContext,
                                        url,
                                        version
                                    )
                                },
                                onCancelDownload = {
                                    AppUpdater.cancelDownload()
                                },
                                onInstall = { apkFile ->
                                    handleApkInstallation(apkFile)
                                },
                                onDontShowAgain = { version, isChecked ->
                                    saveData("dont_ask_for_update_$version", isChecked)
                                },
                                onDismiss = {
                                    AppUpdater.dismissCurrentUpdate()
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    private fun handleApkInstallation(apkFile: File) {
        pendingApkFile = apkFile
        if (canRequestPackageInstalls()) {
            installApk(apkFile)
        } else {
            requestInstallPermission()
        }
    }

    private fun canRequestPackageInstalls(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    private fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:$packageName".toUri()
        )
        installPermissionLauncher.launch(intent)
    }

    private fun installApk(file: File) {
        AppUpdater.installApk(this, file)
    }

    companion object {
        private const val EXTRA_FORCE_CHECK = "extra_force_check"

        fun launch(context: Context, forceCheck: Boolean = false) {
            val intent = Intent(context, UpdateActivity::class.java).apply {
                putExtra(EXTRA_FORCE_CHECK, forceCheck)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (context !is AppCompatActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}