package com.carscreens.app;

import android.content.pm.ApplicationInfo;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;
import androidx.car.app.Screen;
import androidx.car.app.CarContext;

/** Native Android Auto entry point for the Ameer Screens media experience. */
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
        return new Session() {
            @Override
            public Screen onCreateScreen(CarContext carContext) {
                return new AmeerHomeScreen(carContext);
            }
        };
    }
}
