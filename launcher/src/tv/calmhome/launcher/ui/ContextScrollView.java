package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;

/**
 * A vertical scroller whose focus scrolling leaves room around the focused row.
 *
 * <p>Android scrolls a list only until the focused row is just visible, so on Settings the focused row sat flush against the
 * edge of its pane (its highlight cut by the scroller's bounds) with no sign of what comes next. This scroller asks for the row
 * and {@code margin} pixels either side of it, so the row is whole and readable and the neighbouring rows show; at the ends of the
 * list the margin simply runs out and the list stops where it ends.
 */
public final class ContextScrollView extends ScrollView {
    private final int margin;

    /** @param marginPx how much of the neighbouring content to keep in view around the focused row */
    public ContextScrollView(Context c, int marginPx) {
        super(c);
        this.margin = Math.max(0, marginPx);
        setClipToPadding(false);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect wider = new Rect(rect.left, rect.top - margin, rect.right, rect.bottom + margin);
        return super.computeScrollDeltaToGetChildRectOnScreen(wider);
    }
}
