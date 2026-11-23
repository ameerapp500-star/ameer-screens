package com.ameerscreens.app.auto

import android.app.*
import android.content.*
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.Surface
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate

object AmeerProjectionBridge {
    @Volatile var projection: MediaProjection? = null
    @Volatile var display: VirtualDisplay? = null
    @Volatile var surface: Surface? = null
    @Volatile var width = 800
    @Volatile var height = 480
    @Volatile var density = 160
    private var callback: MediaProjection.Callback? = null

    fun attach(container: SurfaceContainer) {
        surface = container.surface
        width = container.width.coerceAtLeast(1)
        height = container.height.coerceAtLeast(1)
        density = container.dpi.coerceAtLeast(1)
        val d = display
        if (d == null) createDisplay() else {
            d.resize(width, height, density)
            d.surface = surface
        }
    }

    fun detach() {
        surface = null
        display?.setSurface(null)
    }

    fun start(context: Context, resultCode: Int, data: Intent) {
        if (projection != null) return
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = manager.getMediaProjection(resultCode, data) ?: return
        val cb = object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }
        p.registerCallback(cb, Handler(Looper.getMainLooper()))
        projection = p
        callback = cb
        createDisplay()
    }

    private fun createDisplay() {
        val p = projection ?: return
        val s = surface ?: return
        if (display == null) {
            val dm = AmeerProjectionService.instance?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
            display = p.createVirtualDisplay(
                "AmeerScreens-Car", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, s, null, Handler(Looper.getMainLooper())
            )
        } else {
            display?.resize(width, height, density)
            display?.surface = s
        }
    }

    fun stop() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { callback?.let { projection?.unregisterCallback(it) } } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        callback = null
    }

    fun tap(x: Float, y: Float): Boolean = AmeerTouchService.tap(x, y)
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean = AmeerTouchService.swipe(x1, y1, x2, y2)
}

class AmeerCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = AmeerCarSession()
}

class AmeerCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = AmeerCarScreen(carContext)
}

class AmeerCarScreen(context: CarContext) : Screen(context) {
    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            AmeerProjectionBridge.attach(container)
            if (AmeerProjectionBridge.projection == null) requestPermission()
        }
        override fun onSurfaceDestroyed(container: SurfaceContainer) { AmeerProjectionBridge.detach() }
        override fun onClick(x: Float, y: Float) {
            val dm = carContext.resources.displayMetrics
            val px = x / AmeerProjectionBridge.width * dm.widthPixels
            val py = y / AmeerProjectionBridge.height * dm.heightPixels
            AmeerProjectionBridge.tap(px, py)
        }
        override fun onScroll(distanceX: Float, distanceY: Float) {
            val dm = carContext.resources.displayMetrics
            val sx = dm.widthPixels / 2f
            val sy = dm.heightPixels / 2f
            val ex = (sx - distanceX * dm.widthPixels / AmeerProjectionBridge.width).coerceIn(1f, dm.widthPixels - 1f)
            val ey = (sy - distanceY * dm.heightPixels / AmeerProjectionBridge.height).coerceIn(1f, dm.heightPixels - 1f)
            AmeerProjectionBridge.swipe(sx, sy, ex, ey)
        }
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
    }

    override fun onGetTemplate(): Template {
        val action = Action.Builder()
            .setTitle("إعداد العرض")
            .setOnClickListener { requestPermission() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(action).build())
            .withPan()
            .build()
    }

    private fun requestPermission() {
        val intent = Intent(carContext, AmeerProjectionSetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            carContext, 771, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = carContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("ameer_projection", "عرض أمير للشاشات", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(carContext, "ameer_projection") else Notification.Builder(carContext)
        nm.notify(771, notification.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("أمير للشاشات")
            .setContentText("اضغط لتمكين عرض التطبيق والتحكم من شاشة السيارة")
            .setContentIntent(pending).setAutoCancel(true).build())
        try { carContext.startActivity(intent) } catch (_: Exception) {}
    }
}

class AmeerProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); instance = this }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("ameer_mirror", "عرض الشاشة", NotificationManager.IMPORTANCE_LOW))
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "ameer_mirror") else Notification.Builder(this)
        val n = builder.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("أمير للشاشات")
            .setContentText("العرض على شاشة السيارة يعمل").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(772, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(772, n)
        val code = intent?.getIntExtra("result_code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("result_data", Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("result_data")
        if (data != null && code == Activity.RESULT_OK) AmeerProjectionBridge.start(this, code, data)
        return START_NOT_STICKY
    }
    override fun onDestroy() { AmeerProjectionBridge.stop(); instance = null; super.onDestroy() }
    companion object { @Volatile var instance: AmeerProjectionService? = null }
}

class AmeerTouchService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    companion object {
        @Volatile private var instance: AmeerTouchService? = null
        fun tap(x: Float, y: Float): Boolean = send(Path().apply { moveTo(x, y) }, 55)
        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
            send(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 180)
        private fun send(path: Path, duration: Long): Boolean {
            val service = instance ?: return false
            val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
            return service.dispatchGesture(gesture, null, null)
        }
    }
}

class AmeerProjectionSetupActivity : Activity() {
    private val requestCode = 773
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.rgb(9, 16, 28))
        }
        val title = android.widget.TextView(this).apply {
            text = "أمير للشاشات\n\nلإظهار واجهة التطبيق على شاشة السيارة، اسمح بتسجيل الشاشة ثم فعّل خدمة التحكم."
            textSize = 18f; setTextColor(Color.WHITE)
        }
        val capture = android.widget.Button(this).apply {
            text = "السماح بعرض الشاشة"
            setOnClickListener {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                @Suppress("DEPRECATION") startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
            }
        }
        val accessibility = android.widget.Button(this).apply {
            text = "تفعيل التحكم اللمسي"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        layout.addView(title); layout.addView(capture); layout.addView(accessibility)
        setContentView(layout)
    }
    @Deprecated("Legacy activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, AmeerProjectionService::class.java)
                .putExtra("result_code", resultCode).putExtra("result_data", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            startActivity(Intent(this, com.ameerscreens.app.MainActivity::class.java))
            finish()
        }
    }
}
