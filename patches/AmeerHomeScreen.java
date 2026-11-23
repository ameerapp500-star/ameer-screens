package com.carscreens.app;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

/** Android Auto-safe rendering of the original Ameer Screens home menu. */
public final class AmeerHomeScreen extends Screen {
    private static final String[][] MENU = new String[][] {
        {"yt", "يوتيوب", "فتح محتوى يوتيوب"},
        {"web", "متصفح الويب", "تصفح الإنترنت"},
        {"tv", "تلفزيون", "القنوات التلفزيونية"},
        {"movies", "الأفلام والمسلسلات", "مكتبة الفيديو"},
        {"files", "مدير الملفات", "ملفات الهاتف"},
        {"video", "مشغل الفيديو", "تشغيل ملفات الفيديو"},
        {"photos", "معرض الصور", "صور الهاتف"},
        {"radio", "الراديو", "محطات إذاعية حسب الدولة"},
        {"music", "الموسيقى", "الأغاني الصوتية الموجودة على الهاتف"},
        {"news", "الأخبار", "مصادر الأخبار"},
        {"kids", "يوتيوب كدز", "محتوى الأطفال"},
        {"maps", "الخرائط", "خرائط ومواقع"},
        {"apps", "تطبيقاتي", "التطبيقات المضافة"},
        {"mirror", "انعكاس الشاشة", "عرض شاشة الهاتف"},
        {"set", "الإعدادات", "إعدادات أمير للشاشات"}
    };

    public AmeerHomeScreen(CarContext context) { super(context); }

    @Override
    public Template onGetTemplate() {
        ItemList.Builder items = new ItemList.Builder();
        for (String[] entry : MENU) {
            final String key = entry[0];
            final String label = entry[1];
            Row.Builder row = new Row.Builder().setTitle(label).addText(entry[2]);
            if ("radio".equals(key) || "music".equals(key)) {
                row.setOnClickListener(() -> getScreenManager().push(
                        new AmeerMediaListScreen(getCarContext(), key, label)));
            } else {
                row.addText("غير متاحة من شاشة السيارة");
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
