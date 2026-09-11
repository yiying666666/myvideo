package com.example.myapplication.cover

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize

/**
 * 在 [CoverSelectionActivity] 页面选中的封面：可以是源视频里的某个具体时间点，
 * 也可以是从相册选的一张静态图片（这种情况下发布形式仍然是视频——只是封面图变了）。
 */
sealed interface CoverResult : Parcelable {

    @Parcelize
    data class VideoFrame(val timestampUs: Long) : CoverResult

    @Parcelize
    data class StaticImage(val imageUri: Uri) : CoverResult
}

/**
 * 用于启动 [CoverSelectionActivity] 的 `ActivityResultContract`：传入视频的 [Uri]，
 * 拿回用户选中的 [CoverResult]（用户取消则返回 null）。
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
