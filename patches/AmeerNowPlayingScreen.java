package com.carscreens.app;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.MediaPlaybackTemplate;
import androidx.car.app.model.Template;

/** Host-rendered playback controls backed by the app's existing MediaSession. */
public final class AmeerNowPlayingScreen extends Screen {
    public AmeerNowPlayingScreen(CarContext context) { super(context); }

    @Override
    public Template onGetTemplate() {
        Header header = new Header.Builder()
                .setTitle("أمير للشاشات")
                .setStartHeaderAction(Action.BACK)
                .build();
        return new MediaPlaybackTemplate.Builder()
                .setHeader(header)
                .build();
    }
}
