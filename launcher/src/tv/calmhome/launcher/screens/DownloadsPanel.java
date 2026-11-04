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
import tv.calmhome.launcher.ui.Glyph;
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
        int[] ink = {0xE5106B54, 0xE5923F31, 0xE5176174};
        Glyph.Kind[] symbols = {Glyph.Kind.SPORTS, Glyph.Kind.CINEMA, Glyph.Kind.TORII};
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
            ImageView badge = new ImageView(c);
            badge.setImageDrawable(new Glyph(symbols[category.ordinal()], Theme.SOFT_WHITE));
            badge.setBackground(t.outlined(ink[category.ordinal()], 0xAADDEBF4, 1, 9));
            badge.setPadding(t.dp(6), t.dp(6), t.dp(6), t.dp(6));
            LinearLayout.LayoutParams badgeSize = new LinearLayout.LayoutParams(t.dp(34), t.dp(34));
            badgeSize.bottomMargin = t.dp(3);
            labels.addView(badge, badgeSize);
            labels.addView(text(category.title, 23, true));
            labels.addView(text(category.subtitle, Theme.T_LABEL, false));
            card.addView(labels, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM));
            TextView chevron = text("›", 23, true);
            chevron.setGravity(Gravity.CENTER);
            chevron.setBackground(t.glass(18, .88f));
            FrameLayout.LayoutParams arrow = new FrameLayout.LayoutParams(t.dp(27), t.dp(27), Gravity.BOTTOM | Gravity.END);
            arrow.rightMargin = t.dp(12);
            arrow.bottomMargin = t.dp(10);
            card.addView(chevron, arrow);
            card.setContentDescription(category.title + ", browse sources");
            w.focusable(card, Theme.R_PANEL, null, true);
            card.setFocusableInTouchMode(true);
            card.setOnClickListener(v -> host.open(new SourceCategoryPanel(host, category)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(t.screenH() * .29f), 1);
            if (category.ordinal() > 0) lp.leftMargin = t.dp(9);
            cards.addView(card, lp);
            if (first == null) first = card;
        }
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(t.glass(Theme.R_PANEL, .88f));
        bar.setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10));
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
        step(steps, "1", "Choose source", "Select an app or code");
        step(steps, "2", "Check APK", "Review file and app details");
        step(steps, "3", "Approve install", "Confirm with Android");
        steps.addView(action("Manage Downloads", () -> host.open(new DownloadLibraryPanel(host))));
        body.addView(steps, spaced(10));
        return screen("Download Center");
    }

    private void step(LinearLayout steps, String number, String heading, String summary) {
        LinearLayout group = new LinearLayout(c);
        group.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = text(number, Theme.T_BODY, true);
        n.setGravity(Gravity.CENTER);
        n.setBackground(t.glass(18, .8f));
        group.addView(n, new LinearLayout.LayoutParams(t.dp(25), t.dp(25)));
        LinearLayout lines = new LinearLayout(c);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setPadding(t.dp(6), 0, t.dp(4), 0);
        lines.addView(text(heading, Theme.T_NAV, true));
        lines.addView(text(summary, Theme.T_CAPTION, false));
        group.addView(lines);
        steps.addView(group, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
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
