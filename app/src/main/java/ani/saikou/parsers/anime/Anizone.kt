package ani.saikou.parsers.anime

//@OptIn(InternalSerializationApi::class)
//class Anizone : DirectApiParser() {
//
//    override val name = "Anizone"
//    override val saveName = "Anizone"
//    override val providerName = "anizone"
//    override val isDubAvailableSeparately = false
//
//
//    override suspend fun search(query: String): List<ShowResponse> {
//        return try {
//            if (query.isBlank()) return emptyList()
//
//            val encoded = URLEncoder.encode(query, "utf-8")
//            val res = client.get("$hostUrl/api/anizone/anime/search?q=$encoded")
//                .parsed<SearchApiResponse>()
//
//            res.data.map {
//                ShowResponse(
//                    name = it.name,
//                    link = it.id,
//                    coverUrl = FileUrl(it.posterImage)
//                )
//            }
//        } catch (e: Exception) {
//            emptyList()
//        }
//    }
//
//    override suspend fun loadVideoServers(
//        episodeLink: String,
//        extra: Map<String, String>?
//    ): List<VideoServer> {
//        return try {
//            val embedUrl = "$hostUrl/api/anizone/sources/$episodeLink"
//
//
//            return listOf(
//                VideoServer(
//                    name = "Anizone Source: IDK what to call this",
//                    embed = FileUrl(embedUrl),
//                    extraData = null
//                )
//            )
//        } catch (e: Exception) {
//            emptyList()
//        }
//    }
//
//    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
//
//        return RapidCloud(server)
//    }
//
//
//    @Serializable
//    private data class SearchApiResponse(
//        val data: List<SearchItems>
//    )
//
//    @Serializable
//    private data class SearchItems(
//        val id: String,
//        val name: String,
//        val posterImage: String
//
//    )
//
//
//
//
//
//
//
//}