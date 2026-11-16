package tv.calmhome.launcher.streaming;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.ModuleActivity;
import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.HttpsFetch;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * ZeroPulse Streaming: Live TV, Movies, TV Shows and Sports on a side rail, plus Sources. Only sections the
 * user switched on are listed. Every destination shows real content from the user's sources or an honest
 * state; nothing is bundled or invented. Network work runs on one background thread that stops with the screen.
 */
public class StreamingActivity extends ModuleActivity {

    static final String SOURCES = "Sources";
    static final String GUIDE = "GUIDE";
    static final int PICK_FILE = 41, PICK_GUIDE = 42;

    private final List<View> railViews = new ArrayList<>();
    private final List<String> railKeys = new ArrayList<>();
    private ExecutorService worker;
    private StreamingStore store;
    private SourceImporter importer;
    private Logos logos;
    private LiveTvView liveTv;
    private GuideLink guideLink;
    private GuideView guideView;
    private SportsView sportsView;
    private LibraryView libraryView;
    private Logos posters;
    private JellyfinAccount jellyfin;
    /** Test hook: the Jellyfin connection to use (the real one uses the Android Keystore). */
    static JellyfinAccount.Vault vaultForTests;
    static JellyfinApi.Transport transportForTests;
    private FrameLayout content;
    private int selected;
    private String notice;
    private boolean busy;
    private long confirmRemove = -1;

    @Override
    protected Features.Module module() {
        return Features.Module.STREAMING;
    }

    @Override
    protected void onModuleCreate(Bundle state) {
        store = new StreamingStore(this);
        importer = new SourceImporter(store, getContentResolver(), HttpsFetch.DEFAULT);
        worker = Executors.newSingleThreadExecutor();
        logos = new Logos(HttpsFetch.DEFAULT, theme.dp(44));
        posters = new Logos(HttpsFetch.DEFAULT, theme.dp(150));
        jellyfin = vaultForTests != null ? new JellyfinAccount(this, vaultForTests) : new JellyfinAccount(this);
        liveTv = new LiveTvView(this, theme, w, store, logos, new LiveTvView.Host() {
            @Override public void watch(StreamingStore.Channel ch) {
                startActivity(new Intent(StreamingActivity.this, PlayerActivity.class)
                        .putExtra(PlayerActivity.EXTRA_CHANNEL_ID, ch.id));
            }

            @Override public String nowPlaying(StreamingStore.Channel ch) {
                return guideLink != null ? guideLink.nowLine(ch, System.currentTimeMillis()) : null;
            }
        });

        LinearLayout body = new LinearLayout(this);
        body.setClipChildren(false);
        body.setPadding(0, 0, 0, theme.safeY());

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(theme.dp(10), theme.dp(12), theme.dp(10), theme.dp(12));
        rail.setBackground(theme.glass(Theme.R_PANEL, 0.85f));
        for (Features.Section s : Features.sections(prefs)) {
            addRail(rail, icon(s), s.label, s.name());
            if (s == Features.Section.LIVE_TV) addRail(rail, Glyph.Kind.GRID, "Guide", GUIDE);
        }
        addRail(rail, Glyph.Kind.GEAR, SOURCES, SOURCES);
        body.addView(rail, new LinearLayout.LayoutParams(theme.dp(230), Widgets.WRAP));

        content = new FrameLayout(this);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f);
        cl.leftMargin = theme.dp(20);
        body.addView(content, cl);

        setContentView(w.screen(Features.Module.STREAMING.title, body, 1f));
        show(0);
        railViews.get(0).requestFocus();
        // The page changes only when focus moves along the rail. Focus arriving on the rail from the page, or from a
        // view that was just removed (Android then picks the first focusable view), returns to this page's rail
        // item instead, so rebuilding a page or closing a details card never switches pages.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((old, now) -> {
            // Up/Down on the remote moves one rail item at a time, always from a focused view ("old"). Any other
            // arrival on the rail keeps the current page: from the page, from a removed view, or after a gap in
            // focus ("old" null), when Android gives focus to the first focusable view (device bug: a remembered
            // rail item across that gap made the Guide's day chips switch to Live TV).
            int i = railViews.indexOf(now), f = railViews.indexOf(old);
            if (i >= 0 && i != selected) {
                if (f >= 0 && Math.abs(i - f) == 1) show(i);
                else railViews.get(selected).requestFocus();
            }
            paint();
        });
    }

    private void addRail(LinearLayout rail, Glyph.Kind icon, String label, String key) {
        LinearLayout item = w.railItem(icon, label);
        final int index = railViews.size();
        item.setOnClickListener(v -> show(index));          // OK on the remote, or a mouse click
        railViews.add(item);
        railKeys.add(key);
        rail.addView(item, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
    }

    private void paint() {
        for (int i = 0; i < railViews.size(); i++) w.paintRail(railViews.get(i), i == selected);
    }

    private void show(int i) {
        selected = i;
        render();
    }

    private void render() {
        if (content == null) return;
        // Guide matching is rebuilt with each page, so it always reflects the current sources and guides.
        guideLink = new GuideLink(store, store.channels());
        // Focus inside the old page moves to this page's rail item first, so no focused view is removed and Android
        // never has to pick a fallback (device bug: Back from the player, after Watch in the Guide, opened Live TV).
        View focus = getCurrentFocus();
        if (focus != null && isInside(focus, content)) railViews.get(selected).requestFocus();
        guideView = null;
        sportsView = null;
        libraryView = null;
        content.removeAllViews();
        content.addView(page(railKeys.get(selected)), new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        paint();
    }

    private static boolean isInside(View v, View group) {
        for (View f = v; f != null; f = f.getParent() instanceof View ? (View) f.getParent() : null) if (f == group) return true;
        return false;
    }

    // ------------------------------------------------------------------ pages

    private View page(String key) {
        if (SOURCES.equals(key)) return sourcesPage();
        if (GUIDE.equals(key)) {
            guideView = new GuideView(this, theme, w, guideLink, store.channels(), this::watch);
            return guideView.build();
        }
        switch (Features.Section.valueOf(key)) {
            case LIVE_TV: return liveTvPage();
            case MOVIES:
            case SHOWS:
                libraryView = new LibraryView(this, theme, w, store, posters, worker, jellyfin, jellyfin.api(transport()),
                        Features.Section.valueOf(key));
                return libraryView.build();
            default:
                sportsView = new SportsView(this, theme, w, guideLink, this::watch);
                return sportsView.build();
        }
    }

    private void watch(StreamingStore.Channel ch) {
        if (!Features.mayOpen(prefs, Features.Section.LIVE_TV)) {
            android.widget.Toast.makeText(this, "Live TV is turned off in Settings → Features", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, ch.id));
    }

    private View liveTvPage() {
        if (store.channels().isEmpty()) {
            return w.stateCard(Glyph.Kind.TV, Theme.HOLO_BLUE, "No channels yet",
                    "ZeroPulse doesn't come with a channel list. Add a playlist you're allowed to use (an HTTPS M3U "
                            + "link or a file on this TV) under Sources, and its HTTPS streams appear here.");
        }
        return liveTv.build();
    }

    private View sourcesPage() {
        LinearLayout list = column();
        list.addView(heading("Add a playlist you're allowed to use. HTTPS only: HTTP links and HTTP streams inside "
                + "playlists are left out in this beta."));

        final EditText link = field("https://… playlist (.m3u / .m3u8)", "Playlist link", this::addLink);
        list.addView(link, fieldParams());
        list.addView(row("Add this link", null, v -> addLink(link.getText().toString())));

        final Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/x-mpegurl", "audio/mpegurl",
                        "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain", "application/octet-stream"});
        if (tv.calmhome.launcher.data.Apps.hasRealHandler(this, pick)) {
            list.addView(row("Choose a playlist file on this TV", null, v -> startActivityForResult(pick, PICK_FILE)));
        } else {
            list.addView(note("This TV has no file picker, so playlist files can't be added here. Use an HTTPS link."));
        }

        if (busy) list.addView(note("Working…"));
        if (notice != null) list.addView(note(notice));

        List<StreamingStore.Source> sources = store.sources();
        final GuideData guides = new GuideData(store);
        List<GuideData.GuideSource> guideSources = guides.sources();
        java.util.Set<String> guideLocations = new java.util.HashSet<>();
        for (GuideData.GuideSource g : guideSources) guideLocations.add(g.location);
        list.addView(heading(sources.isEmpty() ? "No sources yet." : "Your sources"));
        for (final StreamingStore.Source s : sources) {
            String when = s.refreshed > 0 ? "updated " + DateUtils.getRelativeTimeSpanString(s.refreshed) : "never read";
            list.addView(row(s.title, (s.status != null ? s.status : s.channels + " streams") + " · " + when, null));
            list.addView(row("   Refresh " + s.title, null, v -> run(() -> importer.refresh(s))));
            list.addView(row(confirmRemove == s.id ? "   Press again to remove " + s.title : "   Remove " + s.title,
                    "Removes its streams from Live TV; favourites and progress stay", v -> {
                        if (confirmRemove == s.id) {
                            store.removeSource(s.id);
                            confirmRemove = -1;
                            notice = s.title + " removed.";
                        } else {
                            confirmRemove = s.id;
                        }
                        render();
                        refocus(confirmRemove == s.id ? "   Press again to remove " + s.title : "Add this link");
                    }));
            if (s.guideUrl != null && !guideLocations.contains(s.guideUrl)) {
                final String g = s.guideUrl;
                list.addView(row("   Add the guide this playlist names", SourceImporter.host(g) + " · XMLTV, may be large",
                        v -> run(() -> importer.addGuideUrl(g))));
            }
        }

        jellyfinSection(list);

        list.addView(heading("Programme guide (XMLTV): used by Guide, the Now line on Live TV and Sports. HTTPS only; "
                + "large guides can take a while to read."));
        final EditText guideField = field("https://… guide (.xml / .xml.gz)", "Guide link", this::addGuide);
        list.addView(guideField, fieldParams());
        list.addView(row("Add this guide", null, v -> addGuide(guideField.getText().toString())));
        final Intent pickGuide = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        if (tv.calmhome.launcher.data.Apps.hasRealHandler(this, pickGuide)) {
            list.addView(row("Choose a guide file on this TV", null, v -> startActivityForResult(pickGuide, PICK_GUIDE)));
        }
        for (final GuideData.GuideSource g : guideSources) {
            String when = g.refreshed > 0 ? "updated " + DateUtils.getRelativeTimeSpanString(g.refreshed) : "never read";
            list.addView(row(g.title, (g.status != null ? g.status : "") + " · " + when, null));
            list.addView(row("   Refresh guide " + g.title, null, v -> run(() -> importer.refreshGuide(g))));
            final long key = -g.id - 1;
            list.addView(row(confirmRemove == key ? "   Press again to remove guide " + g.title : "   Remove guide " + g.title,
                    null, v -> {
                        if (confirmRemove == key) {
                            guides.removeSource(g.id);
                            confirmRemove = -1;
                            notice = "Guide " + g.title + " removed.";
                        } else {
                            confirmRemove = key;
                        }
                        render();
                        refocus(confirmRemove == key ? "   Press again to remove guide " + g.title : "Add this guide");
                    }));
        }
        return scroll(list);
    }

    private JellyfinApi.Transport transport() {
        return transportForTests != null ? transportForTests : JellyfinApi.HTTPS;
    }

    /** Optional Jellyfin server for Movies and TV Shows: connect (HTTPS only) or disconnect and remove. */
    private void jellyfinSection(LinearLayout list) {
        list.addView(heading("Your Jellyfin server (optional) for Movies and TV Shows. HTTPS with a certificate the TV "
                + "trusts; HTTP-only and self-signed servers can't be used in this beta. Your password isn't stored."));
        if (jellyfin.connected()) {
            list.addView(row("Jellyfin: " + jellyfin.serverName(), "Signed in as " + jellyfin.userName(), null));
            final long key = Long.MIN_VALUE;
            list.addView(row(confirmRemove == key ? "   Press again to sign out and remove the server" : "   Sign out and remove the server",
                    "Favourites and progress saved on this TV stay", v -> {
                        if (confirmRemove != key) {
                            confirmRemove = key;
                            render();
                            refocus("   Press again to sign out and remove the server");
                            return;
                        }
                        confirmRemove = -1;
                        final JellyfinApi api = jellyfin.api(transport());
                        run(() -> {
                            try {
                                if (api != null) api.logout();
                            } catch (java.io.IOException ignored) {
                                // Removing locally still works when the server can't be reached.
                            }
                            jellyfin.forget();
                            return new SourceImporter.Outcome(true, "Jellyfin server removed.");
                        });
                    }));
            return;
        }
        final EditText server = field("https://… your Jellyfin server", "Jellyfin server", v -> { });
        final EditText user = field("User name", "Jellyfin user", v -> { });
        user.setInputType(InputType.TYPE_CLASS_TEXT);
        final EditText password = field("Password", "Jellyfin password", v -> { });
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        list.addView(server, fieldParams());
        list.addView(user, fieldParams());
        list.addView(password, fieldParams());
        list.addView(row("Connect to Jellyfin", null, v -> {
            final String typed = server.getText().toString(), u = user.getText().toString(), pw = password.getText().toString();
            password.setText("");
            run(() -> connectJellyfin(typed, u, pw));
        }));
    }

    private SourceImporter.Outcome connectJellyfin(String typed, String user, String password) {
        if (JellyfinApi.isHttp(typed)) {
            return new SourceImporter.Outcome(false, "That's an HTTP address. This beta connects to Jellyfin over HTTPS only.");
        }
        String server = JellyfinApi.normalise(typed);
        if (server == null) return new SourceImporter.Outcome(false, "That isn't a valid server address.");
        if (user.trim().isEmpty()) return new SourceImporter.Outcome(false, "Enter your Jellyfin user name.");
        JellyfinApi api = new JellyfinApi(server, jellyfin.deviceId(), android.os.Build.MODEL, transport());
        try {
            String name = api.serverName();
            JellyfinApi.Login login = api.login(user.trim(), password);
            jellyfin.save(server, name, login);
            return new SourceImporter.Outcome(true, "Connected to " + name + " as " + login.userName + ".");
        } catch (java.io.IOException e) {
            return new SourceImporter.Outcome(false, e.getMessage());
        } catch (java.security.GeneralSecurityException e) {
            return new SourceImporter.Outcome(false, "This TV couldn't store the sign-in securely, so it wasn't kept.");
        }
    }

    private EditText field(String hint, String description, final java.util.function.Consumer<String> onDone) {
        final EditText e = new EditText(this);
        theme.style(e, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        e.setHint(hint);
        e.setHintTextColor(Theme.MUTED_GRAY);
        e.setContentDescription(description);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setPadding(theme.dp(16), theme.dp(10), theme.dp(16), theme.dp(10));
        e.setBackground(theme.outlined(Theme.PANEL_NAVY, Theme.BORDER, 1, Theme.R_PILL));
        e.setOnFocusChangeListener((v, has) -> v.setBackground(theme.outlined(Theme.PANEL_NAVY,
                has ? Theme.ELECTRIC_BLUE : Theme.BORDER, has ? 2 : 1, Theme.R_PILL)));
        e.setOnEditorActionListener((v, id, ev) -> {
            onDone.accept(e.getText().toString());
            return true;
        });
        return e;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        ll.topMargin = theme.dp(10);
        return ll;
    }

    // ------------------------------------------------------------------ actions

    private void addLink(String url) {
        if (url.trim().isEmpty()) return;
        run(() -> importer.addUrl(url));
    }

    private void addGuide(String url) {
        if (url.trim().isEmpty()) return;
        run(() -> importer.addGuideUrl(url));
    }

    /** Back closes a details card in Guide or Sports before leaving the screen. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (guideView != null && guideView.onBack()) return;
        if (sportsView != null && sportsView.onBack()) return;
        if (libraryView != null && libraryView.onBack()) return;
        super.onBackPressed();
    }

    private void run(final java.util.concurrent.Callable<SourceImporter.Outcome> job) {
        if (busy) return;
        busy = true;
        notice = null;
        render();
        worker.execute(() -> {
            SourceImporter.Outcome o;
            try {
                o = job.call();
            } catch (Exception e) {
                o = null;
            }
            final String msg = o != null ? o.message : "Something went wrong reading that source.";
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false;
                notice = msg;
                render();
                refocus("Add this link");
            });
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request != PICK_FILE && request != PICK_GUIDE) || result != RESULT_OK || data == null
                || data.getData() == null) return;
        final Uri uri = data.getData();
        if (request == PICK_GUIDE) {
            run(() -> importer.addGuideFile(uri));
            return;
        }
        run(() -> importer.addFile(uri));
    }

    // ------------------------------------------------------------------ small views

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(theme.dp(4), theme.dp(4), theme.dp(4), theme.dp(24));
        return l;
    }

    private View scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.setClipToPadding(false);
        s.addView(v);
        return s;
    }

    private TextView heading(String text) {
        TextView t = theme.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        t.setText(text);
        t.setPadding(theme.dp(14), theme.dp(12), theme.dp(14), theme.dp(4));
        return t;
    }

    private TextView note(String text) {
        TextView t = heading(text);
        t.setTextColor(Theme.PULSE_CYAN);
        return t;
    }

    /** A focusable row (or a plain line when {@code click} is null). */
    private View row(String label, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(theme.dp(14), theme.dp(10), theme.dp(14), theme.dp(10));
        TextView a = theme.text(this, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        a.setText(label);
        r.addView(a);
        if (sub != null && !sub.isEmpty()) {
            TextView b = theme.text(this, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            b.setText(sub);
            r.addView(b);
        }
        r.setContentDescription(label);
        if (click != null) {
            r.setFocusable(true);
            r.setClickable(true);
            r.setOnClickListener(click);
            r.setOnFocusChangeListener((v, has) -> v.setBackground(has
                    ? theme.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.3f), Theme.ELECTRIC_BLUE, 1.5f, 10) : null));
        }
        return r;
    }

    private void refocus(String description) {
        ArrayList<View> found = new ArrayList<>();
        content.findViewsWithText(found, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        if (!found.isEmpty()) found.get(0).requestFocus();
    }

    private static Glyph.Kind icon(Features.Section s) {
        switch (s) {
            case LIVE_TV: return Glyph.Kind.TV;
            case MOVIES: return Glyph.Kind.PLAY;
            case SHOWS: return Glyph.Kind.GRID;
            default: return Glyph.Kind.INFO;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing() && content != null) render();   // Live TV reflects sources changed elsewhere
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (logos != null) logos.trim();
        if (posters != null) posters.trim();
    }

    @Override
    protected void onDestroy() {
        if (logos != null) logos.close();
        if (posters != null) posters.close();
        if (worker != null) worker.shutdownNow();
        if (store != null) store.close();
        super.onDestroy();
    }
}
