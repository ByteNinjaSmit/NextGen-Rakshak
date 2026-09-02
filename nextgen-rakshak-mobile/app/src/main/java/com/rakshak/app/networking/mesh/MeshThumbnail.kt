package com.rakshak.app.networking.mesh

import android.graphics.Bitmap
import com.rakshak.app.utils.Constants
import java.io.ByteArrayOutputStream

/**
 * Encodes a child's face photo into the tiny JPEG that travels inside an alert
 * packet, so an offline volunteer's match dialog can still show the parent's
 * photo (FR-07) with no internet to fetch `imageUrl`.
 */
object MeshThumbnail {

    /**
     * Centre-square-crop [src], scale to [Constants.MESH_THUMBNAIL_SIZE_PX] and
     * JPEG-encode it.
     *
     * @return the bytes, or null if the result would still exceed
     *   [Constants.MESH_THUMBNAIL_MAX_BYTES] — the alert then travels without a
     *   thumbnail rather than pushing the packet toward the 32 KB payload limit.
     */
    fun encode(src: Bitmap): ByteArray? {
        val side = minOf(src.width, src.height)
        if (side <= 0) return null
        val square = Bitmap.createBitmap(
            src,
            (src.width - side) / 2,
            (src.height - side) / 2,
            side,
            side,
        )
        val target = Constants.MESH_THUMBNAIL_SIZE_PX
        val scaled = Bitmap.createScaledBitmap(square, target, target, true)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, Constants.MESH_THUMBNAIL_JPEG_QUALITY, out)
            out.toByteArray()
        }
        return bytes.takeIf { it.size <= Constants.MESH_THUMBNAIL_MAX_BYTES }
    }
}
