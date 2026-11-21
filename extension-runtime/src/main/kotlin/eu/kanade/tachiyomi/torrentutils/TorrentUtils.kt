package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.DeadTorrentException
import eu.kanade.tachiyomi.torrentutils.model.TorrentFile
import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

object TorrentUtils {

    fun getTorrentInfo(url: String, title: String): TorrentInfo {
        if (url.startsWith("magnet:") || url.startsWith("magnet?")) {
            val info = parseMagnet(url)
            return if ((info.title == "Magnet Link" || info.title.isEmpty()) && title.isNotEmpty()) {
                info.copy(title = title, files = listOf(TorrentFile(title, 0, 0L, info.hash, info.trackers)))
            } else {
                info
            }
        }
        
        try {
            val bytes = if (url.startsWith("http://") || url.startsWith("https://")) {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP error: ${response.code}")
                    response.body.bytes()
                }
            } else {
                java.io.File(url).readBytes()
            }
            val info = parseTorrentBytes(bytes)
            return if (title.isNotEmpty()) info.copy(title = title) else info
        } catch (e: Exception) {
            val dummyHash = url.hashCode().toString(16).padStart(40, '0').take(40)
            val files = listOf(TorrentFile(title.ifEmpty { "Torrent Video" }, 0, 0L, dummyHash))
            return TorrentInfo(title.ifEmpty { "Torrent Video" }, files, dummyHash, 0L)
        }
    }

    private fun sha1(bytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(bytes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun base32ToHex(base32: String): String {
        val base32Chars = "abcdefghijklmnopqrstuvwxyz234567"
        val clean = base32.lowercase().filter { it in base32Chars }
        var bits = ""
        for (c in clean) {
            val valInt = base32Chars.indexOf(c)
            if (valInt != -1) {
                bits += valInt.toString(2).padStart(5, '0')
            }
        }
        val hex = java.lang.StringBuilder()
        for (i in 0 until (bits.length / 4) * 4 step 4) {
            val chunk = bits.substring(i, i + 4)
            hex.append(chunk.toInt(2).toString(16))
        }
        return hex.toString()
    }

    private fun parseMagnet(url: String): TorrentInfo {
        val trackers = mutableListOf<String>()
        var hash = ""
        var title = "Magnet Link"
        
        val query = if (url.contains("?")) url.substringAfter("?") else url
        query.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts.getOrNull(0) ?: ""
            val value = parts.getOrNull(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            
            if (key == "xt" && value.startsWith("urn:btih:")) {
                hash = value.substringAfter("urn:btih:").lowercase()
                if (hash.length == 32) {
                    hash = base32ToHex(hash)
                }
            } else if (key == "dn") {
                title = value
            } else if (key == "tr") {
                if (value.isNotEmpty() && value !in trackers) {
                    trackers.add(value)
                }
            }
        }
        
        val files = listOf(TorrentFile(title, 0, 0L, hash, trackers))
        return TorrentInfo(title, files, hash, 0L, trackers)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTorrentBytes(bytes: ByteArray): TorrentInfo {
        val parser = BencodeParser(bytes)
        val root = parser.decode() as? Map<String, Any?> ?: throw IllegalArgumentException("Invalid torrent file structure")
        val info = root["info"] as? Map<String, Any?> ?: throw IllegalArgumentException("Missing info dictionary")
        
        val rawInfo = root["_raw_info_bytes"] as? ByteArray ?: throw IllegalArgumentException("Could not extract raw info bytes")
        val infoHash = sha1(rawInfo).toHex()
        
        val title = info["name"] as? String ?: "Unnamed Torrent"
        val trackers = mutableListOf<String>()
        
        (root["announce"] as? String)?.let { trackers.add(it) }
        (root["announce-list"] as? List<*>)?.forEach { list ->
            if (list is List<*>) {
                list.forEach { url ->
                    if (url is String && url !in trackers) trackers.add(url)
                }
            }
        }
        
        val files = mutableListOf<TorrentFile>()
        val filesList = info["files"] as? List<*>
        if (filesList != null) {
            var index = 0
            for (fItem in filesList) {
                val f = fItem as? Map<String, Any?> ?: continue
                val length = (f["length"] as? Number)?.toLong() ?: 0L
                val pathList = (f["path"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val path = pathList.joinToString("/")
                files.add(TorrentFile(path, index++, length, infoHash, trackers))
            }
        } else {
            val length = (info["length"] as? Number)?.toLong() ?: 0L
            files.add(TorrentFile(title, 0, length, infoHash, trackers))
        }
        
        val totalSize = files.sumOf { it.size }
        return TorrentInfo(title, files, infoHash, totalSize, trackers)
    }

    private class BencodeParser(private val data: ByteArray) {
        var pos = 0

        fun decode(): Any? {
            if (pos >= data.size) return null
            val c = data[pos].toInt().toChar()
            return when (c) {
                'i' -> {
                    pos++ // skip 'i'
                    val start = pos
                    while (pos < data.size && data[pos].toInt().toChar() != 'e') {
                        pos++
                    }
                    val value = String(data, start, pos - start, Charsets.UTF_8).toLong()
                    pos++ // skip 'e'
                    value
                }
                'l' -> {
                    pos++ // skip 'l'
                    val list = mutableListOf<Any?>()
                    while (pos < data.size && data[pos].toInt().toChar() != 'e') {
                        list.add(decode())
                    }
                    pos++ // skip 'e'
                    list
                }
                'd' -> {
                    pos++ // skip 'd'
                    val map = mutableMapOf<String, Any?>()
                    while (pos < data.size && data[pos].toInt().toChar() != 'e') {
                        val keyStart = pos
                        val key = decode() as? String ?: throw IllegalArgumentException("Dict key must be string")
                        
                        val valStart = pos
                        val value = decode()
                        val valEnd = pos
                        
                        map[key] = value
                        if (key == "info") {
                            val infoBytes = ByteArray(valEnd - valStart)
                            System.arraycopy(data, valStart, infoBytes, 0, infoBytes.size)
                            map["_raw_info_bytes"] = infoBytes
                        }
                    }
                    pos++ // skip 'e'
                    map
                }
                in '0'..'9' -> {
                    val start = pos
                    while (pos < data.size && data[pos].toInt().toChar() != ':') {
                        pos++
                    }
                    val len = String(data, start, pos - start, Charsets.UTF_8).toInt()
                    pos++ // skip ':'
                    val strStart = pos
                    pos += len
                    val stringBytes = ByteArray(len)
                    System.arraycopy(data, strStart, stringBytes, 0, len)
                    try {
                        String(stringBytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        stringBytes
                    }
                }
                else -> throw IllegalArgumentException("Unexpected character at pos $pos: $c")
            }
        }
    }
}
