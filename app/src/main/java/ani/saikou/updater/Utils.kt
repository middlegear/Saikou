package ani.saikou.updater

import java.io.File

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState

    data class Available(
        val version: String,
        val changelog: String,
        val downloadUrl: String
    ) : UpdateState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercentage: Int
    ) : UpdateState

    data class ReadyToInstall(
        val apkFile: File
    ) : UpdateState

    data class Error(
        val message: String
    ) : UpdateState
}