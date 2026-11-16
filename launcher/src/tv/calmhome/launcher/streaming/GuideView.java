package tv.calmhome.launcher.streaming;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * The programme guide: matched channels with what's on at the chosen time and what's next. Day chips pick the
 * day; Left/Right on a channel moves the time by 30 minutes; OK shows programme details (and Watch when it's on
 * now). Times are shown in the TV's time zone and 12/24-hour setting.
 */
final class GuideView {

    interface Host {
        void watch(StreamingStore.Channel ch);
    }

    static final long STEP = 30 * 60_000L, DAY = 24 * 3_600_000L;

    private final Context c;
    private final Theme t;
    private final Widgets w;
    private final GuideLink link;
    private final List<StreamingStore.Channel> rows = new ArrayList<>();
    private final Host host;
    private final DateFormat clock;
    private final int total;
    private long at;
    private FrameLayout root;
    private TextView header;
    private LinearLayout days;
    private ListView list;
    private View details;
    private final Adapter adapter = new Adapter();

    GuideView(Context c, Theme t, Widgets w, GuideLink link, List<StreamingStore.Channel> channels, Host host) {
        this.c = c;
        this.t = t;
        this.w = w;
        this.link = link;
        this.host = host;
        this.clock = android.text.format.DateFormat.getTimeFormat(c);
        this.total = channels.size();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (StreamingStore.Channel ch : channels) {
            String id = link.guideId(ch);
            if (id != null && seen.add(id)) rows.add(ch);
        }
        at = floor(System.currentTimeMillis());
    }

    static long floor(long ms) {
        return ms - Math.floorMod(ms, STEP);
    }

    View build() {
        long now = System.currentTimeMillis();
        if (link.data.sources().isEmpty()) {
            return w.stateCard(Glyph.Kind.INFO, Theme.HOLO_BLUE, "No guide yet",
                    "Add an XMLTV guide (an HTTPS link or a file on this TV) under Sources. If one of your playlists names "
                            + "a guide, Sources offers to add it. ZeroPulse doesn't include guide data.");
        }
        if (rows.isEmpty()) {
            return w.stateCard(Glyph.Kind.INFO, Theme.WARNING, "The guide doesn't match your channels",
                    "None of your playlist channels matches a guide channel by tvg-id or by name, so no programmes can be "
                            + "shown. Use a guide made for your playlist.");
        }
        root = new FrameLayout(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView strip = new HorizontalScrollView(c);
        strip.setHorizontalScrollBarEnabled(false);
        days = new LinearLayout(c);
        strip.addView(days);
        col.addView(strip, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        header = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        header.setPadding(t.dp(4), t.dp(6), t.dp(4), t.dp(6));
        col.addView(header);
        long last = link.data.lastStop();
        boolean unzoned = false;
        for (GuideData.GuideSource s : link.data.sources()) unzoned |= s.unzoned;
        if (last < now) col.addView(note("The guide's data ended " + DateUtils.getRelativeTimeSpanString(last)
                + ". Refresh it under Sources."));
        if (unzoned) col.addView(note("Some guide times have no time zone and were read as UTC: they may be off by hours."));
        int unmatched = Math.max(0, total - link.matched);
        if (unmatched > 0) col.addView(note(unmatched + " of your channels have no guide data (no matching tvg-id or name)."));

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
        list.setOnItemClickListener((p, v, pos, id) -> showDetails(rows.get(pos)));
        list.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { move(STEP); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { move(-STEP); return true; }
            return false;
        });
        col.addView(list, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        refresh();
        return root;
    }

    private TextView note(String s) {
        TextView v = t.text(c, Theme.T_LABEL, Theme.WARNING, Theme.FONT_REGULAR);
        v.setText(s);
        v.setPadding(t.dp(4), 0, t.dp(4), t.dp(4));
        return v;
    }

    /** Moves the shown time, within 12 hours ago … the end of the guide. */
    void move(long delta) {
        long now = System.currentTimeMillis();
        long min = floor(now - XmltvReader.PAST_MS), max = Math.max(floor(now), floor(link.data.lastStop()));
        at = Math.max(min, Math.min(max, at + delta));
        refresh();
    }

    long at() {
        return at;
    }

    private void refresh() {
        List<View> row = new ArrayList<>();
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long last = link.data.lastStop();
        for (int d = 0; d < 8; d++) {
            Calendar day = (Calendar) today.clone();
            day.add(Calendar.DAY_OF_MONTH, d);
            final long start = day.getTimeInMillis();
            if (d > 0 && start > last) break;
            Calendar end = (Calendar) day.clone();
            end.add(Calendar.DAY_OF_MONTH, 1);
            boolean current = at >= start && at < end.getTimeInMillis();
            String label = d == 0 ? "Today" : d == 1 ? "Tomorrow"
                    : DateUtils.formatDateTime(c, start, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
            final int dd = d;
            row.add(w.navItem(label, current, v -> {
                at = dd == 0 ? floor(System.currentTimeMillis()) : floor(start + 18 * 3_600_000L);   // evenings first
                refresh();
            }));
        }
        Widgets.replaceChips(days, row);
        header.setText("Showing " + DateUtils.formatDateTime(c, at, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL)
                + " " + clock.format(new Date(at)) + " · ◀ ▶ on a channel: 30 minutes · OK: details");
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ details

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

    void showDetails(final StreamingStore.Channel ch) {
        List<GuideData.Programme> p = link.data.programmes(link.guideId(ch), at, at + 1, 1);
        long now = System.currentTimeMillis();
        String head, body;
        final boolean onNow;
        if (p.isEmpty()) {
            head = ch.name;
            body = "No programme information for this time.";
            onNow = false;
        } else {
            GuideData.Programme g = p.get(0);
            head = g.title;
            StringBuilder b = new StringBuilder(span(g) + " · " + ch.name);
            if (g.subtitle != null) b.append("\n").append(g.subtitle);
            if (g.episode != null) b.append(" (").append(g.episode).append(")");
            if (g.category != null) b.append("\n").append(g.category);
            if (g.description != null) b.append("\n\n").append(g.description);
            body = b.toString();
            onNow = g.onAt(now);
        }
        LinearLayout card = w.stateCard(Glyph.Kind.TV, Theme.HOLO_BLUE, head, body);
        if (onNow) card.addView(action("Watch " + ch.name, v -> host.watch(ch)));
        card.addView(action("Close", v -> closeDetails()));
        FrameLayout holder = new FrameLayout(c);
        holder.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.94f));   // text behind a glass card showed through on the TV
        holder.addView(card, new FrameLayout.LayoutParams(t.dp(640), Widgets.WRAP, Gravity.CENTER));
        holder.setClickable(true);
        details = holder;
        root.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        card.getChildAt(card.getChildCount() - (onNow ? 2 : 1)).requestFocus();
    }

    private TextView action(String label, View.OnClickListener click) {
        TextView v = w.navItem(label, false, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        lp.topMargin = t.dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    String span(GuideData.Programme g) {
        return clock.format(new Date(g.start)) + "–" + clock.format(new Date(g.stop));
    }

    // ------------------------------------------------------------------ rows

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return rows.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : row();
            StreamingStore.Channel ch = rows.get(i);
            List<GuideData.Programme> p = link.data.programmes(link.guideId(ch), at, at + 6 * 3_600_000L, 2);
            ((TextView) row.getChildAt(0)).setText(ch.name);
            TextView nowV = (TextView) row.getChildAt(1), nextV = (TextView) row.getChildAt(2);
            if (p.isEmpty()) {
                nowV.setText("No information");
                nextV.setText("");
            } else {
                GuideData.Programme a = p.get(0);
                nowV.setText((a.onAt(at) ? span(a) : "From " + clock.format(new Date(a.start))) + "  " + a.title);
                nextV.setText(p.size() > 1 ? "Next " + clock.format(new Date(p.get(1).start)) + "  " + p.get(1).title : "");
            }
            boolean focused = list != null && list.hasFocus() && list.getSelectedItemPosition() == i;
            row.setBackground(focused
                    ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.28f), Theme.ELECTRIC_BLUE, 2, Theme.R_TILE)
                    : t.rounded(Theme.withAlpha(Theme.PANEL_NAVY, 0.92f), Theme.R_TILE));
            row.setContentDescription(ch.name);
            return row;
        }

        private LinearLayout row() {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10));
            r.addView(cell(Theme.SOFT_WHITE, Theme.FONT_MEDIUM), new LinearLayout.LayoutParams(t.dp(170), Widgets.WRAP));
            r.addView(cell(Theme.SOFT_WHITE, Theme.FONT_REGULAR), new LinearLayout.LayoutParams(0, Widgets.WRAP, 1.2f));
            r.addView(cell(Theme.MUTED_GRAY, Theme.FONT_REGULAR), new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
            return r;
        }

        private TextView cell(int color, String font) {
            TextView v = t.text(c, Theme.T_BODY, color, font);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            v.setPadding(0, 0, t.dp(12), 0);
            return v;
        }
    }
}
