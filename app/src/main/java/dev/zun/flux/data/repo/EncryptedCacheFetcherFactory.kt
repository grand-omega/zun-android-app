package dev.zun.flux.data.repo

import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer

/**
 * Resolves `flux-cache://<safe-job-id>/<kind-filename>` URIs produced by
 * [OfflineImageCache.localUri] when the offline cache is encrypted. Reads the
 * encrypted file off disk, decrypts in memory via [OfflineImageCache.vault],
 * and hands the plaintext bytes to Coil as a [SourceFetchResult] backed by an
 * okio [Buffer].
 *
 * If the offline cache is not currently encrypting (i.e. running under a unit
 * test environment without an Android Keystore), Coil's built-in `file://`
 * fetcher handles the cache instead.
 */
class EncryptedCacheFetcherFactory(
    private val cache: OfflineImageCache,
) : Fetcher.Factory<Uri> {

    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        if (data.scheme != OfflineImageCache.SCHEME) return null
        val vault = cache.vault ?: return null
        val safeJobId = data.authority ?: return null
        val fileName = data.lastPathSegment ?: return null
        val kind = OfflineImageCache.Kind.entries.firstOrNull { it.fileName == fileName } ?: return null
        return Decrypter(safeJobId, kind, cache, vault, options)
    }

    private class Decrypter(
        private val safeJobId: String,
        private val kind: OfflineImageCache.Kind,
        private val cache: OfflineImageCache,
        @Suppress("unused") private val vault: EncryptedFileVault,
        private val options: Options,
    ) : Fetcher {
        override suspend fun fetch(): FetchResult {
            val plaintext = cache.readDecrypted(safeJobId, kind)
                ?: error("Encrypted cache miss: $safeJobId/${kind.fileName}")
            val buffer = Buffer().write(plaintext)
            return SourceFetchResult(
                source = ImageSource(buffer, options.fileSystem),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }
    }
}
