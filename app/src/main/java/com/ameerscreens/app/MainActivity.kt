
package com.ameerscreens.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val serverUrl = "https://ameer-activation.ameerapp500.workers.dev"
    private val executor = Executors.newSingleThreadExecutor()

    private val VIDEO = 4102
    private val FOLDER = 4103
    private val AUDIO = 4104
    private val PHOTO = 4105

    private val bg = Color.rgb(8, 13, 27)
    private val card = Color.rgb(22, 31, 52)
    private val white = Color.WHITE
    private val muted = Color.rgb(157, 174, 199)

    private var videoUri: Uri? = null
    private var folderUri: Uri? = null
    private var player: MediaPlayer? = null
    private var videoView: VideoView? = null
    private var videoName = "لم يتم اختيار فيديو"
    private lateinit var root: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int = white,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun page(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(18), dp(17), dp(24))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addButton(
        parent: LinearLayout,
        title: String,
        color: Int = card,
        action: () -> Unit
    ) {
        val b = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            background = shape(color, 14)
            setPadding(dp(10), dp(15), dp(10), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val prefs = getSharedPreferences("ameer", MODE_PRIVATE)
        prefs.getString("folder_uri", null)?.let {
            folderUri = Uri.parse(it)
        }

        if (prefs.getBoolean("activated", false)) {
            showHome()
        } else {
            showActivation()
        }
    }

    private fun showActivation() {
        val layout = page()
        layout.gravity = Gravity.CENTER

        layout.addView(text("أمير للشاشات", 30f, white, true))
        addSpace(layout, 8)
        layout.addView(text("AMEER SCREENS", 15f, muted))
        addSpace(layout, 30)

        val input = EditText(this).apply {
            hint = "أدخل كود التفعيل"
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
            background = shape(card)
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }
        layout.addView(input, LinearLayout.LayoutParams(-1, -2))

        addButton(layout, "تفعيل التطبيق", Color.rgb(0, 155, 145)) {
            val code = input.text.toString().trim()
            if (code.isEmpty()) {
                input.error = "أدخل كود التفعيل"
                return@addButton
            }

            executor.execute {
                var success = false
                var message = "تعذر الاتصال بالخادم"
                try {
                    val connection = URL(serverUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use {
                        it.write(JSONObject().put("code", code).toString().toByteArray())
                    }
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    success = JSONObject(response).optBoolean("activated", false)
                    message = JSONObject(response).optString("message", "كود غير صحيح")
                    connection.disconnect()
                } catch (_: Exception) {}

                runOnUiThread {
                    if (success) {
                        getSharedPreferences("ameer", MODE_PRIVATE)
                            .edit().putBoolean("activated", true).apply()
                        showHome()
                    } else {
                        AlertDialog.Builder(this)
                            .setMessage(message)
                            .setPositiveButton("حسناً", null)
                            .show()
                    }
                }
            }
        }

        setContentView(layout)
    }

    private fun showHome() {
        root = page()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brand.addView(text("أمير للشاشات", 25f, white, true))
        brand.addView(text("مشغل الوسائط الذكي", 12f, muted))

        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val status = text("● متصل", 12f, Color.rgb(65, 220, 170), true)
        status.background = shape(card, 20)
        status.setPadding(dp(12), dp(9), dp(12), dp(9))
        header.addView(status)

        root.addView(header)
        addSpace(root, 22)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.rgb(17, 74, 91), 22)
            setPadding(dp(19), dp(19), dp(19), dp(19))
        }

        hero.addView(text("أهلاً بك في أمير للشاشات", 21f, white, true))
        addSpace(hero, 7)
        hero.addView(text("كل ملفاتك ووسائطك في مكان واحد", 13f,
            Color.rgb(215, 235, 240)))
        addSpace(hero, 15)

        val open = TextView(this).apply {
            text = "▶   اختر فيديو وابدأ المشاهدة"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            background = shape(Color.rgb(0, 145, 143), 12)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { pickVideo() }
        }
        hero.addView(open)
        root.addView(hero)

        addSpace(root, 23)
        root.addView(text("مشغل الفيديو", 19f, white, true))
        addSpace(root, 10)

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(card, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        videoView = VideoView(this)
        playerCard.addView(videoView, LinearLayout.LayoutParams(-1, dp(190)))

        playerCard.addView(text(videoName, 13f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(10))
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun control(label: String, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(white)
                background = shape(Color.rgb(39, 55, 80), 12)
                setPadding(dp(20), dp(10), dp(20), dp(10))
                setOnClickListener { action() }
            }
            controls.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }

        control("▶") {
            if (videoUri == null) pickVideo() else videoView?.start()
        }
        control("Ⅱ") { videoView?.pause() }
        control("■") { videoView?.stopPlayback() }

        playerCard.addView(controls)
        root.addView(playerCard)

        addSpace(root, 24)
        root.addView(text("الأقسام الرئيسية", 19f, white, true))
        addSpace(root, 12)

        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }

        fun tile(
            emoji: String,
            title: String,
            subtitle: String,
            color: Int,
            action: () -> Unit
        ) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(color, 18)
                setPadding(dp(13), dp(17), dp(13), dp(17))
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }

            box.addView(text(emoji, 27f))
            addSpace(box, 10)
            box.addView(text(title, 16f, white, true))
            addSpace(box, 4)
            box.addView(text(subtitle, 11f, Color.rgb(225, 230, 240)))

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(125)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(box, params)
        }

        tile("🎬", "الفيديوهات", "ملفات الفيديو", Color.rgb(42, 65, 112)) {
            pickVideo()
        }
        tile("📁", "مدير الملفات", "اختيار مجلد كامل", Color.rgb(26, 104, 103)) {
            showFolders()
        }
        tile("🎵", "الموسيقى", "ملفات الصوت", Color.rgb(104, 65, 125)) {
            pickAudio()
        }
        tile("🖼️", "الصور", "معرض الصور", Color.rgb(123, 76, 48)) {
            pickPhoto()
        }
        tile("🌐", "متصفح الإنترنت", "فتح صفحات الويب", Color.rgb(39, 91, 112)) {
            openBrowser()
        }
        tile("⚙️", "الإعدادات", "إعدادات التطبيق", Color.rgb(70, 78, 96)) {
            showSettings()
        }

        root.addView(grid)
        addSpace(root, 18)

        root.addView(text("AMEER SCREENS  •  1.2", 11f, muted).apply {
            gravity = Gravity.CENTER
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })

        videoUri?.let { videoView?.setVideoURI(it) }
    }

    private fun showFolders() {
        root = page()
        root.addView(text("مدير الملفات", 24f, white, true))
        addSpace(root, 8)
        root.addView(text("اختَر مجلداً كاملاً لعرض الفيديوهات الموجودة بداخله", 13f, muted))

        addButton(root, "📁 اختيار مجلد من الجهاز", Color.rgb(0, 125, 125)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, FOLDER)
        }

        addButton(root, "عرض محتويات المجلد المحدد") {
            folderUri?.let { listVideos(it) }
                ?: Toast.makeText(this, "اختَر مجلداً أولاً", Toast.LENGTH_SHORT).show()
        }

        addButton(root, "رجوع للرئيسية") { showHome() }

        setContentView(ScrollView(this).apply { addView(root) })

        folderUri?.let { listVideos(it) }
    }

    private fun listVideos(tree: Uri) {
        try {
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)

            contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->

                var count = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: "فيديو"
                    val mime = cursor.getString(2) ?: ""

                    if (mime.startsWith("video/")) {
                        count++
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

                        addButton(root, "▶   $name") {
                            videoUri = uri
                            videoName = name
                            showHome()
                            videoView?.setVideoURI(uri)
                            videoView?.start()
                        }
                    }
                }

                if (count == 0) {
                    addSpace(root, 15)
                    root.addView(text("لا توجد فيديوهات مباشرة داخل هذا المجلد", 13f, muted))
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر قراءة المجلد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettings() {
        root = page()
        root.addView(text("الإعدادات", 24f, white, true))

        val prefs = getSharedPreferences("ameer", MODE_PRIVATE)

        val keep = Switch(this).apply {
            text = "إبقاء الشاشة مضاءة"
            setTextColor(white)
            isChecked = prefs.getBoolean("keep_screen", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("keep_screen", checked).apply()
                if (checked) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        addSpace(root, 15)
        root.addView(keep)
        addButton(root, "معلومات التطبيق") {
            AlertDialog.Builder(this)
                .setTitle("أمير للشاشات")
                .setMessage("مشغل الوسائط - الإصدار 1.2")
                .setPositiveButton("حسناً", null)
                .show()
        }
        addButton(root, "رجوع للرئيسية") { showHome() }

        setContentView(root)
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, VIDEO)
    }

    private fun pickAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, AUDIO)
    }

    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PHOTO)
    }

    private fun openBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Legacy document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                data.flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
            )
        } catch (_: Exception) {}

        when (requestCode) {
            VIDEO -> {
                videoUri = uri
                videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "فيديو محدد"
                showHome()
                videoView?.setVideoURI(uri)
                videoView?.start()
            }

            FOLDER -> {
                folderUri = uri
                getSharedPreferences("ameer", MODE_PRIVATE)
                    .edit().putString("folder_uri", uri.toString()).apply()
                showFolders()
            }

            AUDIO -> {
                try {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(this@MainActivity, uri)
                        setOnPreparedListener { it.start() }
                        prepareAsync()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "تعذر تشغيل الصوت", Toast.LENGTH_SHORT).show()
                }
            }

            PHOTO -> {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("ameer", MODE_PRIVATE)
                .getBoolean("keep_screen", true)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        try { player?.release() } catch (_: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }
}
