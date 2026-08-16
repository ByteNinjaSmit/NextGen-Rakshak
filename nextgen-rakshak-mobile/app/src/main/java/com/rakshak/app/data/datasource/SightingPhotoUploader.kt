package com.rakshak.app.data.datasource

import android.graphics.Bitmap
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Uploads the volunteer's captured face crop as evidence for a reported
 * sighting, distinct from the alert's original parent-submitted photo — the
 * kiosk's match review needs both to compare side by side.
 */
class SightingPhotoUploader(private val storage: FirebaseStorage) {

    suspend fun upload(alertId: String, face: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { stream ->
            face.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        val ref = storage.reference.child("match_sightings/${alertId}_${System.currentTimeMillis()}.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
