package tv.calmhome.launcher.streaming;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tv.calmhome.launcher.core.Sports;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Sports: programmes that the user's own guide marks as sport (by category), on the user's own channels.
 * States are computed from the guide's times. Nothing is added: no schedules, results or "free" access to paid
 * events. OK on a live event opens its channel; holding OK adds it to or removes it from Favourites.
 */
final class SportsView {

    static final String ALL = "All sports", FAVOURITES = "Favourites";
    static final long BEFORE = 3 * 3_600_000L, AHEAD = 7 * 24 * 3_600_000L;

    static final class Event {
        final GuideData.Programme p;
        final Sports.Sport sport;
        final StreamingStore.Channel channel;   // null: the guide channel isn't in the user's playlists

        Event(GuideData.Programme p, Sports.Sport sport, StreamingStore.Channel channel) {
            this.p = p;
            this.sport = sport;
            this.channel = channel;
        }
    }

    private final Context c;
    private final Theme t;
    private final Widgets w;
    private final GuideLink link;
    private final GuideView.Host host;
    private final DateFormat clock;
    private final List<Event> all = new ArrayList<>();
    private List<Event> shown = new ArrayList<>();
    private String filter = ALL;
    private FrameLayout root;
    private LinearLayout chips;
    private ListView list;
    private View details;
    private final Adapter adapter = new Adapter();

    SportsView(Context c, Theme t, Widgets w, GuideLink link, GuideView.Host host) {
        this.c = c;
        this.t = t;
        this.w = w;
        this.link = link;
        this.host = host;
        this.clock = android.text.format.DateFormat.getTimeFormat(c);
        long now = System.currentTimeMillis();
        Set<String> seen = new HashSet<>();
        for (GuideData.Programme p : link.data.categorised(now - BEFORE, now + AHEAD, 5000)) {
            Sports.Sport s = Sports.of(p.category);
            if (s == null || !seen.add(p.key())) continue;
            all.add(new Event(p, s, link.channelFor(p.channel)));
        }
    }

    List<Event> events() {
        return all;
    }

    View build() {
        if (link.data.sources().isEmpty()) {
            return w.stateCard(Glyph.Kind.INFO, Theme.HOLO_BLUE, "No sports schedule",
                    "Sports lists events from your own programme guide (XMLTV) when it marks programmes as sport. Add a "
                            + "guide under Sources. ZeroPulse doesn't add schedules, and doesn't present paid events as free.");
        }
        if (all.isEmpty()) {
            return w.stateCard(Glyph.Kind.INFO, Theme.HOLO_BLUE, "No sports in your guide",
                    "Your guide doesn't mark any programme in the coming 7 days as sport. Events appear here only when "
                            + "the guide's own categories say so.");
        }
        root = new FrameLayout(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView strip = new HorizontalScrollView(c);
        strip.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(c);
        strip.addView(chips);
        col.addView(strip, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        TextView hint = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        hint.setText("From your guide's categories · OK: watch when live, or details · hold OK: Favourites. "
                + "Official provider links aren't available in this beta.");
        hint.setPadding(t.dp(4), t.dp(6), t.dp(4), t.dp(6));
        col.addView(hint);
        list = new ListView(c);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(t.dp(6));
        list.setSelector(new ColorDrawable(0));
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { adapter.notifyDataSetChanged(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { adapter.notifyDataSetChanged(); }
        });
        list.setOnFocusChangeListener((v, has) -> adapter.notifyDataSetChanged());
        list.setOnItemClickListener((p, v, pos, id) -> open(shown.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            toggleFavourite(shown.get(pos));
            return true;
        });
        col.addView(list, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        refresh();
        return root;
    }

    private void refresh() {
        Map<Sports.Sport, Integer> counts = new EnumMap<>(Sports.Sport.class);
        boolean anyFav = false;
        for (Event e : all) {
            counts.put(e.sport, counts.containsKey(e.sport) ? counts.get(e.sport) + 1 : 1);
            anyFav |= link.data.isFavouriteEvent(e.p.key());
        }
        List<String> names = new ArrayList<>();
        names.add(ALL);
        if (anyFav) names.add(FAVOURITES);
        for (Sports.Sport s : Sports.Sport.values()) if (counts.containsKey(s)) names.add(s.label);
        if (!names.contains(filter)) filter = ALL;
        List<View> row = new ArrayList<>();
        for (final String n : names) row.add(w.navItem(n, n.equals(filter), v -> {
            filter = n;
            refresh();
        }));
        Widgets.replaceChips(chips, row);
        shown = new ArrayList<>();
        for (Event e : all) {
            if (FAVOURITES.equals(filter) && !link.data.isFavouriteEvent(e.p.key())) continue;
            if (!ALL.equals(filter) && !FAVOURITES.equals(filter) && !e.sport.label.equals(filter)) continue;
            shown.add(e);
        }
        adapter.notifyDataSetChanged();
    }

    void toggleFavourite(Event e) {
        boolean on = !link.data.isFavouriteEvent(e.p.key());
        link.data.setFavouriteEvent(e.p.key(), on);
        Toast.makeText(c, e.p.title + (on ? " added to Favourites" : " removed from Favourites"), Toast.LENGTH_SHORT).show();
        refresh();
    }

    void open(Event e) {
        long now = System.currentTimeMillis();
        if (Sports.state(now, e.p.start, e.p.stop) == Sports.State.LIVE && e.channel != null) {
            host.watch(e.channel);
            return;
        }
        String when = DateUtils.formatDateTime(c, e.p.start, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_ALL) + " " + clock.format(new Date(e.p.start)) + "–" + clock.format(new Date(e.p.stop));
        StringBuilder b = new StringBuilder(when + "\n" + e.sport.label + " · " + e.p.category);
        b.append("\n").append(e.channel != null ? "On " + e.channel.name : "On a guide channel that isn't in your playlists");
        if (e.p.subtitle != null) b.append("\n").append(e.p.subtitle);
        if (e.p.description != null) b.append("\n\n").append(e.p.description);
        b.append("\n\n").append(state(e, now) == Sports.State.FINISHED ? "This event is over."
                : "Shown as listed in your guide; whether it airs, and in which region, depends on the broadcaster.");
        LinearLayout card = w.stateCard(Glyph.Kind.INFO, Theme.HOLO_BLUE, e.p.title, b.toString());
        TextView close = w.navItem("Close", false, v -> closeDetails());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        lp.topMargin = t.dp(10);
        card.addView(close, lp);
        FrameLayout holder = new FrameLayout(c);
        holder.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.94f));   // text behind a glass card showed through on the TV
        holder.setClickable(true);
        holder.addView(card, new FrameLayout.LayoutParams(t.dp(640), Widgets.WRAP, Gravity.CENTER));
        details = holder;
        root.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        close.requestFocus();
    }

    boolean onBack() {
        if (details == null) return false;
        closeDetails();
        return true;
    }

    private void closeDetails() {
        root.removeView(details);
        details = null;
        if (list != null) list.requestFocus();
    }

    private static Sports.State state(Event e, long now) {
        return Sports.state(now, e.p.start, e.p.stop);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : row();
            Event e = shown.get(i);
            long now = System.currentTimeMillis();
            Sports.State st = state(e, now);
            TextView when = (TextView) row.getChildAt(0), title = (TextView) row.getChildAt(1), where = (TextView) row.getChildAt(2);
            when.setText(st == Sports.State.LIVE ? "● Live" : st == Sports.State.FINISHED ? "Finished"
                    : DateUtils.isToday(e.p.start) ? "Today " + clock.format(new Date(e.p.start))
                    : DateUtils.formatDateTime(c, e.p.start, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL)
                    + " " + clock.format(new Date(e.p.start)));
            when.setTextColor(st == Sports.State.LIVE ? Theme.SUCCESS : st == Sports.State.FINISHED ? Theme.MUTED_GRAY : Theme.PULSE_CYAN);
            boolean fav = link.data.isFavouriteEvent(e.p.key());
            title.setText((fav ? "★ " : "") + e.p.title + (e.p.subtitle != null ? " · " + e.p.subtitle : ""));
            where.setText(e.sport.label + " · " + (e.channel != null ? e.channel.name : "not in your playlists"));
            boolean focused = list != null && list.hasFocus() && list.getSelectedItemPosition() == i;
            row.setBackground(focused
                    ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.28f), Theme.ELECTRIC_BLUE, 2, Theme.R_TILE)
                    : t.rounded(Theme.withAlpha(Theme.PANEL_NAVY, 0.92f), Theme.R_TILE));
            row.setContentDescription(e.p.title);
            return row;
        }

        private LinearLayout row() {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10));
            r.addView(cell(Theme.FONT_MEDIUM), new LinearLayout.LayoutParams(t.dp(150), Widgets.WRAP));
            r.addView(cell(Theme.FONT_REGULAR), new LinearLayout.LayoutParams(0, Widgets.WRAP, 1.6f));
            r.addView(cell(Theme.FONT_REGULAR), new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
            return r;
        }

        private TextView cell(String font) {
            TextView v = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, font);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            v.setPadding(0, 0, t.dp(12), 0);
            return v;
        }
    }
}
