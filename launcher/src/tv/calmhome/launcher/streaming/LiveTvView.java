package tv.calmhome.launcher.streaming;

import android.content.Context;
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
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tv.calmhome.launcher.core.ChannelFilter;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Live TV: search, filters (All, Favourites, Recently watched, then the playlists' own groups) and a channel grid.
 * The grid is a framework GridView, so only visible cards exist even for very long playlists.
 * OK opens a channel; holding OK adds it to or removes it from Favourites.
 */
final class LiveTvView {

    interface Host {
        void watch(StreamingStore.Channel ch);
        /** Optional one-line programme info (e.g. "Now: …") for a channel, or null. */
        String nowPlaying(StreamingStore.Channel ch);
    }

    static final int COLUMNS = 3;

    private final Context c;
    private final Theme t;
    private final Widgets w;
    private final StreamingStore store;
    private final Logos logos;
    private final Host host;

    private List<StreamingStore.Channel> all = new ArrayList<>();
    private List<StreamingStore.Channel> shown = new ArrayList<>();
    private Set<String> favourites;
    private List<String> recent;
    private String filter = ChannelFilter.ALL;
    private String query = "";
    private LinearLayout chips;
    private GridView grid;
    private TextView count;
    private final Adapter adapter = new Adapter();

    LiveTvView(Context c, Theme t, Widgets w, StreamingStore store, Logos logos, Host host) {
        this.c = c;
        this.t = t;
        this.w = w;
        this.store = store;
        this.logos = logos;
        this.host = host;
    }

    View build() {
        all = store.channels();
        favourites = store.favouriteUrls();
        recent = store.recentUrls(50);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);

        EditText search = new EditText(c);
        t.style(search, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        search.setHint("Search channels");
        search.setHintTextColor(Theme.MUTED_GRAY);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setPadding(t.dp(16), t.dp(9), t.dp(16), t.dp(9));
        search.setText(query);
        search.setContentDescription("Search channels");
        search.setBackground(t.outlined(Theme.PANEL_NAVY, Theme.BORDER, 1, Theme.R_PILL));
        search.setOnFocusChangeListener((v, has) -> v.setBackground(t.outlined(Theme.PANEL_NAVY,
                has ? Theme.ELECTRIC_BLUE : Theme.BORDER, has ? 2 : 1, Theme.R_PILL)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                query = e.toString();
                refresh();
            }
        });
        col.addView(search, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        HorizontalScrollView strip = new HorizontalScrollView(c);
        strip.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(c);
        chips.setPadding(0, t.dp(8), 0, t.dp(4));
        strip.addView(chips);
        col.addView(strip, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        count = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        count.setPadding(t.dp(4), t.dp(2), 0, t.dp(6));
        col.addView(count);

        grid = new GridView(c);
        grid.setNumColumns(COLUMNS);
        grid.setHorizontalSpacing(t.dp(Theme.GAP));
        grid.setVerticalSpacing(t.dp(Theme.GAP));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(0));
        grid.setDrawSelectorOnTop(false);
        grid.setClipToPadding(false);
        grid.setPadding(t.dp(4), t.dp(4), t.dp(4), t.dp(24));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> host.watch(shown.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            toggleFavourite(shown.get(pos));
            return true;
        });
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                adapter.notifyDataSetChanged();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {
                adapter.notifyDataSetChanged();
            }
        });
        grid.setOnFocusChangeListener((v, has) -> adapter.notifyDataSetChanged());
        col.addView(grid, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));
        refresh();
        return col;
    }

    void toggleFavourite(StreamingStore.Channel ch) {
        boolean on = !favourites.contains(ch.url);
        store.setFavourite(ch.url, on);
        if (on) favourites.add(ch.url);
        else favourites.remove(ch.url);
        Toast.makeText(c, ch.name + (on ? " added to Favourites" : " removed from Favourites"), Toast.LENGTH_SHORT).show();
        refresh();
    }

    void refresh() {
        List<String> names = ChannelFilter.filters(all, favourites, recent);
        if (!names.contains(filter)) filter = ChannelFilter.ALL;
        List<View> row = new ArrayList<>();
        for (final String f : names) row.add(w.navItem(f, f.equals(filter), v -> {
            filter = f;
            refresh();
        }));
        Widgets.replaceChips(chips, row);
        shown = ChannelFilter.apply(all, filter, query, favourites, recent);
        count.setText(shown.size() + " of " + all.size() + " · OK: watch · hold OK: add to or remove from Favourites");
        adapter.notifyDataSetChanged();
    }

    /** For tests and the screen: what the grid currently lists. */
    List<StreamingStore.Channel> shown() {
        return shown;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return shown.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout card = convert instanceof LinearLayout ? (LinearLayout) convert : card();
            StreamingStore.Channel ch = shown.get(i);
            ImageView logo = (ImageView) card.getChildAt(0);
            TextView name = (TextView) card.getChildAt(1), sub = (TextView) card.getChildAt(2);
            logos.load(ch.logo, logo);
            boolean fav = favourites.contains(ch.url);
            name.setText(fav ? "★ " + ch.name : ch.name);
            String now = host.nowPlaying(ch);
            sub.setText(now != null ? now : (ch.group != null ? ch.group : ""));
            card.setContentDescription(ch.name + (fav ? ", favourite" : ""));
            boolean focused = grid != null && grid.hasFocus() && grid.getSelectedItemPosition() == i;
            card.setBackground(focused
                    ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.28f), Theme.ELECTRIC_BLUE, 2, Theme.R_TILE)
                    : t.rounded(Theme.withAlpha(Theme.PANEL_NAVY, 0.92f), Theme.R_TILE));
            return card;
        }

        private LinearLayout card() {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(t.dp(12), t.dp(10), t.dp(12), t.dp(10));
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(logo, new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(44)));
            TextView name = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            nl.topMargin = t.dp(6);
            card.addView(name, nl);
            TextView sub = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setGravity(Gravity.CENTER);
            card.addView(sub, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
            return card;
        }
    }
}
