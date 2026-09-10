package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TabHost
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.video.ExoPlayerExtractor
import com.example.myapplication.video.FFmpegExtractor
import com.example.myapplication.video.FrameExtractorBenchmark
import com.example.myapplication.video.MediaMetadataRetrieverExtractor
import com.example.myapplication.video.VideoFrameSelectorView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoSelectorActivity : AppCompatActivity() {

    private lateinit var tabHost: TabHost
    private lateinit var resultContainer: LinearLayout
    private var videoPath: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_selector)

        tabHost = findViewById(R.id.tabHost)
        resultContainer = findViewById(R.id.resultContainer)
        val selectVideoBtn = findViewById<Button>(R.id.selectVideoBtn)

        setupTabHost()

        selectVideoBtn.setOnClickListener {
            openVideoSelector()
        }
    }

    private fun setupTabHost() {
        tabHost.setup()

        // Tab 1: MediaMetadataRetriever
        val tab1 = tabHost.newTabSpec("MediaMetadataRetriever")
        tab1.setIndicator("方案1: MMR")
        tab1.setContent(R.id.tab1)
        tabHost.addTab(tab1)

        // Tab 2: FFmpeg
        val tab2 = tabHost.newTabSpec("FFmpeg")
        tab2.setIndicator("方案2: FFmpeg")
        tab2.setContent(R.id.tab2)
        tabHost.addTab(tab2)

        // Tab 3: ExoPlayer
        val tab3 = tabHost.newTabSpec("ExoPlayer")
        tab3.setIndicator("方案3: ExoPlayer")
        tab3.setContent(R.id.tab3)
        tabHost.addTab(tab3)
    }

    private fun openVideoSelector() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            videoPath = getRealPathFromUri(uri)
            videoPath?.let { loadAndCompareMethods(it) }
        }
    }

    private fun loadAndCompareMethods(videoPath: String) {
        scope.launch {
            resultContainer.removeAllViews()
            addResultText("正在提取视频帧... (1秒一帧)")

            // 运行基准测试
            val benchmark = FrameExtractorBenchmark(this@VideoSelectorActivity)
            val results = benchmark.benchmarkAll(videoPath)

            withContext(Dispatchers.Main) {
                resultContainer.removeAllViews()

                // 显示对比结果
                addResultText("┌─ 对比结果 ─────────────┐")

                results.forEachIndexed { index, result ->
                    addResultText("\n${index + 1}. ${result.extractorName}")
                    addResultText("   • 耗时: ${result.durationMs}ms")
                    addResultText("   • 内存: ${String.format("%.2f", result.memoryUsedMB)}MB")
                    addResultText("   • 帧数: ${result.frames.size}帧")

                    // 在对应Tab中显示UI
                    displayFrameSelectorForMethod(index, result.frames, result.extractorName)
                }

                addResultText("\n└─ 推荐方案 ─────────────┘")

                val sorted = results.sortedBy { it.durationMs }
                addResultText("速度最快: ${sorted.first().extractorName} (${sorted.first().durationMs}ms)")

                val memSorted = results.sortedBy { it.memoryUsedMB }
                addResultText("内存最省: ${memSorted.first().extractorName} (${String.format("%.2f", memSorted.first().memoryUsedMB)}MB)")
            }
        }
    }

    private fun displayFrameSelectorForMethod(
        tabIndex: Int,
        frames: List<Bitmap>,
        methodName: String
    ) {
        val tabId = when (tabIndex) {
            0 -> R.id.tab1
            1 -> R.id.tab2
            else -> R.id.tab3
        }

        val tabContainer = findViewById<android.widget.FrameLayout>(tabId)
        tabContainer.removeAllViews()

        if (frames.isNotEmpty()) {
            val selector = VideoFrameSelectorView(this)
            selector.setFrames(frames, methodName)
            selector.setOnCoverSelected { bitmap, timeMs ->
                // 用户选择的封面回调
            }
            tabContainer.addView(selector)
        } else {
            val errorText = TextView(this)
            errorText.text = "$methodName: 提取失败"
            errorText.textSize = 16f
            tabContainer.addView(errorText)
        }
    }

    private fun addResultText(text: String) {
        val textView = TextView(this)
        textView.text = text
        textView.textSize = 12f
        textView.setTextIsSelectable(true)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 4
        textView.layoutParams = params
        resultContainer.addView(textView)
    }

    private fun getRealPathFromUri(uri: Uri): String {
        return when (uri.scheme) {
            "content" -> {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    cursor.moveToFirst()
                    cursor.getString(column)
                }
            }
            "file" -> uri.path
            else -> uri.toString()
        } ?: uri.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val REQUEST_VIDEO = 1001
    }
}
