package com.lumen.browser;
import android.util.Log;
import android.graphics.drawable.ColorDrawable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full-screen lists: history and bookmarks. */
final class Lists {
    final MainActivity act;

    Lists(MainActivity act) { this.act = act; }

    Dialog fullDialog(String title, View body, String action, Runnable onAction) {
        final Dialog d = new Dialog(act, Ui.dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);
        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(act.dp(4), 0, act.dp(8), 0);
        ImageView back = Ui.iconBtn(act, R.drawable.ic_back, Ui.TEXT2);
        back.setOnClickListener(v -> d.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
        TextView tt = Ui.medium(Ui.text(act, title, 20, Ui.TEXT));
        tt.setPaddingRelative(act.dp(12), 0, 0, 0);
        bar.addView(tt, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        if (action != null) {
            TextView a = Ui.medium(Ui.text(act, action, 15, Ui.ACCENT));
            a.setPaddingRelative(act.dp(12), act.dp(12), act.dp(12), act.dp(12));
            a.setBackground(Ui.ripple(act, true));
            a.setOnClickListener(v -> onAction.run());
            bar.addView(a);
        }
        col.addView(bar, new LinearLayout.LayoutParams(act.MATCH, act.dp(56)));
        View dv = new View(act); dv.setBackgroundColor(Ui.DIVIDER);
        col.addView(dv, new LinearLayout.LayoutParams(act.MATCH, 1));
        int wdp = Math.round(act.scrWpx() / Ui.density);
        if (wdp >= 840) {
            // tablets: a readable centered column instead of very long rows
            FrameLayout mid = new FrameLayout(act);
            mid.addView(body, new FrameLayout.LayoutParams(act.dp(720), act.MATCH, Gravity.CENTER_HORIZONTAL));
            col.addView(mid, new LinearLayout.LayoutParams(act.MATCH, 0, 1));
        } else col.addView(body, new LinearLayout.LayoutParams(act.MATCH, 0, 1));
        d.setContentView(col);
        Window w = d.getWindow();
        if (w != null) act.edgeToEdge(w, col, Ui.BG, !Ui.dark);
        d.show();
        return d;
    }

    void showHistory() { showItems(L.t("История"), act.store.history, true); }

    void showBookmarks() { showItems(L.t("Закладки"), act.store.bookmarks, false); }

    Runnable itemsRefresh;
    static final String UP_FOLDER = "\u0000up";

    void showItems(String title, ArrayList<Store.Item> items, boolean history) {
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        EditText q = new EditText(act);
        q.setSingleLine(true);
        q.setHint(history ? L.t("Поиск по истории") : L.t("Поиск по закладкам"));
        q.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        q.setTextColor(Ui.TEXT);
        q.setHintTextColor(Ui.TEXT2);
        q.setBackground(Ui.round(Ui.PILL, 22));
        q.setPaddingRelative(act.dp(18), 0, act.dp(18), 0);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(act.MATCH, act.dp(44));
        qlp.setMargins(act.dp(12), act.dp(10), act.dp(12), act.dp(4));
        body.addView(q, qlp);
        FrameLayout frame = new FrameLayout(act);
        body.addView(frame, new LinearLayout.LayoutParams(act.MATCH, 0, 1));
        android.widget.ListView lv = new android.widget.ListView(act);
        lv.setDivider(null);
        lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lv.setClipToPadding(false);
        lv.setPaddingRelative(0, 0, 0, act.dp(80));
        frame.addView(lv, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        TextView empty = Ui.text(act, "", 15, Ui.TEXT2);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPaddingRelative(act.dp(24), act.dp(64), act.dp(24), 0);
        frame.addView(empty, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        lv.setEmptyView(empty);
        // in-dialog undo bar (the activity snackbar would be hidden behind the full-screen dialog)
        LinearLayout undo = new LinearLayout(act);
        undo.setGravity(Gravity.CENTER_VERTICAL);
        undo.setPaddingRelative(act.dp(18), act.dp(4), act.dp(6), act.dp(4));
        undo.setMinimumHeight(act.dp(50));
        undo.setBackground(Ui.round(Ui.SNACK, 14));
        undo.setElevation(act.dp(8));
        TextView undoText = Ui.text(act, "", 14, Ui.dark ? 0xFF202124 : 0xFFF1F3F4);
        undo.addView(undoText, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        TextView undoBtn = Ui.medium(Ui.text(act, L.t("Вернуть"), 14, Ui.dark ? 0xFF1A73E8 : 0xFF8AB4F8));
        undoBtn.setPaddingRelative(act.dp(14), act.dp(12), act.dp(14), act.dp(12));
        undo.addView(undoBtn);
        undo.setVisibility(View.GONE);
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(act.MATCH, act.WRAP, Gravity.BOTTOM);
        ulp.setMargins(act.dp(12), 0, act.dp(12), act.dp(16));
        frame.addView(undo, ulp);
        final Runnable hideUndo = () -> undo.setVisibility(View.GONE);

        final Dialog[] dl = new Dialog[1];
        final String[] folder = {""};
        final ArrayList<MainActivity.ListRow> rows = new ArrayList<>();
        final java.text.DateFormat dayFmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG);
        final java.text.DateFormat timeFmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT);
        final Runnable[] fill = new Runnable[1];
        android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public boolean isEnabled(int i) { MainActivity.ListRow r = rows.get(i); return r.header == null; }
            @Override public View getView(int pos, View cv, android.view.ViewGroup parent) {
                MainActivity.ListRow row = rows.get(pos);
                if (row.header != null) {
                    TextView h = Ui.medium(Ui.text(act, row.header, 13, Ui.ACCENT));
                    h.setPaddingRelative(act.dp(20), act.dp(18), act.dp(20), act.dp(6));
                    return h;
                }
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(10), act.dp(8), act.dp(10));
                r.setMinimumHeight(act.dp(56));
                r.setBackground(Ui.ripple(act, false));
                if (row.folder != null) {
                    boolean up = UP_FOLDER.equals(row.folder);
                    ImageView fi = Ui.icon(act, up ? R.drawable.ic_back : R.drawable.ic_folder, Ui.TEXT2);
                    fi.setScaleType(ImageView.ScaleType.CENTER);
                    r.addView(fi, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
                    int n = 0;
                    if (!up) for (Store.Item b : items) if (row.folder.equals(b.f)) n++;
                    LinearLayout tx = new LinearLayout(act);
                    tx.setOrientation(LinearLayout.VERTICAL);
                    tx.setPaddingRelative(act.dp(16), 0, act.dp(8), 0);
                    tx.addView(Ui.single(act, up ? folder[0] : row.folder, 15, Ui.TEXT));
                    tx.addView(Ui.single(act, up ? L.t("Назад") : n + " " + L.t("закладок"), 13, Ui.TEXT2));
                    r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                    r.setOnClickListener(v -> { folder[0] = up ? "" : row.folder; fill[0].run(); lv.setSelection(0); });
                    return r;
                }
                final Store.Item it = row.it;
                r.addView(act.home.tileIcon(it.t, it.u, false, 0, 36), new LinearLayout.LayoutParams(act.dp(36), act.dp(36)));
                LinearLayout tx = new LinearLayout(act);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPaddingRelative(act.dp(16), 0, act.dp(8), 0);
                tx.addView(Ui.single(act, it.t == null || it.t.isEmpty() ? act.displayUrl(it.u) : it.t, 15, Ui.TEXT));
                String sub = act.displayUrl(it.u);
                if (history && it.d > 0) sub = timeFmt.format(new java.util.Date(it.d)) + " · " + sub;
                else if (!history && !q.getText().toString().trim().isEmpty() && it.f != null && !it.f.isEmpty()) sub = it.f + " · " + sub;
                tx.addView(Ui.single(act, sub, 13, Ui.TEXT2));
                r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                if (!history) {
                    ImageView ed = Ui.iconBtn(act, R.drawable.ic_edit, Ui.TEXT2);
                    ed.setOnClickListener(v -> editBookmark(it, fill[0]));
                    r.addView(ed, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
                }
                ImageView del = Ui.iconBtn(act, R.drawable.ic_close, Ui.TEXT2);
                del.setContentDescription(L.t("Удалить"));
                del.setOnClickListener(v -> {
                    int idx = items.indexOf(it);
                    if (idx < 0) return;
                    items.remove(idx);
                    if (history) act.store.saveHistory(); else act.store.saveBookmarks();
                    fill[0].run();
                    undoText.setText(L.t("Удалено") + ": " + (it.t == null || it.t.isEmpty() ? act.displayUrl(it.u) : it.t));
                    undoBtn.setOnClickListener(x -> {
                        items.add(Math.min(idx, items.size()), it);
                        if (history) act.store.saveHistory(); else act.store.saveBookmarks();
                        hideUndo.run();
                        fill[0].run();
                    });
                    undo.setVisibility(View.VISIBLE);
                    act.ui.removeCallbacks(hideUndo);
                    act.ui.postDelayed(hideUndo, 5000);
                });
                r.addView(del, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
                r.setOnClickListener(v -> { dl[0].dismiss(); act.navigate(it.u); });
                r.setOnLongClickListener(v -> { act.linkMenu(it.u, it.t, null); return true; });
                return r;
            }
        };
        fill[0] = () -> {
            rows.clear();
            String f = q.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (history) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
                long today = c.getTimeInMillis(), yesterday = today - 86400000L;
                String last = null;
                for (Store.Item it : items) {
                    if (!f.isEmpty() && !matches(it, f)) continue;
                    String day = it.d <= 0 ? L.t("Ранее") : it.d >= today ? L.t("Сегодня") : it.d >= yesterday ? L.t("Вчера") : dayFmt.format(new java.util.Date(it.d));
                    if (!day.equals(last)) { rows.add(new MainActivity.ListRow(day, null, null)); last = day; }
                    rows.add(new MainActivity.ListRow(null, null, it));
                }
            } else if (!f.isEmpty()) {
                for (Store.Item it : items) if (matches(it, f) || (it.f != null && it.f.toLowerCase(java.util.Locale.ROOT).contains(f))) rows.add(new MainActivity.ListRow(null, null, it));
            } else if (folder[0].isEmpty()) {
                for (String name : act.store.folders()) rows.add(new MainActivity.ListRow(null, name, null));
                for (Store.Item it : items) if (it.f == null || it.f.isEmpty()) rows.add(new MainActivity.ListRow(null, null, it));
            } else {
                rows.add(new MainActivity.ListRow(null, UP_FOLDER, null));
                for (Store.Item it : items) if (folder[0].equals(it.f)) rows.add(new MainActivity.ListRow(null, null, it));
                if (rows.size() == 1) folder[0] = "";
                if (folder[0].isEmpty()) { fill[0].run(); return; }
            }
            empty.setText(!f.isEmpty() ? L.t("Ничего не найдено") : history ? L.t("История пуста") : L.t("Закладок пока нет.\nНажмите ☆ в меню, чтобы добавить страницу."));
            ad.notifyDataSetChanged();
        };
        lv.setAdapter(ad);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { fill[0].run(); }
        });
        fill[0].run();
        itemsRefresh = history ? null : fill[0];
        dl[0] = fullDialog(title, body, history ? L.t("Очистить") : L.t("Ещё"), () -> {
            if (history) act.dialog()
                    .setMessage(L.t("Очистить всю историю просмотров?"))
                    .setPositiveButton(L.t("Очистить"), (d, w) -> { act.store.history.clear(); act.store.saveHistory(); fill[0].run(); })
                    .setNegativeButton(L.t("Отмена"), null).show();
            else act.dialog()
                    .setItems(new String[]{L.t("Импорт закладок (HTML)"), L.t("Экспорт закладок (HTML)")}, (d, w) -> {
                        try {
                            if (w == 0) act.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/html", "text/plain", "application/octet-stream"}), REQ_BM_IMPORT);
                            else act.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("text/html").putExtra(Intent.EXTRA_TITLE, "lasur-bookmarks.html"), REQ_BM_EXPORT);
                        } catch (Exception e) { act.toast(L.t("Не удалось открыть выбор файла")); }
                    }).show();
        });
        dl[0].setOnDismissListener(d -> { act.ui.removeCallbacks(hideUndo); if (!history) itemsRefresh = null; });
    }

    static boolean matches(Store.Item it, String f) {
        return (it.t != null && it.t.toLowerCase(java.util.Locale.ROOT).contains(f)) || (it.u != null && it.u.toLowerCase(java.util.Locale.ROOT).contains(f));
    }

    void editBookmark(Store.Item it, Runnable after) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(act.dp(22), act.dp(8), act.dp(22), 0);
        EditText name = new EditText(act);
        name.setSingleLine(true);
        name.setHint(L.t("Название"));
        name.setText(it.t);
        box.addView(name);
        EditText url = new EditText(act);
        url.setSingleLine(true);
        url.setHint("URL");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(it.u);
        box.addView(url);
        android.widget.AutoCompleteTextView fold = new android.widget.AutoCompleteTextView(act);
        fold.setSingleLine(true);
        fold.setThreshold(0);
        fold.setHint(L.t("Папка (необязательно)"));
        fold.setText(it.f == null ? "" : it.f);
        fold.setAdapter(new android.widget.ArrayAdapter<>(act, android.R.layout.simple_dropdown_item_1line, act.store.folders()));
        fold.setOnFocusChangeListener((v, has) -> { if (has && fold.getAdapter() != null && fold.getAdapter().getCount() > 0) fold.showDropDown(); });
        box.addView(fold);
        act.dialog().setTitle(L.t("Изменить закладку")).setView(box)
                .setPositiveButton(L.t("Сохранить"), (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (!u.contains("://") && !u.startsWith("about:")) u = act.toUrl(u);
                    it.u = u;
                    String t = name.getText().toString().trim();
                    it.t = t.isEmpty() ? act.displayUrl(u) : t;
                    it.f = fold.getText().toString().trim();
                    act.store.saveBookmarks();
                    if (after != null) after.run();
                })
                .setNegativeButton(L.t("Отмена"), null).show();
    }

    static final int REQ_BM_IMPORT = 19, REQ_BM_EXPORT = 20;

    static String htmlEsc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Netscape bookmark file format, understood by Chrome, Firefox and most other browsers. */
    static String exportBookmarksHtml(ArrayList<Store.Item> items, ArrayList<String> folders) {
        StringBuilder b = new StringBuilder("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n");
        for (String f : folders) {
            b.append("    <DT><H3>").append(htmlEsc(f)).append("</H3>\n    <DL><p>\n");
            for (Store.Item it : items) if (f.equals(it.f)) appendBm(b, it, "        ");
            b.append("    </DL><p>\n");
        }
        for (Store.Item it : items) if (it.f == null || it.f.isEmpty()) appendBm(b, it, "    ");
        return b.append("</DL><p>\n").toString();
    }

    static void appendBm(StringBuilder b, Store.Item it, String ind) {
        b.append(ind).append("<DT><A HREF=\"").append(htmlEsc(it.u)).append("\" ADD_DATE=\"").append(Math.max(0, it.d / 1000)).append("\">")
                .append(htmlEsc(it.t)).append("</A>\n");
    }

    static String htmlUnesc(String s) {
        s = s.replaceAll("(?s)<[^>]*>", "");
        Matcher m = Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);").matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String e = m.group(1), r;
            switch (e) {
                case "amp": r = "&"; break; case "lt": r = "<"; break; case "gt": r = ">"; break;
                case "quot": r = "\""; break; case "apos": r = "'"; break; case "nbsp": r = " "; break;
                default:
                    try { r = new String(Character.toChars(e.startsWith("#x") || e.startsWith("#X") ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1)))); }
                    catch (Exception ex) { r = m.group(); }
            }
            m.appendReplacement(b, Matcher.quoteReplacement(r));
        }
        m.appendTail(b);
        return b.toString().trim();
    }

    /** Parses a Netscape bookmark file; nested folders are flattened to their innermost name. */
    static ArrayList<Store.Item> parseBookmarksHtml(String html) {
        ArrayList<Store.Item> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?is)<h3[^>]*>(.*?)</h3>|<a\\s[^>]*?href\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a>|<dl\\b|</dl>").matcher(html);
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        String pending = null;
        int depth = 0;
        while (m.find()) {
            String g = m.group();
            if (m.group(1) != null) pending = htmlUnesc(m.group(1));
            else if (m.group(2) != null) {
                String u = htmlUnesc(m.group(2));
                if (!u.startsWith("http://") && !u.startsWith("https://")) continue;
                String t = htmlUnesc(m.group(3));
                Store.Item it = new Store.Item(t.isEmpty() ? u : t, u, 0);
                Matcher d = Pattern.compile("(?i)add_date\\s*=\\s*\"(\\d+)\"").matcher(g);
                if (d.find()) try { it.d = Long.parseLong(d.group(1)) * 1000; } catch (NumberFormatException ignored) { }
                it.f = stack.isEmpty() ? "" : stack.peek();
                out.add(it);
            } else if (g.regionMatches(true, 0, "<dl", 0, 3)) {
                depth++;
                // the outermost list maps to the top level
                stack.push(pending != null && depth > 1 ? pending : stack.isEmpty() ? "" : stack.peek());
                pending = null;
            } else {
                depth--;
                if (!stack.isEmpty()) stack.pop();
            }
        }
        return out;
    }

    void onBookmarkFile(int req, Uri uri) {
        if (uri == null) return;
        if (req == REQ_BM_EXPORT) {
            final String html = exportBookmarksHtml(act.store.bookmarks, act.store.folders());
            act.BG.execute(() -> {
                try (java.io.OutputStream os = act.getContentResolver().openOutputStream(uri, "wt")) {
                    os.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    act.longToast(L.t("Закладки экспортированы"));
                } catch (Exception e) { Log.w(act.TAG, "bookmark export", e); act.longToast(L.t("Не удалось сохранить файл")); }
            });
            return;
        }
        act.BG.execute(() -> {
            try (InputStream is = act.getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 16 * 1024 * 1024) break; }
                ArrayList<Store.Item> got = parseBookmarksHtml(bo.toString("UTF-8"));
                act.ui.post(() -> {
                    HashSet<String> have = new HashSet<>();
                    for (Store.Item it : act.store.bookmarks) have.add(it.u);
                    int added = 0;
                    for (Store.Item it : got) if (have.add(it.u)) { act.store.bookmarks.add(it); added++; }
                    act.store.saveBookmarks();
                    if (itemsRefresh != null) itemsRefresh.run();
                    act.longToast(L.t("Импортировано закладок:") + " " + added);
                });
            } catch (Exception e) { Log.w(act.TAG, "bookmark import", e); act.longToast(L.t("Не удалось прочитать файл")); }
        });
    }
}
