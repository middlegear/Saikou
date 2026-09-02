package ani.saikou.others

import ani.saikou.FileUrl
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.io.InputStream


class FileUrlLoader(
    private val concreteLoader: ModelLoader<GlideUrl, InputStream>
) : ModelLoader<FileUrl, InputStream> {

    override fun buildLoadData(
        model: FileUrl,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        val headers = LazyHeaders.Builder().apply {
            model.headers.forEach { (key, value) -> addHeader(key, value) }
        }.build()

        val glideUrl = GlideUrl(model.url, headers)
        return concreteLoader.buildLoadData(glideUrl, width, height, options)
    }

    override fun handles(model: FileUrl): Boolean = model.url.isNotEmpty()

    class Factory : ModelLoaderFactory<FileUrl, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<FileUrl, InputStream> {

            return FileUrlLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {}
    }
}