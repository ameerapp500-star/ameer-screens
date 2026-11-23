package com.carscreens.app;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

/** Main car-safe menu. Only audio experiences are exposed while driving. */
public final class AmeerHomeScreen extends Screen {
    public AmeerHomeScreen(CarContext context) { super(context); }

    @Override
    public Template onGetTemplate() {
        ItemList items = new ItemList.Builder()
                .addItem(new Row.Builder()
                        .setTitle("الراديو")
                        .addText("محطات إذاعية حسب الدولة")
                        .setOnClickListener(() -> getScreenManager().push(
                                new AmeerMediaListScreen(getCarContext(), "radio", "الراديو")))
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("الموسيقى")
                        .addText("الأغاني الصوتية الموجودة على الهاتف")
                        .setOnClickListener(() -> getScreenManager().push(
                                new AmeerMediaListScreen(getCarContext(), "music", "الموسيقى")))
                        .build())
                .build();

        return new ListTemplate.Builder()
                .setTitle("أمير للشاشات")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(items)
                .build();
    }
}
