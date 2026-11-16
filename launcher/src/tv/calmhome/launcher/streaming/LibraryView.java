package tv.calmhome.launcher.streaming;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Movies or TV Shows from the user's own Jellyfin server: poster grid with search, Continue watching and
 * Favourites, details with Resume/Play, and for series the seasons, episodes and next episode. Nothing is bundled;
 * without a server the page says how to connect one.
 */
final class LibraryView {

    static final String ALL = "All", CONTINUE = "Continue watching", FAVOURITES = "Favourites";
    static final int PAGE = 500;

    private final Activity a;
    private final Theme t;
    private final Widgets w;
    private final StreamingStore store;
    private final Logos posters;
    private final ExecutorService worker;
    private final JellyfinAccount account;
    private final JellyfinApi api;
    private final Features.Section section;
    private final String type;          // "Movie" or "Series"

    private final List<JellyfinApi.Item> loaded = new ArrayList<>();
    private List<JellyfinApi.Item> shown = new ArrayList<>();
    private String filter = ALL, query = "";
    private FrameLayout root;
    private LinearLayout chips;
    private TextView status;
    private GridView grid;
    private View details;
    private final Adapter adapter = new Adapter();
    private int generation;

    LibraryView(Activity a, Theme t, Widgets w, StreamingStore store, Logos posters, ExecutorService worker,
                JellyfinAccount account, JellyfinApi api, Features.Section section) {
        this.a = a;
        this.t = t;
        this.w = w;
        this.store = store;
        this.posters = posters;
        this.worker = worker;
        this.account = account;
        this.api = api;
        this.section = section;
        this.type = section == Features.Section.MOVIES ? "Movie" : "Series";
    }

    View build() {
        if (api == null) {
            boolean movies = section == Features.Section.MOVIES;
            return w.stateCard(movies ? Glyph.Kind.PLAY : Glyph.Kind.GRID, Theme.HOLO_BLUE,
                    movies ? "No movie library connected" : "No show library connected",
                    (movies ? "Movies" : "Series, seasons and episodes") + " come from your own Jellyfin server (HTTPS). "
                            + "Connect it under Sources. ZeroPulse doesn't include any films or shows, and doesn't use "
                            + "unofficial catalogues.");
        }
        root = new FrameLayout(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(a);
        t.style(search, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        search.setHint(section == Features.Section.MOVIES ? "Search movies" : "Search shows");
        search.setContentDescription(search.getHint());
        search.setHintTextColor(Theme.MUTED_GRAY);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setPadding(t.dp(16), t.dp(9), t.dp(16), t.dp(9));
        search.setText(query);
        search.setBackground(t.outlined(Theme.PANEL_NAVY, Theme.BORDER, 1, Theme.R_PILL));
        search.setOnFocusChangeListener((v, has) -> v.setBackground(t.outlined(Theme.PANEL_NAVY,
                has ? Theme.ELECTRIC_BLUE : Theme.BORDER, has ? 2 : 1, Theme.R_PILL)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) { }
            @Override public void afterTextChanged(Editable e) {
                query = e.toString();
                apply();
            }
        });
        col.addView(search, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        HorizontalScrollView strip = new HorizontalScrollView(a);
        strip.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(a);
        chips.setPadding(0, t.dp(8), 0, t.dp(4));
        strip.addView(chips);
        col.addView(strip, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        status = t.text(a, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        status.setPadding(t.dp(4), t.dp(2), 0, t.dp(6));
        col.addView(status);
        grid = new GridView(a);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(t.dp(Theme.GAP));
        grid.setVerticalSpacing(t.dp(Theme.GAP));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setPadding(t.dp(4), t.dp(4), t.dp(4), t.dp(24));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> open(shown.get(pos)));
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { adapter.notifyDataSetChanged(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { adapter.notifyDataSetChanged(); }
        });
        grid.setOnFocusChangeListener((v, has) -> adapter.notifyDataSetChanged());
        col.addView(grid, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        chipsFor();
        load();
        return root;
    }

    private void chipsFor() {
        List<View> row = new ArrayList<>();
        for (final String f : new String[]{ALL, CONTINUE, FAVOURITES}) {
            row.add(w.navItem(f, f.equals(filter), v -> {
                filter = f;
                chipsFor();
                load();
            }));
        }
        Widgets.replaceChips(chips, row);
    }

    /** Loads the current filter from the server on the worker thread; late answers for an old filter are dropped. */
    private void load() {
        final int gen = ++generation;
        final String f = filter;
        status.setText("Loading from " + account.serverName() + "…");
        worker.execute(() -> {
            List<JellyfinApi.Item> got = null;
            String error = null;
            boolean signedOut = false;
            try {
                got = CONTINUE.equals(f) ? (type.equals("Movie") ? api.resume("Movie", 100) : seriesOfResumedEpisodes())
                        : api.library(type, null, 0, PAGE);
            } catch (JellyfinApi.JellyfinException e) {
                error = e.getMessage();
                signedOut = e.signedOut;
            } catch (IOException | RuntimeException e) {
                error = "The server couldn't be reached.";
            }
            final List<JellyfinApi.Item> result = got;
            final String err = error;
            final boolean out = signedOut;
            a.runOnUiThread(() -> {
                if (a.isDestroyed() || gen != generation) return;
                loaded.clear();
                if (result != null) loaded.addAll(result);
                apply();
                if (err != null) status.setText(out ? err : err + " Press OK on a filter to try again.");
            });
        });
    }

    /** Continue watching for shows: the series of episodes in progress (one entry per series). */
    private List<JellyfinApi.Item> seriesOfResumedEpisodes() throws IOException {
        List<JellyfinApi.Item> eps = api.resume("Episode", 100), out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<JellyfinApi.Item> all = api.library("Series", null, 0, PAGE);
        for (JellyfinApi.Item e : eps) {
            if (e.seriesId == null || !seen.add(e.seriesId)) continue;
            for (JellyfinApi.Item s : all) if (e.seriesId.equals(s.id)) out.add(s);
        }
        return out;
    }

    private void apply() {
        String q = tv.calmhome.launcher.core.ChannelFilter.fold(query);
        shown = new ArrayList<>();
        for (JellyfinApi.Item it : loaded) {
            if (FAVOURITES.equals(filter) && !store.isFavourite(JellyfinApi.key(account.serverId(), it))) continue;
            if (!q.isEmpty() && !tv.calmhome.launcher.core.ChannelFilter.fold(it.name).contains(q)) continue;
            shown.add(it);
        }
        status.setText(shown.size() + (shown.size() == 1 ? " title" : " titles") + " · " + account.serverName()
                + (loaded.size() >= PAGE ? " · first " + PAGE + " shown, use search" : "") + " · OK: details");
        adapter.notifyDataSetChanged();
    }

    List<JellyfinApi.Item> shown() {
        return shown;
    }

    // ------------------------------------------------------------------ details

    boolean onBack() {
        if (details == null) return false;
        root.removeView(details);
        details = null;
        grid.requestFocus();
        return true;
    }

    void open(final JellyfinApi.Item it) {
        LinearLayout card = w.stateCard(Glyph.Kind.PLAY, Theme.HOLO_BLUE, it.name, describe(it));
        final String key = JellyfinApi.key(account.serverId(), it);
        if (type.equals("Movie")) {
            long local = store.progress(key), at = local > 0 ? local : it.positionMs;
            if (at > 5_000) card.addView(button("Resume from " + clock(at), v -> playList(java.util.Collections.singletonList(it), 0, at)));
            card.addView(button(at > 5_000 ? "Play from the start" : "Play", v -> {
                store.saveProgress(key, 0, Math.max(1, it.runtimeMs));
                playList(java.util.Collections.singletonList(it), 0, 0);
            }));
        } else {
            final LinearLayout seasons = new LinearLayout(a);
            seasons.setOrientation(LinearLayout.VERTICAL);
            TextView loading = t.text(a, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            loading.setText("Loading seasons…");
            seasons.addView(loading);
            card.addView(seasons);
            loadSeasons(it, seasons);
        }
        boolean fav = store.isFavourite(key);
        card.addView(button(fav ? "Remove from Favourites" : "Add to Favourites", v -> {
            store.setFavourite(key, !store.isFavourite(key));
            Toast.makeText(a, it.name + (store.isFavourite(key) ? " added to Favourites" : " removed from Favourites"),
                    Toast.LENGTH_SHORT).show();
            onBack();
            apply();
        }));
        card.addView(button("Close", v -> onBack()));
        ScrollView sc = new ScrollView(a);
        sc.addView(card);
        FrameLayout holder = new FrameLayout(a);
        holder.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.94f));
        holder.setClickable(true);
        holder.addView(sc, new FrameLayout.LayoutParams(t.dp(680), Widgets.WRAP, Gravity.CENTER));
        details = holder;
        root.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        for (int i = 0; i < card.getChildCount(); i++) if (card.getChildAt(i).isFocusable()) { card.getChildAt(i).requestFocus(); break; }
    }

    private void loadSeasons(final JellyfinApi.Item series, final LinearLayout into) {
        worker.execute(() -> {
            List<JellyfinApi.Item> seasons = null, next = null;
            String err = null;
            try {
                seasons = api.seasons(series.id);
                next = api.nextUp(series.id);
            } catch (IOException | RuntimeException e) {
                err = e instanceof JellyfinApi.JellyfinException ? e.getMessage() : "The server couldn't be reached.";
            }
            final List<JellyfinApi.Item> s = seasons, n = next;
            final String error = err;
            a.runOnUiThread(() -> {
                if (a.isDestroyed() || details == null) return;
                into.removeAllViews();
                if (error != null) {
                    into.addView(line(error, Theme.ERROR));
                    return;
                }
                if (n != null && !n.isEmpty()) {
                    final JellyfinApi.Item e = n.get(0);
                    into.addView(button("Next up: " + episodeLabel(e), v -> playEpisodesFrom(series, e)));
                }
                if (s.isEmpty()) into.addView(line("This series has no seasons on the server.", Theme.MUTED_GRAY));
                for (final JellyfinApi.Item season : s) {
                    into.addView(button(season.name + (season.played ? "  ✓" : ""), v -> showEpisodes(series, season)));
                }
            });
        });
    }

    private void showEpisodes(final JellyfinApi.Item series, final JellyfinApi.Item season) {
        worker.execute(() -> {
            List<JellyfinApi.Item> eps = null;
            String err = null;
            try {
                eps = api.episodes(series.id, season.id);
            } catch (IOException | RuntimeException e) {
                err = e instanceof JellyfinApi.JellyfinException ? e.getMessage() : "The server couldn't be reached.";
            }
            final List<JellyfinApi.Item> list = eps;
            final String error = err;
            a.runOnUiThread(() -> {
                if (a.isDestroyed()) return;
                if (details != null) {
                    root.removeView(details);
                    details = null;
                }
                LinearLayout card = w.stateCard(Glyph.Kind.GRID, Theme.HOLO_BLUE, series.name + " · " + season.name,
                        error != null ? error : list.isEmpty() ? "No episodes on the server for this season." : "OK on an episode plays it, then the following ones.");
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        final int at = i;
                        JellyfinApi.Item e = list.get(i);
                        long pos = Math.max(store.progress(JellyfinApi.key(account.serverId(), e)), e.positionMs);
                        String state = e.played ? "  ✓ watched" : pos > 5_000 ? "  ▶ " + clock(pos) : "";
                        card.addView(button(episodeLabel(e) + (e.runtimeMs > 0 ? " · " + minutes(e.runtimeMs) : "") + state,
                                v -> playList(list, at, -1)));
                    }
                }
                card.addView(button("Close", v -> onBack()));
                ScrollView sc = new ScrollView(a);
                sc.addView(card);
                FrameLayout holder = new FrameLayout(a);
                holder.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.94f));
                holder.setClickable(true);
                holder.addView(sc, new FrameLayout.LayoutParams(t.dp(680), Widgets.MATCH, Gravity.CENTER));
                details = holder;
                root.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
                for (int i = 0; i < card.getChildCount(); i++) if (card.getChildAt(i).isFocusable()) { card.getChildAt(i).requestFocus(); break; }
            });
        });
    }

    private void playEpisodesFrom(final JellyfinApi.Item series, final JellyfinApi.Item episode) {
        worker.execute(() -> {
            try {
                final List<JellyfinApi.Item> eps = api.episodes(series.id, episode.seasonId);
                int at = 0;
                for (int i = 0; i < eps.size(); i++) if (eps.get(i).id.equals(episode.id)) at = i;
                final int start = at;
                a.runOnUiThread(() -> playList(eps, start, -1));
            } catch (IOException | RuntimeException e) {
                a.runOnUiThread(() -> playList(java.util.Collections.singletonList(episode), 0, -1));
            }
        });
    }

    /** Starts the player on {@code list[start]}; {@code resumeMs} -1 = use saved positions, 0 = from the start. */
    void playList(List<JellyfinApi.Item> list, int start, long resumeMs) {
        int n = list.size();
        String[] titles = new String[n], subs = new String[n], urls = new String[n], keys = new String[n];
        long[] resume = new long[n];
        for (int i = 0; i < n; i++) {
            JellyfinApi.Item it = list.get(i);
            titles[i] = it.type.equals("Episode") ? (it.seriesName != null ? it.seriesName + " · " : "") + episodeLabel(it) : it.name;
            subs[i] = it.type.equals("Episode") ? null : yearAndRuntime(it);
            urls[i] = api.streamUrl(it);
            keys[i] = JellyfinApi.key(account.serverId(), it);
            resume[i] = i == start && resumeMs >= 0 ? resumeMs : it.positionMs;
        }
        if (resumeMs == 0) store.saveProgress(keys[start], 0, 1);
        a.startActivity(new Intent(a, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_SECTION, section.name())
                .putExtra(PlayerActivity.EXTRA_TITLES, titles).putExtra(PlayerActivity.EXTRA_SUBTITLES, subs)
                .putExtra(PlayerActivity.EXTRA_URLS, urls).putExtra(PlayerActivity.EXTRA_KEYS, keys)
                .putExtra(PlayerActivity.EXTRA_RESUME, resume).putExtra(PlayerActivity.EXTRA_START, start)
                .putExtra(PlayerActivity.EXTRA_JELLYFIN, true));
    }

    // ------------------------------------------------------------------ text

    static String describe(JellyfinApi.Item it) {
        StringBuilder b = new StringBuilder();
        String yr = yearAndRuntime(it);
        if (!yr.isEmpty()) b.append(yr);
        if (!it.genres.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(TextUtils.join(", ", it.genres));
        if (it.overview != null) b.append(b.length() > 0 ? "\n\n" : "").append(it.overview);
        return b.length() > 0 ? b.toString() : "No description on the server.";
    }

    static String yearAndRuntime(JellyfinApi.Item it) {
        String y = it.year > 0 ? String.valueOf(it.year) : "";
        String r = it.runtimeMs > 0 ? minutes(it.runtimeMs) : "";
        return y.isEmpty() ? r : r.isEmpty() ? y : y + " · " + r;
    }

    static String minutes(long ms) {
        long m = ms / 60_000;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }

    static String episodeLabel(JellyfinApi.Item e) {
        return (e.seasonIndex > 0 || e.index > 0 ? "S" + e.seasonIndex + " E" + e.index + " · " : "") + e.name;
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private TextView button(String label, View.OnClickListener click) {
        TextView v = w.navItem(label, false, click);
        v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        lp.topMargin = t.dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView line(String text, int color) {
        TextView v = t.text(a, Theme.T_BODY, color, Theme.FONT_REGULAR);
        v.setText(text);
        return v;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout card = convert instanceof LinearLayout ? (LinearLayout) convert : card();
            JellyfinApi.Item it = shown.get(i);
            posters.load(api.imageUrl(it, 360), (ImageView) card.getChildAt(0));
            boolean fav = store.isFavourite(JellyfinApi.key(account.serverId(), it));
            ((TextView) card.getChildAt(1)).setText((fav ? "★ " : "") + it.name);
            ((TextView) card.getChildAt(2)).setText(it.year > 0 ? String.valueOf(it.year) : "");
            card.setContentDescription(it.name);
            boolean focused = grid != null && grid.hasFocus() && grid.getSelectedItemPosition() == i;
            card.setBackground(focused
                    ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.28f), Theme.ELECTRIC_BLUE, 2, Theme.R_TILE)
                    : t.rounded(Theme.withAlpha(Theme.PANEL_NAVY, 0.92f), Theme.R_TILE));
            return card;
        }

        private LinearLayout card() {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(t.dp(8), t.dp(8), t.dp(8), t.dp(8));
            ImageView img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            c.addView(img, new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(150)));
            for (int k = 0; k < 2; k++) {
                TextView v = t.text(a, k == 0 ? Theme.T_LABEL + 1 : Theme.T_LABEL, k == 0 ? Theme.SOFT_WHITE : Theme.MUTED_GRAY,
                        k == 0 ? Theme.FONT_MEDIUM : Theme.FONT_REGULAR);
                v.setSingleLine(true);
                v.setEllipsize(TextUtils.TruncateAt.END);
                c.addView(v);
            }
            return c;
        }
    }
}
