package dev.zun.flux.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * The fix in feature "duplicate recent photos" (HomeRoute now calls
 * [prepareImageForUpload] at pick-time, for every fresh gallery/camera/share
 * pick, so its hash matches what a "recent photo" re-download represents)
 * depends on [prepareImageForUpload] being a deterministic function of its
 * input: the same raw photo picked twice independently must produce
 * byte-identical (and therefore same-hash) staged output, or the composer's
 * content-hash dedup silently stops working again.
 */
@RunWith(AndroidJUnit4::class)
class ImageUtilsDeterminismTest {

    @Test
    fun prepareImageForUpload_isDeterministicForTheSameRawInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // A large (needs downscaling), non-trivial (varied pixel content,
        // not a single flat color a naive encoder could special-case)
        // bitmap, standing in for a real camera-roll photo.
        val rawFile = File(context.cacheDir, "determinism_test_raw.jpg")
        val bitmap = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height step 37) {
            for (x in 0 until bitmap.width step 41) {
                bitmap.setPixel(x, y, Color.rgb((x * 7) % 256, (y * 13) % 256, ((x + y) * 3) % 256))
            }
        }
        FileOutputStream(rawFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        bitmap.recycle()

        try {
            val rawUri = Uri.fromFile(rawFile)

            val staged1 = prepareImageForUpload(context, rawUri)
            val hash1 = try {
                sha256Hex(staged1)
            } finally {
                staged1.delete()
            }

            val staged2 = prepareImageForUpload(context, rawUri)
            val hash2 = try {
                sha256Hex(staged2)
            } finally {
                staged2.delete()
            }

            assertEquals(
                "prepareImageForUpload must produce the same bytes for the same raw input — " +
                    "otherwise a fresh re-pick of the same photo won't hash-match a prior upload " +
                    "or a \"recent\" re-download of it",
                hash1,
                hash2,
            )
        } finally {
            rawFile.delete()
        }
    }
}
