package ani.saikou.settings.extensions

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import ani.saikou.compose.SaikouTheme
import ani.saikou.extensions.AniyomiSource
import ani.saikou.extensions.AvailableAniyomiExtension
import ani.saikou.extensions.CloudStreamProvider
import ani.saikou.extensions.ExtensionPreference
import ani.saikou.extensions.ExtensionRuntime
import ani.saikou.extensions.ExtensionSourceRegistry
import ani.saikou.extensions.ExtensionStore
import ani.saikou.extensions.KotatsuSource
import ani.saikou.initActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extension manager for the bundled runtime: Aniyomi/Tachiyomi repositories and extensions,
 * CloudStream plugins and Kotatsu parser jars, including per-source preferences.
 */
class ExtensionsActivity : AppCompatActivity() {

    private var pendingInstallKind = KIND_ANIYOMI_ANIME

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { copyAndInstall(it, pendingInstallKind) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initActivity(this)

        setContent {
            SaikouTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExtensionsScreen(
                        onBack = { finish() },
                        onPickFile = { kind ->
                            pendingInstallKind = kind
                            pickFile.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
        }
    }

    private fun copyAndInstall(uri: Uri, kind: Int) {
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val name = displayName(uri) ?: "extension_${System.currentTimeMillis()}.bin"
                    val cached = File(cacheDir, name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        cached.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext "Could not read the selected file"

                    val installed = when (kind) {
                        KIND_CLOUDSTREAM -> ExtensionRuntime.loadCloudStreamPlugin(cached.absolutePath)
                        KIND_KOTATSU -> ExtensionRuntime.installKotatsuPlugin(cached)
                        KIND_ANIYOMI_MANGA -> ExtensionRuntime.installExtensionFromFile(cached, false)
                        else -> ExtensionRuntime.installExtensionFromFile(cached, true)
                    }
                    ExtensionSourceRegistry.invalidate()
                    if (installed) "Installed $name" else "Failed to install $name"
                } catch (t: Throwable) {
                    "Installation failed: ${t.message}"
                }
            }
            Toast.makeText(this@ExtensionsActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (t: Throwable) {
        null
    }

    companion object {
        const val KIND_ANIYOMI_ANIME = 0
        const val KIND_ANIYOMI_MANGA = 1
        const val KIND_CLOUDSTREAM = 2
        const val KIND_KOTATSU = 3
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1
private const val TAB_CLOUDSTREAM = 2
private const val TAB_KOTATSU = 3

@Composable
private fun ExtensionsScreen(
    onBack: () -> Unit,
    onPickFile: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(TAB_ANIME) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var animeSources by remember { mutableStateOf(ExtensionRuntime.allAnimeSources()) }
    var mangaSources by remember { mutableStateOf(ExtensionRuntime.allMangaSources()) }
    var cloudStreamProviders by remember { mutableStateOf(ExtensionRuntime.allCloudStreamProviders()) }
    var kotatsuSources by remember { mutableStateOf(ExtensionRuntime.allKotatsuSources()) }

    var showRepositories by remember { mutableStateOf(false) }
    var availableExtensions by remember { mutableStateOf<List<AvailableAniyomiExtension>>(emptyList()) }
    var showAvailable by remember { mutableStateOf(false) }
    var preferenceTarget by remember { mutableStateOf<AniyomiSource?>(null) }
    var preferences by remember { mutableStateOf<List<ExtensionPreference>>(emptyList()) }

    fun reload() {
        animeSources = ExtensionRuntime.allAnimeSources()
        mangaSources = ExtensionRuntime.allMangaSources()
        cloudStreamProviders = ExtensionRuntime.allCloudStreamProviders()
        kotatsuSources = ExtensionRuntime.allKotatsuSources()
    }

    fun refreshEverything() {
        if (busy) return
        busy = true
        status = "Refreshing extensions..."
        scope.launch {
            val result = runCatching {
                ExtensionRuntime.refreshAniyomiSources()
                ExtensionRuntime.refreshCloudStreamProviders()
                ExtensionRuntime.refreshKotatsuSources()
            }
            ExtensionSourceRegistry.invalidate()
            reload()
            busy = false
            status = result.exceptionOrNull()?.let { "Refresh failed: ${it.message}" }
                ?: "Extensions reloaded"
        }
    }

    LaunchedEffect(preferenceTarget) {
        val target = preferenceTarget ?: return@LaunchedEffect
        preferences = runCatching {
            ExtensionRuntime.aniyomiPreferences(target.id, target.isAnime)
        }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Extensions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { refreshEverything() }, enabled = !busy) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        TabRow(selectedTabIndex = tab) {
            listOf("Anime", "Manga", "CloudStream", "Kotatsu").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, fontSize = 12.sp) },
                )
            }
        }

        status?.let { text ->
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        when (tab) {
            TAB_ANIME, TAB_MANGA -> {
                val isAnime = tab == TAB_ANIME
                val sources = if (isAnime) animeSources else mangaSources

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showRepositories = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Repositories") }
                    Button(
                        onClick = {
                            busy = true
                            status = "Loading repository index..."
                            scope.launch {
                                val result = runCatching {
                                    ExtensionRuntime.availableExtensions(isAnime)
                                }
                                busy = false
                                result.onSuccess {
                                    availableExtensions = it
                                    showAvailable = true
                                    status = "${it.size} extensions available"
                                }.onFailure {
                                    status = "Could not load repositories: ${it.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Browse") }
                }

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            onPickFile(
                                if (isAnime) ExtensionsActivity.KIND_ANIYOMI_ANIME
                                else ExtensionsActivity.KIND_ANIYOMI_MANGA
                            )
                        }
                    ) { Text("Install APK") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val result = runCatching { ExtensionRuntime.refreshAniyomiSources() }
                                ExtensionSourceRegistry.invalidate()
                                reload()
                                status = result.exceptionOrNull()?.let { "Scan failed: ${it.message}" }
                                    ?: "Scanned installed extensions"
                            }
                        }
                    ) { Text("Rescan") }
                }

                if (sources.isEmpty()) {
                    EmptyState(
                        "No ${if (isAnime) "anime" else "manga"} extensions installed.\n" +
                            "Install one from a repository or an APK file."
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sources, key = { it.key }) { source ->
                            ExtensionRow(
                                name = source.displayName,
                                subtitle = buildString {
                                    append(source.extensionName)
                                    if (!source.version.isNullOrBlank()) append(" • ${source.version}")
                                    if (source.isNsfw) append(" • NSFW")
                                },
                                enabled = ExtensionStore.isSourceEnabled(context, source.key),
                                onEnabledChange = { enabled ->
                                    ExtensionStore.setSourceEnabled(context, source.key, enabled)
                                    ExtensionSourceRegistry.invalidate()
                                    reload()
                                },
                                onSettings = { preferenceTarget = source },
                                onDelete = {
                                    scope.launch {
                                        val pkgName = source.pkgName
                                        if (pkgName.isNullOrBlank()) {
                                            status = "This extension has no package name"
                                        } else {
                                            val removed = runCatching {
                                                ExtensionRuntime.uninstallExtension(pkgName, source.isAnime)
                                            }.getOrDefault(false)
                                            ExtensionSourceRegistry.invalidate()
                                            reload()
                                            status = if (removed) "Removed ${source.name}" else "Could not remove ${source.name}"
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            TAB_CLOUDSTREAM -> {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { onPickFile(ExtensionsActivity.KIND_CLOUDSTREAM) }) {
                        Text("Import plugin")
                    }
                    TextButton(onClick = { refreshEverything() }) { Text("Reload") }
                }

                if (cloudStreamProviders.isEmpty()) {
                    EmptyState("No CloudStream providers loaded.\nImport a CloudStream plugin file to add providers.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(cloudStreamProviders, key = { it.key }) { provider ->
                            ExtensionRow(
                                name = provider.name,
                                subtitle = listOf(provider.language, provider.url)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                enabled = ExtensionStore.isSourceEnabled(context, provider.key),
                                onEnabledChange = { enabled ->
                                    ExtensionStore.setSourceEnabled(context, provider.key, enabled)
                                    ExtensionSourceRegistry.invalidate()
                                    reload()
                                },
                                onSettings = if (provider.hasSettings) {
                                    {
                                        scope.launch {
                                            val opened = runCatching {
                                                ExtensionRuntime.openCloudStreamSettings(provider.name)
                                            }.getOrDefault(false)
                                            if (!opened) {
                                                Toast.makeText(
                                                    context,
                                                    "This plugin has no settings screen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                } else null,
                                onDelete = {
                                    scope.launch {
                                        runCatching { ExtensionRuntime.unloadCloudStreamPlugin(provider.name) }
                                        ExtensionSourceRegistry.invalidate()
                                        reload()
                                        status = "Unloaded ${provider.name}"
                                    }
                                },
                            )
                        }
                    }
                }
            }

            TAB_KOTATSU -> {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { onPickFile(ExtensionsActivity.KIND_KOTATSU) }) {
                        Text("Import parser jar")
                    }
                    TextButton(onClick = { refreshEverything() }) { Text("Reload") }
                }

                if (kotatsuSources.isEmpty()) {
                    EmptyState("No Kotatsu parsers loaded.\nImport a Kotatsu plugin jar to add manga sources.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(kotatsuSources, key = { it.key }) { source ->
                            ExtensionRow(
                                name = source.name,
                                subtitle = listOf(source.lang, source.baseUrl)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                enabled = ExtensionStore.isSourceEnabled(context, source.key),
                                onEnabledChange = { enabled ->
                                    ExtensionStore.setSourceEnabled(context, source.key, enabled)
                                    ExtensionSourceRegistry.invalidate()
                                    reload()
                                },
                                onSettings = null,
                                onDelete = null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRepositories) {
        RepositoriesDialog(
            isAnime = tab == TAB_ANIME,
            onDismiss = { showRepositories = false },
            onChanged = { status = "Repositories updated" },
        )
    }

    if (showAvailable) {
        AvailableExtensionsDialog(
            extensions = availableExtensions,
            isAnime = tab == TAB_ANIME,
            onInstall = { extension ->
                scope.launch {
                    busy = true
                    status = "Installing ${extension.name}..."
                    val installed = runCatching { ExtensionRuntime.installExtension(extension) }
                        .getOrDefault(false)
                    ExtensionSourceRegistry.invalidate()
                    reload()
                    busy = false
                    status = if (installed) "Installed ${extension.name}" else "Failed to install ${extension.name}"
                }
            },
            onDismiss = { showAvailable = false },
        )
    }

    preferenceTarget?.let { target ->
        PreferencesDialog(
            title = target.displayName,
            preferences = preferences,
            onSave = { key, value ->
                scope.launch {
                    ExtensionRuntime.saveAniyomiPreference(target.id, key, value)
                    preferences = runCatching {
                        ExtensionRuntime.aniyomiPreferences(target.id, target.isAnime)
                    }.getOrDefault(preferences)
                }
            },
            onRefresh = {
                scope.launch {
                    preferences = runCatching {
                        ExtensionRuntime.aniyomiPreferences(target.id, target.isAnime)
                    }.getOrDefault(preferences)
                }
            },
            onDismiss = { preferenceTarget = null },
        )
    }
}

@Composable
private fun ExtensionRow(
    name: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSettings: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onSettings?.let {
            IconButton(onClick = it, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
            }
        }
        onDelete?.let {
            IconButton(onClick = it, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun RepositoriesDialog(
    isAnime: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var repositories by remember {
        mutableStateOf(ExtensionStore.repositories(context, isAnime))
    }
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAnime) "Anime repositories" else "Manga repositories") },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("index.min.json URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val url = input.trim()
                        if (url.isNotEmpty()) {
                            ExtensionStore.addRepository(context, isAnime, url)
                            repositories = ExtensionStore.repositories(context, isAnime)
                            input = ""
                            onChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add repository") }

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(repositories) { repo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = repo,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    ExtensionStore.removeRepository(context, isAnime, repo)
                                    repositories = ExtensionStore.repositories(context, isAnime)
                                    onChanged()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove repository",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun AvailableExtensionsDialog(
    extensions: List<AvailableAniyomiExtension>,
    isAnime: Boolean,
    onInstall: (AvailableAniyomiExtension) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAnime) "Anime extensions" else "Manga extensions") },
        text = {
            if (extensions.isEmpty()) {
                Text("No extensions found in the configured repositories.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(extensions, key = { "${it.pkgName}:${it.versionCode}" }) { extension ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = extension.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${extension.version} • ${extension.lang}" +
                                        if (extension.isNsfw) " • NSFW" else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onInstall(extension) }) { Text("Install") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PreferencesDialog(
    title: String,
    preferences: List<ExtensionPreference>,
    onSave: (String, Any?) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            if (preferences.isEmpty()) {
                Text("This source has no preferences.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(preferences, key = { it.key }) { preference ->
                        PreferenceRow(preference = preference, onSave = onSave)
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onRefresh) { Text("Reload") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun PreferenceRow(
    preference: ExtensionPreference,
    onSave: (String, Any?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember(preference.key, preference.value) {
        mutableStateOf(preference.value?.toString() ?: "")
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = preference.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        preference.summary?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (preference.type) {
            "switch", "checkbox" -> {
                Switch(
                    checked = preference.value == true,
                    onCheckedChange = { onSave(preference.key, it) },
                )
            }

            "list" -> {
                val entries = preference.entries.ifEmpty { preference.entryValues }
                val currentIndex = preference.entryValues.indexOf(preference.value?.toString())
                val label = entries.getOrNull(currentIndex)
                    ?: preference.value?.toString()
                    ?: "Select"

                Box {
                    TextButton(onClick = { expanded = true }) { Text(label) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        entries.forEachIndexed { index, entry ->
                            DropdownMenuItem(
                                text = { Text(entry) },
                                onClick = {
                                    expanded = false
                                    onSave(preference.key, preference.entryValues.getOrNull(index) ?: entry)
                                },
                            )
                        }
                    }
                }
            }

            "text" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(preference.key, text) }) { Text("Save") }
                }
            }

            "button", "other" -> {
                TextButton(onClick = { onSave(preference.key, true) }) { Text("Open") }
            }
        }
    }
}
