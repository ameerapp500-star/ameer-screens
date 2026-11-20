
package com.ameerscreens.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val serverUrl = "https://ameer-activation.ameerapp500.workers.dev"

    private val pickVideoRequest = 4102
    private val pickFolderRequest = 4103
    private val pickAudioRequest = 4104

    private var selectedVideo: Uri? = null
    private var selectedVideoName = "لم يتم اختيار فيديو بعد"
    private var videoView: VideoView? = null
    private var playButton: Button? = null
    private var folderUri: Uri? = null
    private var audioPlayer: MediaPlayer? = null

    private lateinit var root: LinearLayout

    private val bg = Color.rgb(9, 15, 29)
    private val panel = Color.rgb(20, 31, 51)
    private val accent = Color.rgb(0, 190, 170)
    private val muted = Color.rgb(160, 174, 195)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val prefs = getSharedPreferences("ameer", MODE_PRIVATE)
        prefs.getString("folder_uri", null)?.let {
            try { folderUri = Uri.parse(it) } catch (_: Exception) {}
        }

        if (prefs.getBoolean("activated", false)) {
            showHome()
        } else {
            showActivation()
        }
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun basePage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(22))
        setBackgroundColor(bg)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun label(
        text: String,
        size: Float,
        color: Int = Color.WHITE,
        bold: Boolean = false
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(text: String, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun params(
        width: Int = -1,
        height: Int = -2,
        top: Int = 0
    ) = LinearLayout.LayoutParams(width, height).apply {
        topMargin = dp(top)
    }

    private fun pageTitle(title: String, subtitle: String? = null) {
        root.addView(label(title, 25f, Color.WHITE, true), params(top = 4))
        if (subtitle != null) {
            root.addView(label(subtitle, 13f, muted), params(top = 5))
        }
    }

    private fun addAction(text: String, action: () -> Unit) {
        root.addView(button(text, action), params(top = 10))
    }

    private fun showActivation() {
        val page = basePage().apply { gravity = Gravity.CENTER }

        page.addView(label("أمير للشاشات", 30f, Color.WHITE, true))
        page.addView(label(
            "مشغل الوسائط للهاتف والسيارة", 15f, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(24))
        })

        val status = label(
            "نسخة مستقلة - أدخل كود التفعيل", 14f, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        page.addView(status)

        val input = EditText(this).apply {
            hint = "أدخل كود التفعيل"
            textSize = 16f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setBackgroundColor(panel)
        }
        page.addView(input, params())

        val activate = button("تفعيل التطبيق") {}
        page.addView(activate, params(top = 14))

        activate.setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isEmpty()) {
                input.error = "أدخل كود التفعيل"
                return@setOnClickListener
            }

            activate.isEnabled = false
            activate.text = "جارٍ التحقق..."

            executor.execute {
                var ok = false
                var message = "تعذر الاتصال بالخادم"

                try {
                    val c = URL(serverUrl).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 15000
                    c.readTimeout = 15000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")

                    c.outputStream.use {
                        it.write(
                            JSONObject().put("code", code)
                                .toString().toByteArray(Charsets.UTF_8)
                        )
                    }

                    val rc = c.responseCode
                    val stream = if (rc in 200..299) c.inputStream else c.errorStream
                    val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    c.disconnect()

                    if (response.isNotBlank()) {
                        val result = JSONObject(response)
                        ok = rc == 200 && result.optBoolean("activated", false)
                        message = result.optString("message", "كود التفعيل غير صحيح")
                    }
                } catch (_: Exception) {
                    message = "تعذر الاتصال بالخادم"
                }

                runOnUiThread {
                    activate.isEnabled = true
                    activate.text = "تفعيل التطبيق"

                    if (ok) {
                        getSharedPreferences("ameer", MODE_PRIVATE)
                            .edit().putBoolean("activated", true).apply()
                        showHome()
                    } else {
                        status.text = message
                        status.setTextColor(Color.RED)
                    }
                }
            }
        }

        setContentView(page)
    }

    private fun showHome() {
        root = basePage()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brand.addView(label("أمير للشاشات", 26f, Color.WHITE, true))
        brand.addView(label("AMEER SCREENS PLAYER", 11f, muted))

        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("● جاهز", 13f, accent, true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(panel)
        })
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(17, 52, 68))
        }
        hero.addView(label("مشاهدة الوسائط", 22f, Color.WHITE, true))
        hero.addView(label(
            "افتح ملفات الفيديو والصوت من جهازك",
            14f, Color.rgb(205, 224, 232)
        ).apply {
            setPadding(0, dp(7), 0, dp(12))
        })
        hero.addView(button("＋ اختيار فيديو", ::openVideoPicker),
            params(height = dp(48)))
        root.addView(hero, params(top = 22))

        root.addView(label("التشغيل الآن", 19f, Color.WHITE, true), params(top = 22))

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }

        videoView = VideoView(this)
        playerCard.addView(videoView, params(height = dp(205)))
        playerCard.addView(label(selectedVideoName, 14f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        val replay = button("↺") {
            videoView?.seekTo(0)
            videoView?.start()
            playButton?.text = "Ⅱ"
        }

        playButton = button("▶") {
            if (selectedVideo == null) {
                openVideoPicker()
            } else if (videoView?.isPlaying == true) {
                videoView?.pause()
                playButton?.text = "▶"
            } else {
                videoView?.start()
                playButton?.text = "Ⅱ"
            }
        }

        val stop = button("■") {
            videoView?.stopPlayback()
            selectedVideo?.let { videoView?.setVideoURI(it) }
            playButton?.text = "▶"
        }

        listOf(replay, playButton!!, stop).forEach {
            controls.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f))
        }

        playerCard.addView(controls)
        root.addView(playerCard, params(top = 10))
        root.addView(label("الأقسام", 19f, Color.WHITE, true), params(top = 22))

        addHomeTile("🎬 الفيديوهات", "اختيار وتشغيل ملفات الفيديو", ::showVideos)
        addHomeTile("📁 المجلدات", "اختيار مجلد من ذاكرة الجهاز", ::showFolders)
        addHomeTile("🎵 الموسيقى", "اختيار ملف صوتي وتشغيله", ::showMusic)
        addHomeTile("⚙ الإعدادات", "خيارات التطبيق ومعلوماته", ::showSettings)

        root.addView(label("أمير للشاشات • الإصدار 1.1", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }, params(top = 8))

        setContentView(ScrollView(this).apply { addView(root) })
        selectedVideo?.let { videoView?.setVideoURI(it) }
    }

    private fun addHomeTile(title: String, description: String, action: () -> Unit) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            setBackgroundColor(panel)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        tile.addView(label(title, 17f, Color.WHITE, true))
        tile.addView(label(description, 12f, muted).apply {
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(tile, params(top = 8))
    }

    private fun showVideos() {
        root = basePage()
        pageTitle("الفيديوهات", "اختر فيديو من جهازك أو شغّل الفيديو المحدد")
        addAction("＋ اختيار فيديو", ::openVideoPicker)

        val current = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(panel)
        }
        current.addView(label("الفيديو المحدد", 16f, Color.WHITE, true))
        current.addView(label(selectedVideoName, 13f, muted).apply {
            setPadding(0, dp(8), 0, dp(8))
        })

        addInlineButton(current, "تشغيل") {
            val uri = selectedVideo
            if (uri == null) {
                openVideoPicker()
            } else {
                showHome()
                videoView?.setVideoURI(uri)
                videoView?.start()
                playButton?.text = "Ⅱ"
            }
        }

        root.addView(current, params(top = 18))
        addAction("رجوع للرئيسية", ::showHome)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showFolders() {
        root = basePage()
        pageTitle("المجلدات", "اختر مجلداً من ذاكرة الجهاز")

        root.addView(label(
            if (folderUri != null)
                "المجلد المحدد: ${folderUri?.lastPathSegment ?: "مجلد الجهاز"}"
            else
                "اختَر مجلداً كاملاً لعرض ملفات الفيديو الموجودة بداخله",
            14f, muted
        ), params(top = 16))

        addAction("📁 اختيار مجلد كامل", ::openFolderPicker)
        addAction("تحديث محتويات المجلد") {
            folderUri?.let { showFolderContents(it) }
                ?: Toast.makeText(this, "اختَر مجلداً أولاً", Toast.LENGTH_SHORT).show()
        }
        addAction("رجوع للرئيسية", ::showHome)

        setContentView(ScrollView(this).apply { addView(root) })
        folderUri?.let { showFolderContents(it) }
    }

    private fun showFolderContents(treeUri: Uri) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        section.addView(label("محتويات المجلد", 17f, Color.WHITE, true))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section.addView(list, params(top = 8))

        try {
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                var count = 0
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: "ملف بدون اسم"
                    val mime = cursor.getString(mimeCol) ?: ""

                    if (mime.startsWith("video/") ||
                        name.substringAfterLast('.', "").lowercase() in
                        listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")) {
                        count++
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        list.addView(button("▶  $name") {
                            selectedVideo = fileUri
                            selectedVideoName = name
                            showHome()
                            videoView?.setVideoURI(fileUri)
                            videoView?.start()
                            playButton?.text = "Ⅱ"
                        }, params(top = 6))
                    }
                }

                if (count == 0) {
                    list.addView(label(
                        "ما لكيت ملفات فيديو مباشرة بهذا المجلد. جرّب مجلداً يحتوي فيديوهات.",
                        13f, muted
                    ))
                } else {
                    section.addView(label("عدد الفيديوهات: $count", 12f, accent), params(top = 8))
                }
            } ?: list.addView(label("تعذر قراءة محتويات هذا المجلد.", 13f, muted))
        } catch (e: Exception) {
            list.addView(label(
                "تعذر عرض الملفات: ${e.message ?: "خطأ غير معروف"}",
                13f, muted
            ))
        }

        root.addView(section, params(top = 16))
    }

    private fun showMusic() {
        root = basePage()
        pageTitle("الموسيقى", "اختر ملفاً صوتياً من جهازك")
        addAction("＋ اختيار ملف صوتي", ::openAudioPicker)

        addAction("إيقاف الصوت") {
            audioPlayer?.stop()
            audioPlayer?.release()
            audioPlayer = null
            Toast.makeText(this, "تم إيقاف الصوت", Toast.LENGTH_SHORT).show()
        }

        addAction("رجوع للرئيسية", ::showHome)
        setContentView(root)
    }

    private fun showSettings() {
        root = basePage()
        pageTitle("الإعدادات", "إعدادات تطبيق أمير للشاشات")

        val prefs = getSharedPreferences("ameer", MODE_PRIVATE)

        val keepScreen = Switch(this).apply {
            text = "إبقاء الشاشة مضاءة أثناء الاستخدام"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("keep_screen", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("keep_screen", checked).apply()
                applyKeepScreen(checked)
            }
        }
        root.addView(keepScreen, params(top = 18))

        val autoPlay = Switch(this).apply {
            text = "تشغيل الفيديو تلقائياً بعد الاختيار"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("auto_play", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("auto_play", checked).apply()
            }
        }
        root.addView(autoPlay, params(top = 12))

        addAction("معلومات التطبيق") {
            AlertDialog.Builder(this)
                .setTitle("أمير للشاشات")
                .setMessage(
                    "الإصدار 1.1\nمشغل وسائط للهاتف.\nالفيديوهات والمجلدات والموسيقى والإعدادات."
                )
                .setPositiveButton("حسناً", null)
                .show()
        }

        addAction("رجوع للرئيسية", ::showHome)
        setContentView(root)
    }

    private fun applyKeepScreen(enabled: Boolean) {
        if (enabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun addInlineButton(parent: LinearLayout, text: String, action: () -> Unit) {
        parent.addView(button(text, action), params(top = 8))
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, pickVideoRequest)
    }

    private fun openFolderPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), pickFolderRequest)
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, pickAudioRequest)
    }

    @Deprecated("Deprecated API retained for broad Android compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!

        when (requestCode) {
            pickVideoRequest -> {
                selectedVideo = uri
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                selectedVideoName = uri.lastPathSegment?.substringAfterLast('/')
                    ?: "تم اختيار فيديو"

                showHome()
                videoView?.setVideoURI(uri)

                if (getSharedPreferences("ameer", MODE_PRIVATE)
                        .getBoolean("auto_play", false)) {
                    videoView?.start()
                    playButton?.text = "Ⅱ"
                }

                Toast.makeText(this, "تم اختيار الفيديو", Toast.LENGTH_SHORT).show()
            }

            pickFolderRequest -> {
                folderUri = uri
                getSharedPreferences("ameer", MODE_PRIVATE).edit()
                    .putString("folder_uri", uri.toString()).apply()

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                showFolders()
                Toast.makeText(this, "تم حفظ اختيار المجلد", Toast.LENGTH_SHORT).show()
            }

            pickAudioRequest -> {
                try {
                    audioPlayer?.release()
                    audioPlayer = MediaPlayer().apply {
                        setDataSource(this@MainActivity, uri)
                        setOnPreparedListener {
                            it.start()
                            Toast.makeText(
                                this@MainActivity,
                                "بدأ تشغيل الصوت",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        setOnErrorListener { _, _, _ ->
                            Toast.makeText(
                                this@MainActivity,
                                "تعذر تشغيل الملف الصوتي",
                                Toast.LENGTH_SHORT
                            ).show()
                            true
                        }
                        prepareAsync()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "تعذر فتح الملف الصوتي", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("ameer", MODE_PRIVATE)
                .getBoolean("keep_screen", true)) {
            applyKeepScreen(true)
        }
    }

    override fun onDestroy() {
        try { audioPlayer?.release() } catch (_: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }
}
