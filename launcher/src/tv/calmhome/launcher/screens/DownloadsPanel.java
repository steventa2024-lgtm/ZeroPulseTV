package tv.calmhome.launcher.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import tv.calmhome.launcher.R;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.downloads.DownloadPolicy;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** Native Download Center. Category artwork is local and independent of source availability. */
public final class DownloadsPanel extends DownloadPage {
    private static final String EXTERNAL_DOWNLOADER = "com.esaba.downloader";
    private EditText input;
    private TextView message;
    public DownloadsPanel(Host host) { super(host); }
    public static Panel detail(Host host, long id) { return new DownloadDetailPanel(host, id); }

    @Override protected View build() {
        body.addView(text("Choose an app category or enter a link from its developer.", Theme.T_BODY, false));
        LinearLayout cards = new LinearLayout(c);
        cards.setClipChildren(false);
        body.addView(cards, spaced(12));
        int[] pictures = {R.drawable.category_sports, R.drawable.category_movies, R.drawable.category_anime};
        for (SourceEntry.Category category : SourceEntry.Category.values()) {
            FrameLayout card = new FrameLayout(c);
            card.setBackground(t.glass(Theme.R_PANEL, .94f));
            ImageView art = new ImageView(c);
            art.setImageResource(pictures[category.ordinal()]);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
            View shade = new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x13090F17, 0xF214202E}));
            card.addView(shade, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
            LinearLayout labels = new LinearLayout(c);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(t.dp(14), 0, t.dp(12), t.dp(10));
            labels.addView(text(category.title, 23, true));
            labels.addView(text(category.subtitle + "   ›", Theme.T_LABEL, false));
            card.addView(labels, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM));
            card.setContentDescription(category.title + ", browse sources");
            w.focusable(card, Theme.R_PANEL, null, true);
            card.setOnClickListener(v -> host.open(new SourceCategoryPanel(host, category)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(t.screenH() * .26f), 1);
            if (category.ordinal() > 0) lp.leftMargin = t.dp(9);
            cards.addView(card, lp);
            if (first == null) first = card;
        }
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(t.glass(Theme.R_PANEL, .88f));
        bar.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
        input = field("Enter a code or HTTPS link", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 8192);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setOnEditorActionListener((v, id, event) -> { if (id == EditorInfo.IME_ACTION_GO) { go(); return true; } return false; });
        bar.addView(input, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
        TextView go = action("Go   ›", this::go);
        LinearLayout.LayoutParams gl = w.wrap();
        gl.leftMargin = t.dp(9);
        bar.addView(go, gl);
        body.addView(bar, spaced(12));
        message = text("", Theme.T_LABEL, false);
        message.setVisibility(View.GONE);
        body.addView(message);
        LinearLayout steps = new LinearLayout(c);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        steps.setPadding(t.dp(14), t.dp(6), t.dp(8), t.dp(6));
        steps.setBackground(t.glass(Theme.R_PANEL, .79f));
        steps.addView(text("1  Choose source     ›     2  Check APK     ›     3  Approve in Android", Theme.T_LABEL, true),
                new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
        steps.addView(action("Manage Downloads", () -> host.open(new DownloadLibraryPanel(host))));
        body.addView(steps, spaced(10));
        return screen("Download Center");
    }

    private void go() {
        String value = input.getText().toString().trim();
        message.setVisibility(View.GONE);
        try {
            if (SourceEntry.isCode(value)) {
                SourceEntry entry = SourceEntry.byCode(new SourceCatalog(c).all(), value);
                if (entry != null) host.open(new SourceReviewPanel(host, entry.url, entry.sha256, entry.label));
                else external(value);
            } else {
                String url = DownloadPolicy.validateUrl(value).toASCIIString();
                host.open(new SourceReviewPanel(host, url, "", "Direct HTTPS link"));
            }
        } catch (IllegalArgumentException e) {
            message.setVisibility(View.VISIBLE);
            showError(message, e);
            input.requestFocus();
        }
    }

    private void external(String code) {
        boolean installed = host.apps().usable(EXTERNAL_DOWNLOADER);
        MenuPanel menu = new MenuPanel(host, "Unmapped code", "No ZeroPulse source uses " + code +
                ". AFTVnews codes can be entered in its Downloader app." + (installed ? "" : " Downloader is unavailable on this TV."));
        Runnable copy = () -> {
            ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Downloader code", code));
        };
        if (installed) menu.item("Copy code and open Downloader", () -> {
            copy.run();
            if (!host.apps().launch(EXTERNAL_DOWNLOADER)) host.toast("Code copied. Downloader could not open.");
        });
        menu.item("Copy code", () -> { copy.run(); host.toast("Code copied"); });
        menu.item("Back", null);
        host.open(menu);
    }
}
