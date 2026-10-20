package tv.calmhome.launcher.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import tv.calmhome.launcher.data.TvInputs;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** Physical inputs as dark panel cards. OK switches; status dots only reflect what Android reports. */
public class InputsPanel extends Panel {

    private LinearLayout row;

    public InputsPanel(Host host) {
        super(host);
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        TextView hint = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        hint.setText("OK to switch · Back to return");
        body.addView(hint);

        row = new LinearLayout(c);
        row.setClipChildren(false);
        row.setPadding(t.dp(6), t.dp(30), t.dp(6), t.dp(10));
        List<TvInputs.Source> sources = TvInputs.list(c);
        int n = Math.max(4, Math.min(sources.size(), 5));
        float availDp = (t.screenW() - 2f * t.safeX() - t.dp(12)) / c.getResources().getDisplayMetrics().density / t.scale;
        int gap = 18;
        int cardW = Math.round((availDp - (n - 1) * gap) / n);
        int cardH = Math.round(cardW * 0.64f);
        for (TvInputs.Source s : sources) {
            View card = card(s, cardW, cardH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(cardW), t.dp(cardH));
            if (row.getChildCount() > 0) lp.leftMargin = t.dp(gap);
            row.addView(card, lp);
        }
        if (sources.isEmpty()) {
            TextView none = t.text(c, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
            none.setText("This TV reported no inputs.");
            row.addView(none);
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.addView(row);
        body.addView(hs, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        return frame("Inputs", body);
    }

    private View card(final TvInputs.Source s, int wDp, int hDp) {
        FrameLayout card = new FrameLayout(c);
        card.setBackground(t.rounded(Theme.withAlpha(Theme.PANEL_NAVY, 0.92f), Theme.R_PANEL));
        card.setPadding(t.dp(18), t.dp(16), t.dp(18), t.dp(16));
        card.setContentDescription(s.name + ", " + s.detail());

        ImageView icon = new ImageView(c);
        icon.setImageDrawable(new Glyph(s.tuner ? Glyph.Kind.TV : Glyph.Kind.HDMI, Theme.SOFT_WHITE));
        card.addView(icon, new FrameLayout.LayoutParams(t.dp(30), t.dp(30), Gravity.TOP | Gravity.START));

        int dotColor = s.state == TvInputs.State.CONNECTED ? Theme.SUCCESS
                : s.state == TvInputs.State.STANDBY ? Theme.WARNING : 0;
        if (dotColor != 0) {
            View dot = new View(c);
            dot.setBackground(t.rounded(dotColor, 6));
            card.addView(dot, new FrameLayout.LayoutParams(t.dp(10), t.dp(10), Gravity.TOP | Gravity.END));
        }

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = t.text(c, 20, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        name.setText(s.name);
        name.setSingleLine(true);
        text.addView(name);
        TextView detail = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        detail.setText(s.detail());
        detail.setSingleLine(true);
        LinearLayout.LayoutParams dl = w.wrap();
        dl.topMargin = t.dp(5);
        text.addView(detail, dl);
        card.addView(text, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM | Gravity.START));

        if (s.state == TvInputs.State.DISCONNECTED) card.setAlpha(0.6f);
        card.setOnClickListener(v -> {
            host.closeAll();
            if (!host.apps().start(s.intent)) host.toast("Couldn't switch to " + s.name);
        });
        w.focusable(card, Theme.R_PANEL, null, false);
        return card;
    }

    @Override
    public void focusFirst() {
        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }
}
