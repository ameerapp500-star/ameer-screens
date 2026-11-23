package com.carscreens.app;

import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.MediaDescription;
import android.media.session.MediaController;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.media.MediaPlaybackManager;
import android.support.v4.media.session.MediaSessionCompat;

import java.util.ArrayList;
import java.util.List;

/** Renders the original AutoService MediaBrowser tree as native car-safe rows. */
public final class AmeerMediaListScreen extends Screen {
    private final String parentId;
    private final String title;
    private final List<MediaBrowser.MediaItem> children = new ArrayList<>();
    private MediaBrowser browser;
    private MediaController controller;
    private boolean loading = true;
    private String error;

    public AmeerMediaListScreen(CarContext context, String parentId, String title) {
        super(context);
        this.parentId = parentId;
        this.title = title;
        connect();
    }

    private void connect() {
        browser = new MediaBrowser(getCarContext(),
                new ComponentName(getCarContext(), AutoService.class),
                new MediaBrowser.ConnectionCallback() {
                    @Override public void onConnected() {
                        try {
                            controller = new MediaController(getCarContext(), browser.getSessionToken());
                            try {
                                MediaPlaybackManager manager = (MediaPlaybackManager)
                                        getCarContext().getCarService(CarContext.MEDIA_PLAYBACK_SERVICE);
                                manager.registerMediaPlaybackToken(
                                        MediaSessionCompat.Token.fromToken(browser.getSessionToken()));
                            } catch (Exception ignored) { }
                            browser.subscribe(parentId, new MediaBrowser.SubscriptionCallback() {
                                @Override public void onChildrenLoaded(String id, List<MediaBrowser.MediaItem> items) {
                                    children.clear();
                                    if (items != null) children.addAll(items);
                                    loading = false;
                                    invalidate();
                                }
                                @Override public void onError(String id) {
                                    loading = false;
                                    error = "تعذر تحميل القائمة";
                                    invalidate();
                                }
                            });
                        } catch (Exception e) {
                            loading = false;
                            error = "تعذر الاتصال بخدمة الوسائط";
                            invalidate();
                        }
                    }
                    @Override public void onConnectionFailed() {
                        loading = false;
                        error = "تعذر الاتصال بخدمة أمير للشاشات";
                        invalidate();
                    }
                }, null);
        browser.connect();
        getLifecycle().addObserver((source, event) -> {
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY && browser != null) {
                try { if (browser.isConnected()) browser.disconnect(); } catch (Exception ignored) { }
            }
        });
    }

    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();
        if (loading) {
            list.addItem(new Row.Builder().setTitle("جارٍ تحميل المحتوى...").build());
        } else if (!TextUtils.isEmpty(error)) {
            list.addItem(new Row.Builder().setTitle(error).build());
        } else if (children.isEmpty()) {
            list.addItem(new Row.Builder().setTitle("لا يوجد محتوى متاح حالياً").build());
        } else {
            for (MediaBrowser.MediaItem child : children) {
                MediaDescription description = child.getDescription();
                CharSequence itemTitle = description == null ? "عنصر صوتي" : description.getTitle();
                CharSequence subtitle = description == null ? null : description.getSubtitle();
                String id = child.getMediaId();
                Row.Builder row = new Row.Builder().setTitle(itemTitle == null ? "عنصر صوتي" : itemTitle);
                if (!TextUtils.isEmpty(subtitle)) row.addText(subtitle);
                row.setOnClickListener(() -> {
                    if (child.isBrowsable()) {
                        getScreenManager().push(new AmeerMediaListScreen(getCarContext(), id,
                                itemTitle == null ? title : itemTitle.toString()));
                    } else if (child.isPlayable() && controller != null) {
                        controller.getTransportControls().playFromMediaId(id, new Bundle());
                        getScreenManager().push(new AmeerNowPlayingScreen(getCarContext()));
                    }
                });
                list.addItem(row.build());
            }
        }
        return new ListTemplate.Builder()
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .setSingleList(list.build())
                .build();
    }
}
