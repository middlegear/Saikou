package com.lagradost.cloudstream3.plugins

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.RepositoryManager.getRepositories
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import dalvik.system.PathClassLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.reflect.KClass

object PluginManager {
    const val ONLINE_PLUGINS_FOLDER = "Plugins"
    const val PLUGINS_KEY = "PLUGINS_KEY"
    const val PLUGIN_TAG = "PluginManager"
    
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    var plugins: MutableMap<String, BasePlugin> = mutableMapOf()

    val urlPlugins = lazy {
        getPluginsOnline().map { it.internalName }
    }

    data class PluginData(
        @JsonProperty("internalName") val internalName: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("tvTypes") val tvTypes: List<String>?,
        @JsonProperty("version") val version: Int,
        @JsonProperty("url") val url: String,
        @JsonProperty("iconUrl") val iconUrl: String?,
        @JsonProperty("filePath") val filePath: String,
        @JsonProperty("isDisabled") val isDisabled: Boolean,       // For keeping track of plugin states
        @JsonProperty("isDownloaded") val isDownloaded: Boolean    // For keeping track of plugins being downloaded right now
    )

    data class OnlinePluginData(
        val savedData: PluginData,
        val onlineData: Pair<String, SitePlugin>
    )

    fun getPluginsOnline(): Array<PluginData> {
        return com.lagradost.cloudstream3.utils.DataStore.getPrefs()?.let {
            it.getString(PLUGINS_KEY, null)?.let { json ->
                parseJson<Array<PluginData>>(json)
            }
        } ?: emptyArray()
    }

    private fun setPluginsOnline(plugins: Array<PluginData>) {
        com.lagradost.cloudstream3.utils.DataStore.setKey(PLUGINS_KEY, plugins)
    }

    private fun updatePluginData(data: PluginData) {
        val plugins = getPluginsOnline().toMutableList()
        val index = plugins.indexOfFirst { it.internalName == data.internalName }
        if (index != -1) {
            plugins[index] = data
        } else {
            plugins.add(data)
        }
        setPluginsOnline(plugins.toTypedArray())
    }

    private fun deletePluginData(data: PluginData) {
        val plugins = getPluginsOnline().toMutableList()
        plugins.removeAll { it.internalName == data.internalName }
        setPluginsOnline(plugins.toTypedArray())
    }

    fun getPluginSanitizedFileName(url: String): String {
        return url.replace(Regex("""[^a-zA-Z0-9.\-]"""), "_")
    }

    fun getPluginPath(
        context: Context,
        internalName: String,
        repositoryUrl: String
    ): File {
        val folderName = getPluginSanitizedFileName(repositoryUrl)
        val fileName = getPluginSanitizedFileName(internalName)
        return File("${context.filesDir}/${ONLINE_PLUGINS_FOLDER}/${folderName}/$fileName.cs3")
    }

    suspend fun downloadPlugin(
        context: Context,
        pluginUrl: String,
        internalName: String,
        repositoryUrl: String
    ): Boolean {
        return downloadPlugin(
            context,
            pluginUrl,
            internalName,
            getPluginPath(context, internalName, repositoryUrl)
        )
    }

    suspend fun downloadPlugin(
        context: Context,
        pluginUrl: String,
        internalName: String,
        file: File,
    ): Boolean {
        try {
            val request = Request.Builder().url(RepositoryManager.convertRawGitUrl(pluginUrl)).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body ?: return false

            file.parentFile?.mkdirs()
            file.setWritable(true) // Ensure it's writable before downloading
            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            Log.i(PLUGIN_TAG, "Downloaded plugin to ${file.absolutePath}")
            val pluginData = getPluginsOnline().firstOrNull { it.internalName == internalName }
            if (pluginData != null) {
                updatePluginData(pluginData.copy(isDownloaded = true, filePath = file.absolutePath))
            }

            loadPlugin(context, file)
            return true
        } catch (e: Exception) {
            Log.e(PLUGIN_TAG, "Failed to download plugin", e)
            return false
        }
    }

    suspend fun loadPlugin(context: Context, file: File): Boolean {
        if (!file.exists()) {
            Log.w(PLUGIN_TAG, "Plugin file ${file.absolutePath} does not exist.")
            return false
        }
        val filePath = file.absolutePath

        val pluginData = getPluginsOnline().firstOrNull { it.filePath == filePath }
        if (pluginData?.isDisabled == true || pluginData?.isDownloaded == false) {
            Log.w(PLUGIN_TAG, "Plugin ${file.absolutePath} is disabled or not fully downloaded.")
            return false
        }

        try {
            Log.i(PLUGIN_TAG, "Loading plugin from ${file.absolutePath}")
            file.setReadOnly() // Required for Android 13+ to prevent SecurityException
            val loader = PathClassLoader(filePath, PluginManager::class.java.classLoader)
            
            // Search for manifest
            val manifestUrl = loader.getResource("manifest.json")
            if (manifestUrl == null) {
                Log.e(PLUGIN_TAG, "Plugin ${file.name} does not contain manifest.json")
                return false
            }

            val manifestText = manifestUrl.openStream().bufferedReader().use { it.readText() }
            val manifest = try {
                Gson().fromJson(manifestText, BasePlugin.Manifest::class.java)
            } catch (e: Throwable) {
                Log.w(PLUGIN_TAG, "Gson parsing failed, falling back to JSONObject")
                val json = org.json.JSONObject(manifestText)
                val tvTypesList = mutableListOf<String>()
                val tvTypesArray = json.optJSONArray("tvTypes")
                if (tvTypesArray != null) {
                    for (i in 0 until tvTypesArray.length()) {
                        tvTypesList.add(tvTypesArray.getString(i))
                    }
                }
                BasePlugin.Manifest().apply {
                    name = json.optString("name", "")
                    pluginClassName = json.optString("pluginClassName", "")
                    version = json.optInt("version", 0)
                    requiresResources = json.optBoolean("requiresResources", false)
                    tvTypes = tvTypesList
                }
            }

            Log.i(PLUGIN_TAG, "Parsed manifest: ${manifest.name} v${manifest.version}")

            @Suppress("UNCHECKED_CAST")
            val pluginClass = loader.loadClass(manifest.pluginClassName) as Class<out BasePlugin?>
            val pluginInstance: BasePlugin = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin

            plugins[filePath] = pluginInstance
            pluginInstance.filename = filePath

            if (pluginInstance is Plugin) {
                try {
                    val assetManager = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                    addAssetPath.invoke(assetManager, filePath)
                    val res = android.content.res.Resources(
                        assetManager,
                        context.resources.displayMetrics,
                        context.resources.configuration
                    )
                    pluginInstance.resources = res
                } catch (e: Exception) {
                    Log.e(PLUGIN_TAG, "Failed to load resources for plugin $filePath", e)
                }

                val loadContext = (context as? AppCompatActivity)
                    ?: com.anymex.runtimehost.RuntimeBridge.resolveAppCompatActivity(context)
                    ?: context
                pluginInstance.load(loadContext)
            } else {
                pluginInstance.load() // For BasePlugin
            }

            if (pluginData == null) {
                // Was not tracked yet, store data
                updatePluginData(
                    PluginData(
                        internalName = manifest.name,
                        name = manifest.name,
                        tvTypes = manifest.tvTypes,
                        version = manifest.version,
                        url = "",
                        iconUrl = "",
                        filePath = filePath,
                        isDisabled = false,
                        isDownloaded = true
                    )
                )
            }

            return true

        } catch (e: Exception) {
            Log.e(PLUGIN_TAG, "Error loading plugin ${file.absolutePath}", e)
            return false
        }
    }

    fun unloadPlugin(filePath: String) {
        val plugin = plugins[filePath]
        if (plugin != null) {
            plugin.beforeUnload()
            APIHolder.allProviders.removeIf { it.sourcePlugin == filePath }
            APIHolder.apis.removeIf { it.sourcePlugin == filePath }
            // Remove extractor apis
            com.lagradost.cloudstream3.utils.extractorApis.removeIf { it.sourcePlugin == filePath }
            plugins.remove(filePath)
            Log.i(PLUGIN_TAG, "Unloaded plugin from $filePath")
        }
    }

    fun deletePlugin(filePath: String): Boolean {
        val file = File(filePath)
        return try {
            file.setWritable(true) // Ensure it's writable before deleting
            if (file.delete()) {
                unloadPlugin(filePath)
                getPluginsOnline().firstOrNull { it.filePath == filePath }?.let {
                    deletePluginData(it)
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}

class AppCompatActivityWrapper(base: Context) : AppCompatActivity() {
    init {
        attachBaseContext(base)
    }

    override fun getSupportFragmentManager(): androidx.fragment.app.FragmentManager {
        val hostActivity = (baseContext as? androidx.fragment.app.FragmentActivity)
            ?: (baseContext as? ContextWrapper)?.baseContext as? androidx.fragment.app.FragmentActivity
        if (hostActivity != null) {
            return hostActivity.supportFragmentManager
        }
        return super.getSupportFragmentManager()
    }

    override fun isFinishing(): Boolean {
        val hostActivity = (baseContext as? Activity)
            ?: (baseContext as? ContextWrapper)?.baseContext as? Activity
        return hostActivity?.isFinishing ?: super.isFinishing()
    }

    override fun isDestroyed(): Boolean {
        val hostActivity = (baseContext as? Activity)
            ?: (baseContext as? ContextWrapper)?.baseContext as? Activity
        return hostActivity?.isDestroyed ?: super.isDestroyed()
    }

    override fun getApplicationContext(): Context {
        return baseContext.applicationContext
    }

    override fun getResources(): android.content.res.Resources {
        return baseContext.resources
    }

    override fun getAssets(): android.content.res.AssetManager {
        return baseContext.assets
    }

    override fun getTheme(): android.content.res.Resources.Theme {
        return baseContext.theme
    }

    override fun getPackageName(): String {
        return baseContext.packageName
    }

    override fun getPackageManager(): android.content.pm.PackageManager {
        return baseContext.packageManager
    }

    override fun getClassLoader(): ClassLoader {
        return baseContext.classLoader
    }

    override fun getSystemService(name: String): Any {
        return baseContext.getSystemService(name)
    }
}
