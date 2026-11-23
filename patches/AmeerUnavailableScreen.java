package com.carscreens.app;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;

/** Explains why a phone-only feature cannot be exposed by Android Auto. */
public final class AmeerUnavailableScreen extends Screen {
    private final String feature;
    public AmeerUnavailableScreen(CarContext context, String feature) {
        super(context);
        this.feature = feature;
    }
    @Override public Template onGetTemplate() {
        return new MessageTemplate.Builder(
                "ميزة «" + feature + "» موجودة بالتطبيق الأصلي على الهاتف، لكن Android Auto لا يسمح بعرض واجهتها أو تشغيلها ضمن فئة تطبيقات الوسائط.")
                .setTitle(feature)
                .setHeaderAction(Action.BACK)
                .build();
    }
}
