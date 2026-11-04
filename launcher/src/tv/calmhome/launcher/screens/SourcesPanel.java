package tv.calmhome.launcher.screens;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.EnumMap;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.core.SourceEntry.Category;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.ui.Theme;

/** The same owner catalog is reachable from the category overview and the action dock. */
final class SourcesPanel extends DownloadPage {
    private final String heading;
    private final EnumMap<Category,TextView> actions = new EnumMap<>(Category.class);
    SourcesPanel(Host host, String heading) { super(host); this.heading = heading; }
    @Override protected View build() {
        body.addView(text("Choose a category to add, review or remove trusted developer links.", Theme.T_BODY, false));
        LinearLayout cards = column();
        for (Category category : Category.values()) {
            TextView row = action(category.title, () -> host.open(new SourceCategoryPanel(host, category)));
            row.setContentDescription(category.title + ", manage sources");
            actions.put(category, row);
            cards.addView(row, spaced(4));
            if (first == null) first = row;
        }
        body.addView(cards, spaced(8));
        recount();
        return screen(heading);
    }
    private void recount() {
        EnumMap<Category,Integer> totals = new EnumMap<>(Category.class);
        for (SourceEntry s : new SourceCatalog(c).all()) totals.put(s.category, totals.getOrDefault(s.category, 0) + 1);
        for (Category category : actions.keySet()) {
            int total = totals.getOrDefault(category, 0);
            updateText(actions.get(category), category.title + "   ·   " + total + (total == 1 ? " link" : " links"));
        }
    }
    @Override public void onUncovered() { recount(); }
}
