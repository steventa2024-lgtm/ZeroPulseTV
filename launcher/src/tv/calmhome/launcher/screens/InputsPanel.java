package tv.calmhome.launcher.screens;

import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.data.TvInputs;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * The TV's physical inputs, exactly as Android's TV input service reports them (nothing is invented: an input the TV does not list is
 * not shown, and a status dot appears only for a state the TV reported). OK hands the switch to the TV's own input service; this
 * launcher has no privileged access, so if the TV refuses, it says so and offers the TV's own settings instead of pretending.
 *
 * <p>In Liquid Glass each input is a card of the approved glass whose icon and name lift slightly on focus; the glass itself never
 * moves. Left and Right stay on the row.
 */
public class InputsPanel extends Panel {

    private LinearLayout row;
    private final List<View> cards = new ArrayList<>();

    public InputsPanel(Host host) {
        super(host);
    }

    private boolean glass() {
        return t.liquid && t.kit != null;
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        TextView hint = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        hint.setText("OK switches with the TV's own input service  ·  Back returns");
        body.addView(hint);

        row = new LinearLayout(c);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(t.dp(6), t.dp(30), t.dp(6), t.dp(10));
        List<TvInputs.Source> sources = TvInputs.list(c);
        int n = Math.max(4, Math.min(sources.size(), 5));
        float availDp = (t.screenW() - 2f * t.safeX() - t.dp(12)) / c.getResources().getDisplayMetrics().density / t.scale;
        int gap = 18;
        int cardW = Math.round((availDp - (n - 1) * gap) / n);
        int cardH = Math.round(cardW * 0.64f);
        for (TvInputs.Source s : sources) {
            View card = card(s);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(cardW), t.dp(cardH));
            if (row.getChildCount() > 0) lp.leftMargin = t.dp(gap);
            row.addView(card, lp);
            cards.add(card);
        }
        if (sources.isEmpty()) {
            // An honest empty state: no fabricated HDMI cards. The TV's own settings are one OK away when this TV has them.
            final Intent settings = new Intent(Settings.ACTION_SETTINGS);
            boolean canOpen = host.apps().resolves(settings);
            String detail = canOpen ? "OK opens the TV's own settings, where its inputs are managed." : "Inputs can be chosen from the TV's own menu.";
            View empty;
            if (glass()) {
                empty = w.glassNotice("This TV reported no inputs", detail, Math.round(availDp * 0.62f), 96,
                        canOpen ? v -> host.apps().start(settings) : null);
            } else {
                TextView none = t.text(c, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
                none.setText("This TV reported no inputs. " + detail);
                if (canOpen) {
                    none.setFocusable(true);
                    none.setOnClickListener(v -> host.apps().start(settings));
                }
                empty = none;
            }
            row.addView(empty);
            cards.add(empty instanceof LinearLayout ? Widgets.cardOf(empty) : empty);
        }
        Widgets.lockRowEnds(cards);
        HorizontalScrollView hs = new tv.calmhome.launcher.ui.SafeMarginScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.addView(row);
        body.addView(hs, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        return frame("Inputs", body);
    }

    private View card(final TvInputs.Source s) {
        FrameLayout content = new FrameLayout(c);
        content.setPadding(t.dp(18), t.dp(16), t.dp(18), t.dp(16));

        ImageView icon = new ImageView(c);
        icon.setImageDrawable(new Glyph(s.tuner ? Glyph.Kind.TV : Glyph.Kind.HDMI, Theme.SOFT_WHITE));
        content.addView(icon, new FrameLayout.LayoutParams(t.dp(30), t.dp(30), Gravity.TOP | Gravity.START));

        int dotColor = s.state == TvInputs.State.CONNECTED ? Theme.SUCCESS
                : s.state == TvInputs.State.STANDBY ? Theme.WARNING : 0;
        if (dotColor != 0) {
            View dot = new View(c);
            dot.setBackground(t.rounded(dotColor, 6));
            content.addView(dot, new FrameLayout.LayoutParams(t.dp(9), t.dp(9), Gravity.TOP | Gravity.END));
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
        content.addView(text, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM | Gravity.START));

        View card = w.glassButton(content, GraphiteGlassSurface.Shape.CARD, true, 1.03f);
        if (card == null) {
            content.setBackground(t.glass(Theme.R_PANEL, .9f));
            w.focusable(content, Theme.R_PANEL, null, false);
            card = content;
        }
        card.setContentDescription(s.name + (s.detail().isEmpty() ? "" : ", " + s.detail()));
        if (s.state == TvInputs.State.DISCONNECTED) content.setAlpha(0.6f);      // the words dim; the glass stays the same glass
        card.setOnClickListener(v -> {
            if (host.apps().start(s.intent)) {
                host.closeAll();
                return;
            }
            // The TV did not accept the switch from an ordinary app: say so, and offer the TV's own way.
            final Intent settings = new Intent(Settings.ACTION_SETTINGS);
            MenuPanel m = new MenuPanel(host, "Couldn't switch to " + s.name,
                    "This TV did not let ZeroPulse change the input. Choose it from the TV's own input menu instead.");
            if (host.apps().resolves(settings)) m.item("Open TV settings", () -> host.apps().start(settings));
            m.item("OK", null);
            host.open(m);
        });
        return card;
    }

    @Override
    public void focusFirst() {
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }
}
