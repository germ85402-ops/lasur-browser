package com.lumen.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

final class Ui {
    static float density;
    static boolean dark;
    static int BG, SURFACE, TOOLBAR, PILL, TEXT, TEXT2, ACCENT, DIVIDER, SWITCHER_BG;
    static final int INC_TOOLBAR = 0xFF202124, INC_PILL = 0xFF3C4043, INC_TEXT = 0xFFE8EAED, INC_TEXT2 = 0xFF9AA0A6, INC_BG = 0xFF202124,
            INC_SURFACE = 0xFF35363A, INC_DIVIDER = 0xFF3C4043;
    /** Chips/tiles, secondary chips, snackbar, pull-to-refresh indicator. */
    static int CHIP, CHIP2, SNACK, PTR;

    static final String[] ACCENT_NAMES = {"Синий", "Зелёный", "Фиолетовый", "Розовый", "Оранжевый", "Бирюзовый", "Красный"};
    static final int[] ACCENT_LIGHT = {0xFF1A73E8, 0xFF188038, 0xFF8430CE, 0xFFD01884, 0xFFB95000, 0xFF007B83, 0xFFD93025};
    static final int[] ACCENT_DARK = {0xFF8AB4F8, 0xFF81C995, 0xFFC58AF9, 0xFFFF8BCB, 0xFFFCAD70, 0xFF78D9EC, 0xFFF28B82};
    static final String[] MODE_NAMES = {"Как в системе", "Светлая", "Тёмная", "Чёрная (AMOLED)"};
    static int TONAL, ON_TONAL, CARD, mode, accent;
    /** Popup menus and dialogs, search pill on the home page, disabled icons, error/warning colors. */
    static int MENU_SURFACE, NTP_PILL, DISABLED, ERROR, WARN;
    static boolean amoled;

    static boolean resolveDark(Context c, int mode) {
        if (mode == 1) return false;
        if (mode >= 2) return true;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static void init(Context c) { init(c, 0, 0); }

    static void init(Context c, int themeMode, int accentIdx) {
        density = c.getResources().getDisplayMetrics().density;
        mode = themeMode;
        accent = accentIdx < 0 || accentIdx >= ACCENT_LIGHT.length ? 0 : accentIdx;
        dark = resolveDark(c, themeMode);
        amoled = themeMode == 3;
        if (dark) {
            BG = amoled ? 0xFF000000 : 0xFF202124; SURFACE = amoled ? 0xFF121212 : 0xFF2D2E31; TOOLBAR = BG; PILL = amoled ? 0xFF1F1F1F : 0xFF3C4043;
            TEXT = 0xFFE8EAED; TEXT2 = 0xFF9AA0A6; ACCENT = ACCENT_DARK[accent]; DIVIDER = amoled ? 0xFF262626 : 0xFF3C4043; SWITCHER_BG = BG;
            CARD = amoled ? 0xFF121212 : 0xFF292A2D;
            TONAL = blend(ACCENT, BG, 0.68f); ON_TONAL = blend(ACCENT, 0xFFFFFFFF, 0.55f);
            MENU_SURFACE = amoled ? 0xFF161616 : 0xFF2D2E31; NTP_PILL = amoled ? 0xFF1F1F1F : 0xFF303134;
            DISABLED = 0xFF5F6368; ERROR = 0xFFF28B82; WARN = 0xFFFDD663;
            CHIP = amoled ? 0xFF262626 : 0xFF3C4043; CHIP2 = amoled ? 0xFF1F1F1F : 0xFF303134; SNACK = 0xFFE8EAED; PTR = 0xFF3C4043;
        } else {
            BG = 0xFFFFFFFF; SURFACE = 0xFFFFFFFF; TOOLBAR = 0xFFFFFFFF; PILL = 0xFFF1F3F4;
            TEXT = 0xFF202124; TEXT2 = 0xFF5F6368; ACCENT = ACCENT_LIGHT[accent]; DIVIDER = 0xFFE0E0E0; SWITCHER_BG = 0xFFF1F3F4;
            CARD = 0xFFF8F9FA;
            TONAL = blend(ACCENT, 0xFFFFFFFF, 0.82f); ON_TONAL = blend(ACCENT, 0xFF000000, 0.55f);
            MENU_SURFACE = 0xFFFFFFFF; NTP_PILL = 0xFFFFFFFF;
            DISABLED = 0xFFBDC1C6; ERROR = 0xFFD93025; WARN = 0xFFB06000;
            CHIP = 0xFFF1F3F4; CHIP2 = 0xFFF1F3F4; SNACK = 0xFF303134; PTR = 0xFFFFFFFF;
        }
    }

    static int dp(float v) { return Math.round(v * density); }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static GradientDrawable stroke(int fill, int strokeColor, float strokeDp, float radiusDp) {
        GradientDrawable g = round(fill, radiusDp);
        g.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static Drawable ripple(Context c, boolean borderless) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(borderless ? android.R.attr.selectableItemBackgroundBorderless : android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }

    static ImageView icon(Context c, int res, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(tint));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return iv;
    }

    static ImageView iconBtn(Context c, int res, int tint) {
        ImageView iv = icon(c, res, tint);
        iv.setBackground(ripple(c, true));
        iv.setClickable(true);
        iv.setFocusable(true);
        describe(iv, label(res));
        return iv;
    }

    /** Spoken label (TalkBack) and long-press tooltip for an icon-only button. */
    static void describe(android.view.View v, String text) {
        if (text == null) return;
        v.setContentDescription(text);
        if (android.os.Build.VERSION.SDK_INT >= 26) v.setTooltipText(text);
    }

    /** Default accessibility label for an icon. */
    static String label(int res) {
        if (res == R.drawable.ic_home) return L.t("Главная страница");
        if (res == R.drawable.ic_back) return L.t("Назад");
        if (res == R.drawable.ic_forward) return L.t("Вперёд");
        if (res == R.drawable.ic_more) return L.t("Меню");
        if (res == R.drawable.ic_close) return L.t("Закрыть");
        if (res == R.drawable.ic_mic) return L.t("Голосовой поиск");
        if (res == R.drawable.ic_search) return L.t("Поиск");
        if (res == R.drawable.ic_up) return L.t("Предыдущее совпадение");
        if (res == R.drawable.ic_down) return L.t("Следующее совпадение");
        if (res == R.drawable.ic_edit) return L.t("Изменить");
        if (res == R.drawable.ic_star) return L.t("Удалить закладку");
        if (res == R.drawable.ic_star_border) return L.t("Добавить в закладки");
        if (res == R.drawable.ic_download) return L.t("Скачать");
        if (res == R.drawable.ic_info) return L.t("Информация о сайте");
        if (res == R.drawable.ic_refresh) return L.t("Обновить");
        if (res == R.drawable.ic_settings) return L.t("Настройки");
        if (res == R.drawable.ic_palette) return L.t("Темы и обои");
        if (res == R.drawable.ic_copy) return L.t("Вставить из буфера обмена");
        if (res == R.drawable.ic_share) return L.t("Поделиться");
        if (res == R.drawable.ic_add) return L.t("Добавить");
        if (res == R.drawable.ic_bookmarks) return L.t("Закладки");
        if (res == R.drawable.ic_history) return L.t("История");
        if (res == R.drawable.ic_key) return L.t("Пароли");
        if (res == R.drawable.ic_lock) return L.t("Подключение защищено");
        if (res == R.drawable.ic_incognito) return L.t("Инкогнито");
        if (res == R.drawable.ic_desktop) return L.t("Версия для ПК");
        if (res == R.drawable.ic_shield) return L.t("Блокировка рекламы");
        if (res == R.drawable.ic_video) return L.t("Видео");
        if (res == R.drawable.ic_translate) return L.t("Перевести страницу");
        if (res == R.drawable.ic_eye) return L.t("Показать");
        if (res == R.drawable.ic_check) return L.t("Готово");
        if (res == R.drawable.ic_fullscreen_exit) return L.t("Выйти из полноэкранного режима");
        if (res == R.drawable.ic_play) return L.t("Воспроизвести");
        if (res == R.drawable.ic_wallpaper) return L.t("Обои");
        if (res == R.drawable.ic_globe) return L.t("Сайт");
        return null;
    }

    static void tint(ImageView iv, int color) { iv.setImageTintList(ColorStateList.valueOf(color)); }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    static TextView single(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    static TextView medium(TextView t) { t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t; }

    static int letterColor(String s) {
        int[] cols = {0xFF1A73E8, 0xFFD93025, 0xFF188038, 0xFFE37400, 0xFF9334E6, 0xFF007B83, 0xFFC5221F, 0xFF1967D2};
        return cols[Math.abs(s.hashCode()) % cols.length];
    }

    static TextView letterTile(Context c, String label, int sizeDp, int bg) {
        TextView t = new TextView(c);
        String l = label == null || label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
        t.setText(l);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(letterColor(label == null ? "" : label));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.42f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(oval(bg));
        return t;
    }
}
