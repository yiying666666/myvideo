package com.example.myapplication.cover

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize

/**
 * The cover chosen on the [CoverSelectionActivity] screen: either a specific
 * timestamp within the source video, or a static image picked from the album
 * (in which case the item still publishes as a video - only the cover image
 * changes).
 */
sealed interface CoverResult : Parcelable {

    @Parcelize
    data class VideoFrame(val timestampUs: Long) : CoverResult

    @Parcelize
    data class StaticImage(val imageUri: Uri) : CoverResult
}

/**
 * `ActivityResultContract` for launching [CoverSelectionActivity] with a video [Uri]
 * and getting back the chosen [CoverResult] (or null if the user cancelled).
 */
object CoverSelectionContract : ActivityResultContract<Uri, CoverResult?>() {

    const val EXTRA_VIDEO_URI = "com.example.myapplication.cover.EXTRA_VIDEO_URI"
    const val EXTRA_COVER_RESULT = "com.example.myapplication.cover.EXTRA_COVER_RESULT"

    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(context, CoverSelectionActivity::class.java)
            .putExtra(EXTRA_VIDEO_URI, input)

    override fun parseResult(resultCode: Int, intent: Intent?): CoverResult? {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return null
        return intent.getParcelableExtraCompat(EXTRA_COVER_RESULT)
    }
}
