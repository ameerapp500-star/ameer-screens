package com.carscreens.app;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;

/** Original Ameer Screens 15-entry menu, rendered with visible icons on the car display. */
public final class AmeerHomeScreen extends Screen {
    private static final String[][] MENU = new String[][] {
        {"yt", "يوتيوب", "فتح محتوى يوتيوب", "ic_media_play"},
        {"web", "متصفح الويب", "تصفح الإنترنت", "ic_menu_search"},
        {"tv", "تلفزيون", "القنوات التلفزيونية", "ic_menu_slideshow"},
        {"movies", "الأفلام والمسلسلات", "مكتبة الفيديو", "ic_menu_gallery"},
        {"files", "مدير الملفات", "ملفات الهاتف", "ic_menu_agenda"},
        {"video", "مشغل الفيديو", "تشغيل ملفات الفيديو", "ic_media_play"},
        {"photos", "معرض الصور", "صور الهاتف", "ic_menu_gallery"},
        {"radio", "الراديو", "محطات إذاعية حسب الدولة", "ic_lock_silent_mode_off"},
        {"music", "الموسيقى", "الأغاني الصوتية الموجودة على الهاتف", "ic_media_play"},
        {"news", "الأخبار", "مصادر الأخبار", "ic_menu_info_details"},
        {"kids", "يوتيوب كدز", "محتوى الأطفال", "ic_menu_myplaces"},
        {"maps", "الخرائط", "خرائط ومواقع", "ic_menu_mapmode"},
        {"apps", "تطبيقاتي", "التطبيقات المضافة", "ic_menu_manage"},
        {"mirror", "انعكاس الشاشة", "عرض شاشة الهاتف", "ic_menu_rotate"},
        {"set", "الإعدادات", "إعدادات أمير للشاشات", "ic_menu_preferences"}
    };

    public AmeerHomeScreen(CarContext context) { super(context); }

    private CarIcon icon(String name) {
        int id = getCarContext().getResources().getIdentifier(name, "drawable", "android");
        if (id == 0) id = android.R.drawable.ic_menu_view;
        return new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), id)).build();
    }

    @Override
    public Template onGetTemplate() {
        ItemList.Builder items = new ItemList.Builder();
        for (String[] entry : MENU) {
            final String key = entry[0];
            final String label = entry[1];
            Row.Builder row = new Row.Builder()
                    .setTitle(label)
                    .addText(entry[2])
                    .setImage(icon(entry[3]), Row.IMAGE_TYPE_ICON);
            if ("radio".equals(key) || "music".equals(key)) {
                row.setOnClickListener(() -> getScreenManager().push(
                        new AmeerMediaListScreen(getCarContext(), key, label)));
            } else {
                row.setOnClickListener(() -> getScreenManager().push(
                        new AmeerUnavailableScreen(getCarContext(), label)));
            }
            items.addItem(row.build());
        }
        return new ListTemplate.Builder()
                .setTitle("أمير للشاشات")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(items.build())
                .build();
    }
}
