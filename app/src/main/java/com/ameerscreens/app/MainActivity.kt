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
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
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

    private val bg = Color.rgb(7, 12, 22)
    private val card = Color.rgb(18, 27, 43)
    private val white = Color.WHITE
    private val muted = Color.rgb(151, 169, 191)
    private val teal = Color.rgb(0, 205, 190)

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

        val logo = TextView(this).apply {
            text = "▶"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(0, 220, 196), Color.rgb(0, 105, 120))
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        layout.addView(logo, LinearLayout.LayoutParams(dp(76), dp(76)))

        addSpace(layout, 18)
        layout.addView(text("أمير للشاشات", 30f, white, true))
        addSpace(layout, 8)
        layout.addView(text("AMEER SCREENS", 14f, muted))
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

        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "غير متاح"

        addSpace(layout, 16)
        layout.addView(text("معرّف الجهاز الخاص بالتفعيل", 13f, muted, true))
        addSpace(layout, 6)

        val idBox = TextView(this).apply {
            text = deviceId
            textSize = 13f
            setTextColor(white)
            gravity = Gravity.CENTER
            background = shape(card)
            setPadding(dp(12), dp(13), dp(12), dp(13))
        }
        layout.addView(idBox, LinearLayout.LayoutParams(-1, -2))

        addButton(layout, "نسخ معرّف الجهاز", Color.rgb(44, 60, 78)) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Ameer Device ID", deviceId))
            Toast.makeText(this, "تم نسخ معرّف الجهاز", Toast.LENGTH_SHORT).show()
        }

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
                        it.write(JSONObject().put("code", code).put("device_id", deviceId).put("deviceId", deviceId).toString().toByteArray())
                    }
                    val response = JSONObject(
                        connection.inputStream.bufferedReader().use { it.readText() }
                    )
                    success = response.optBoolean("activated", false)
                    message = response.optString("message", "كود غير صحيح")
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

    // الواجهة الرئيسية الجديدة
    private fun showHome() {
        root = page()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "▶"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(0, 220, 196), Color.rgb(0, 100, 125))
            ).apply { cornerRadius = dp(17).toFloat() }
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(text("أمير للشاشات", 22f, white, true))
        brand.addView(text("AMEER SCREENS  •  MEDIA PLAYER", 9f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val status = text("● جاهز", 11f, Color.rgb(73, 225, 177), true)
        status.background = shape(Color.rgb(18, 48, 48), 30)
        status.setPadding(dp(11), dp(9), dp(11), dp(9))
        header.addView(status)
        root.addView(header)

        addSpace(root, 22)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(0, 105, 112), Color.rgb(16, 48, 72), Color.rgb(20, 34, 58))
            ).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(20), dp(21), dp(20), dp(20))
        }

        hero.addView(text("عالم الترفيه بين إيديك", 22f, white, true))
        addSpace(hero, 7)
        hero.addView(text("شغّل فيديوهاتك واستمتع بملفاتك\nمن مكان واحد وبسهولة.", 13f,
            Color.rgb(218, 235, 240)))
        addSpace(hero, 18)

        val chooseVideo = TextView(this).apply {
            text = "▶    اختر فيديو الآن"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            background = shape(Color.rgb(0, 165, 155), 14)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            setOnClickListener { pickVideo() }
        }
        hero.addView(chooseVideo)
        root.addView(hero)

        addSpace(root, 24)

        val playerTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playerTitle.addView(text("مشغل الفيديو", 19f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        playerTitle.addView(text("VIDEO PLAYER", 9f, teal, true))
        root.addView(playerTitle)
        addSpace(root, 11)

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.rgb(15, 23, 37), 20)
            setPadding(dp(11), dp(11), dp(11), dp(13))
        }

        val videoFrame = FrameLayout(this).apply {
            background = shape(Color.rgb(3, 8, 15), 15)
        }
        videoView = VideoView(this)
        videoFrame.addView(videoView, FrameLayout.LayoutParams(-1, dp(190)))
        playerCard.addView(videoFrame, LinearLayout.LayoutParams(-1, dp(190)))

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(13), dp(4), dp(11))
        }
        val nameText = text(videoName, 12f, muted)
        nameText.maxLines = 1
        nameText.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        nameRow.addView(nameText, LinearLayout.LayoutParams(0, -2, 1f))
        nameRow.addView(text("●", 10f, teal))
        playerCard.addView(nameRow)

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
                background = shape(Color.rgb(34, 49, 69), 13)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener { action() }
            }
            controls.addView(b, LinearLayout.LayoutParams(0, dp(47), 1f).apply {
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

        addSpace(root, 25)

        val sectionTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sectionTitle.addView(text("استكشف الأقسام", 19f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        sectionTitle.addView(text("6 أقسام", 11f, muted))
        root.addView(sectionTitle)
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
                background = shape(color, 19)
                setPadding(dp(15), dp(15), dp(12), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }

            val iconBox = TextView(this).apply {
                text = emoji
                textSize = 25f
                gravity = Gravity.CENTER
                background = shape(Color.argb(45, 255, 255, 255), 13)
            }
            box.addView(iconBox, LinearLayout.LayoutParams(dp(48), dp(46)))
            addSpace(box, 12)
            box.addView(text(title, 15f, white, true))
            addSpace(box, 4)
            box.addView(text(subtitle, 10f, Color.rgb(213, 224, 237)))

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(139)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(box, params)
        }

        tile("🎬", "الفيديوهات", "تصفح وشغّل الفيديو", Color.rgb(30, 62, 91)) {
            pickVideo()
        }
        tile("📁", "مدير الملفات", "استعرض مجلداتك", Color.rgb(20, 78, 77)) {
            showFolders()
        }
        tile("🎵", "الموسيقى", "ملفاتك الصوتية", Color.rgb(66, 48, 91)) {
            pickAudio()
        }
        tile("🖼️", "الصور", "افتح معرض الصور", Color.rgb(88, 57, 43)) {
            pickPhoto()
        }
        tile("🌐", "متصفح الإنترنت", "تصفح مواقع الويب", Color.rgb(28, 67, 83)) {
            openBrowser()
        }
        tile("⚙️", "الإعدادات", "تحكم بالتطبيق", Color.rgb(49, 57, 75)) {
            showSettings()
        }

        root.addView(grid)
        addSpace(root, 21)

        val footerLine = View(this).apply {
            setBackgroundColor(Color.rgb(35, 49, 67))
        }
        root.addView(footerLine, LinearLayout.LayoutParams(-1, dp(1)))
        addSpace(root, 14)
        root.addView(text("AMEER SCREENS", 11f, teal, true).apply {
            gravity = Gravity.CENTER
        })
        addSpace(root, 4)
        root.addView(text("صُنع لتجربة مشاهدة أفضل  •  الإصدار 1.2", 10f, muted).apply {
            gravity = Gravity.CENTER
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
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
