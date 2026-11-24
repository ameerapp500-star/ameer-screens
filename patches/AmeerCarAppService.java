package com.carscreens.app;

import android.Manifest;
import android.app.Activity;
import android.app.Presentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Personal Ameer Screens car entry. It renders the original index.html on the
 * Android Auto surface so the same 15-module interface and activation screen
 * are used on phone and car.
 */
public final class AmeerCarAppService extends CarAppService {
    @Override
    public HostValidator createHostValidator() {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        }
        return new HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build();
    }

    @Override
    public Session onCreateSession() {
        return new AmeerSession();
    }

    private static final class AmeerSession extends Session {
        @Override
        public Screen onCreateScreen(Intent intent) {
            return new AmeerSurfaceScreen(getCarContext());
        }
    }

    private static final class AmeerSurfaceScreen extends Screen {
        private final SurfaceDisplay display;
        private final SurfaceCallback callback;

        AmeerSurfaceScreen(CarContext context) {
            super(context);
            display = new SurfaceDisplay(context);
            callback = new SurfaceCallback() {
                @Override public void onSurfaceAvailable(SurfaceContainer container) {
                    display.attach(container);
                }
                @Override public void onSurfaceDestroyed(SurfaceContainer container) {
                    display.detachSurface();
                }
                @Override public void onClick(float x, float y) {
                    display.tap(x, y);
                }
                @Override public void onScroll(float distanceX, float distanceY) {
                    display.scroll(distanceX, distanceY);
                }
            };
            context.getCarService(AppManager.class).setSurfaceCallback(callback);
            getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
                @Override public void onStart(androidx.lifecycle.LifecycleOwner owner) {
                    display.setActive(true);
                }
                @Override public void onStop(androidx.lifecycle.LifecycleOwner owner) {
                    display.setActive(false);
                }
                @Override public void onDestroy(androidx.lifecycle.LifecycleOwner owner) {
                    display.release();
                }
            });
        }

        @Override
        public Template onGetTemplate() {
            ActionStrip strip = new ActionStrip.Builder()
                    .addAction(new Action.Builder()
                            .setTitle("رجوع")
                            .setOnClickListener(display::goBack)
                            .build())
                    .addAction(new Action.Builder()
                            .setTitle("الرئيسية")
                            .setOnClickListener(display::goHome)
                            .build())
                    .build();
            return new NavigationTemplate.Builder()
                    .setActionStrip(strip)
                    .build();
        }
    }

    private static final class SurfaceDisplay {
        private static final int DISPLAY_FLAGS =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        private final Context context;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private SurfaceContainer container;
        private VirtualDisplay virtualDisplay;
        private Presentation presentation;
        private FrameLayout root;
        private WebView webView;
        private View customView;
        private WebChromeClient.CustomViewCallback customViewCallback;
        private boolean active;
        private boolean gestureActive;
        private float fingerX;
        private float fingerY;
        private long gestureDownTime;
        private final Runnable liftFinger = this::finishGesture;

        SurfaceDisplay(Context context) {
            this.context = context.getApplicationContext();
        }

        void setActive(boolean value) {
            handler.post(() -> {
                active = value;
                if (active) attachNow();
                else detachNow();
            });
        }

        void attach(SurfaceContainer value) {
            handler.post(() -> {
                container = value;
                if (active) attachNow();
            });
        }

        void detachSurface() {
            handler.post(() -> {
                container = null;
                detachNow();
            });
        }

        void release() {
            handler.post(() -> {
                container = null;
                active = false;
                detachNow();
            });
        }

        private void attachNow() {
            detachNow();
            SurfaceContainer c = container;
            if (!active || c == null || c.getSurface() == null) return;
            try {
                DisplayManager manager =
                        (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                virtualDisplay = manager.createVirtualDisplay(
                        "AmeerScreensCarDisplay",
                        c.getWidth(),
                        c.getHeight(),
                        c.getDpi(),
                        c.getSurface(),
                        DISPLAY_FLAGS
                );
                if (virtualDisplay == null) return;
                presentation = new Presentation(context, virtualDisplay.getDisplay());
                root = new FrameLayout(presentation.getContext());
                webView = new WebView(presentation.getContext());
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setAllowFileAccess(true);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                webView.addJavascriptInterface(new AmeerCarBridge(context, this), "Android");
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return handleNavigation(request.getUrl().toString());
                    }

                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        return interceptLocalFile(request.getUrl());
                    }
                });
                webView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                     FileChooserParams params) {
                        PickerActivity.launchFile(context, callback);
                        return true;
                    }

                    @Override
                    public void onShowCustomView(View view, CustomViewCallback callback) {
                        if (root == null || webView == null) return;
                        customView = view;
                        customViewCallback = callback;
                        webView.setVisibility(View.GONE);
                        root.addView(customView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
                    }

                    @Override
                    public void onHideCustomView() {
                        if (root == null || customView == null) return;
                        root.removeView(customView);
                        customView = null;
                        if (webView != null) webView.setVisibility(View.VISIBLE);
                        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                        customViewCallback = null;
                    }
                });
                root.addView(webView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                presentation.setContentView(root);
                presentation.show();
                webView.loadUrl("file:///android_asset/index.html");
            } catch (Exception e) {
                detachNow();
            }
        }

        private void detachNow() {
            cancelGesture();
            try {
                if (presentation != null) presentation.dismiss();
            } catch (Exception ignored) {}
            if (webView != null) {
                try { webView.stopLoading(); webView.destroy(); } catch (Exception ignored) {}
            }
            if (virtualDisplay != null) {
                try { virtualDisplay.release(); } catch (Exception ignored) {}
            }
            presentation = null;
            virtualDisplay = null;
            root = null;
            webView = null;
            customView = null;
            customViewCallback = null;
        }

        private boolean handleNavigation(String url) {
            if (url == null) return true;
            if (url.startsWith("http://") || url.startsWith("https://")
                    || url.startsWith("file://") || url.startsWith("about:")
                    || url.startsWith("data:") || url.startsWith("blob:")) return false;
            try {
                Intent intent = url.startsWith("intent:")
                        ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
            return true;
        }

        private WebResourceResponse interceptLocalFile(Uri requestUri) {
            if (requestUri == null || !"localfile.invalid".equals(requestUri.getHost())) return null;
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            try {
                String targetText = requestUri.getQueryParameter("u");
                Uri target = Uri.parse(targetText);
                String mime = context.getContentResolver().getType(target);
                if (mime == null) mime = "application/octet-stream";
                InputStream stream = context.getContentResolver().openInputStream(target);
                return new WebResourceResponse(mime, null, 200, "OK", headers, stream);
            } catch (Exception e) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                        headers, new java.io.ByteArrayInputStream(new byte[0]));
            }
        }

        void openUrl(String url) {
            handler.post(() -> {
                if (url == null) return;
                if (url.startsWith("intent:")) {
                    handleNavigation(url);
                } else if (webView != null) {
                    webView.loadUrl(url);
                }
            });
        }

        void goBack() {
            handler.post(() -> {
                if (webView == null) return;
                if (webView.canGoBack()) webView.goBack();
                else webView.loadUrl("file:///android_asset/index.html");
            });
        }

        void goHome() {
            handler.post(() -> {
                if (webView != null) {
                    webView.loadUrl("file:///android_asset/index.html");
                    webView.clearHistory();
                }
            });
        }

        void playVideo(String source) {
            handler.post(() -> {
                if (webView == null || source == null) return;
                try {
                    String src = source;
                    if (source.startsWith("content:")) {
                        src = "https://localfile.invalid/?u=" + URLEncoder.encode(source, "UTF-8");
                    }
                    String html = "<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                            + "<body style='margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh'>"
                            + "<video controls autoplay playsinline style='width:100%;height:100%;object-fit:contain' src=\""
                            + src.replace("&", "&amp;").replace("\"", "&quot;")
                            + "\"></video></body></html>";
                    webView.loadDataWithBaseURL("https://localfile.invalid/", html, "text/html", "UTF-8", null);
                } catch (Exception ignored) {}
            });
        }

        void tap(float x, float y) {
            handler.post(() -> {
                View target = decorView();
                if (target == null) return;
                finishGesture();
                long down = SystemClock.uptimeMillis();
                dispatch(target, MotionEvent.ACTION_DOWN, x, y, down);
                dispatch(target, MotionEvent.ACTION_UP, x, y, down);
            });
        }

        void scroll(float dx, float dy) {
            handler.post(() -> {
                View target = decorView();
                if (target == null) return;
                if (!gestureActive) beginGesture(target);
                float rawX = fingerX - dx;
                float rawY = fingerY - dy;
                float margin = 24f;
                float x = Math.max(margin, Math.min(target.getWidth() - margin, rawX));
                float y = Math.max(margin, Math.min(target.getHeight() - margin, rawY));
                if (x != rawX || y != rawY) {
                    finishGesture();
                    return;
                }
                fingerX = x;
                fingerY = y;
                dispatch(target, MotionEvent.ACTION_MOVE, fingerX, fingerY, gestureDownTime);
                handler.removeCallbacks(liftFinger);
                handler.postDelayed(liftFinger, 140);
            });
        }

        private void beginGesture(View target) {
            gestureDownTime = SystemClock.uptimeMillis();
            fingerX = target.getWidth() / 2f;
            fingerY = target.getHeight() / 2f;
            dispatch(target, MotionEvent.ACTION_DOWN, fingerX, fingerY, gestureDownTime);
            gestureActive = true;
        }

        private void finishGesture() {
            if (!gestureActive) return;
            gestureActive = false;
            handler.removeCallbacks(liftFinger);
            View target = decorView();
            if (target != null) dispatch(target, MotionEvent.ACTION_UP, fingerX, fingerY, gestureDownTime);
        }

        private void cancelGesture() {
            handler.removeCallbacks(liftFinger);
            if (gestureActive) finishGesture();
        }

        private View decorView() {
            return presentation == null || presentation.getWindow() == null
                    ? null : presentation.getWindow().getDecorView();
        }

        private void dispatch(View target, int action, float x, float y, long downTime) {
            MotionEvent event = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            target.dispatchTouchEvent(event);
            event.recycle();
        }
    }

    private static final class AmeerCarBridge {
        private final Context context;
        private final SurfaceDisplay display;

        AmeerCarBridge(Context context, SurfaceDisplay display) {
            this.context = context.getApplicationContext();
            this.display = display;
        }

        @JavascriptInterface public String deviceId() {
            String id = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return id == null ? "UNKNOWN" : id.toUpperCase();
        }

        @JavascriptInterface public void openWeb(String url) {
            display.openUrl(url);
        }

        @JavascriptInterface public void openApp(String packageName) {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public String listApps() {
            JSONArray result = new JSONArray();
            try {
                Intent query = new Intent(Intent.ACTION_MAIN);
                query.addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager pm = context.getPackageManager();
                for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                    JSONObject item = new JSONObject();
                    item.put("n", info.loadLabel(pm).toString());
                    item.put("p", info.activityInfo.packageName);
                    result.put(item);
                }
            } catch (Exception ignored) {}
            return result.toString();
        }

        @JavascriptInterface public void pickFolder() {
            PickerActivity.launchFolder(context);
        }

        @JavascriptInterface public String folders() {
            return context.getSharedPreferences("f", 0).getString("l", "[]");
        }

        @JavascriptInterface public void removeFolder(String tree) {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences("f", 0);
                JSONArray old = new JSONArray(prefs.getString("l", "[]"));
                JSONArray next = new JSONArray();
                for (int i = 0; i < old.length(); i++) {
                    JSONObject item = old.getJSONObject(i);
                    if (!tree.equals(item.optString("t"))) next.put(item);
                }
                prefs.edit().putString("l", next.toString()).apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public String listDir(String tree, String documentId) {
            JSONArray result = new JSONArray();
            Cursor cursor = null;
            try {
                Uri treeUri = Uri.parse(tree);
                String doc = documentId;
                if (doc == null || doc.length() == 0) doc = DocumentsContract.getTreeDocumentId(treeUri);
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, doc);
                cursor = context.getContentResolver().query(children,
                        new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE},
                        null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        JSONObject item = new JSONObject();
                        String id = cursor.getString(0);
                        String mime = cursor.getString(2);
                        item.put("id", id);
                        item.put("n", cursor.getString(1));
                        item.put("m", mime);
                        item.put("d", DocumentsContract.Document.MIME_TYPE_DIR.equals(mime));
                        item.put("u", DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString());
                        result.put(item);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
            return result.toString();
        }

        @JavascriptInterface public String listMedia(String kind) {
            String requiredPermission;
            if (Build.VERSION.SDK_INT >= 33) {
                requiredPermission = "audio".equals(kind) ? Manifest.permission.READ_MEDIA_AUDIO
                        : ("video".equals(kind) ? Manifest.permission.READ_MEDIA_VIDEO
                        : Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            if (context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) return "PERM";
            Uri base;
            if ("audio".equals(kind)) base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            else if ("video".equals(kind)) base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            else base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            JSONArray result = new JSONArray();
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(base,
                        new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME},
                        null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC");
                if (cursor != null) {
                    int count = 0;
                    while (cursor.moveToNext() && count++ < 500) {
                        JSONObject item = new JSONObject();
                        item.put("n", cursor.getString(1));
                        item.put("u", ContentUris.withAppendedId(base, cursor.getLong(0)).toString());
                        result.put(item);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
            return result.toString();
        }

        @JavascriptInterface public void copy(String value) {
            try {
                ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText("Ameer Screens", value));
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public void playVideo(String uri) {
            display.playVideo(uri);
        }

        @JavascriptInterface public void view(String uri, String mime) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(uri), mime == null || mime.length() == 0 ? "*/*" : mime);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    /** Small transparent phone-side picker used by the file manager and M3U chooser. */
    public static final class PickerActivity extends Activity {
        private static ValueCallback<Uri[]> pendingFileCallback;

        static void launchFolder(Context context) {
            Intent intent = new Intent(context, PickerActivity.class);
            intent.putExtra("folder", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        static void launchFile(Context context, ValueCallback<Uri[]> callback) {
            pendingFileCallback = callback;
            Intent intent = new Intent(context, PickerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        @Override protected void onCreate(Bundle state) {
            super.onCreate(state);
            if (getIntent().getBooleanExtra("folder", false)) {
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(picker, 2);
            } else {
                Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                picker.setType("*/*");
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(picker, 1);
            }
        }

        @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == 1) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(
                            WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                    pendingFileCallback = null;
                }
            } else if (requestCode == 2 && resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    Uri tree = data.getData();
                    getContentResolver().takePersistableUriPermission(
                            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String id = DocumentsContract.getTreeDocumentId(tree);
                    String name = id.contains(":") ? id.substring(id.lastIndexOf(':') + 1) : id;
                    if (name.length() == 0) name = "الذاكرة";
                    android.content.SharedPreferences prefs = getSharedPreferences("f", 0);
                    JSONArray folders = new JSONArray(prefs.getString("l", "[]"));
                    JSONObject folder = new JSONObject();
                    folder.put("t", tree.toString());
                    folder.put("n", name);
                    folders.put(folder);
                    prefs.edit().putString("l", folders.toString()).apply();
                } catch (Exception ignored) {}
            }
            finish();
        }
    }
}
