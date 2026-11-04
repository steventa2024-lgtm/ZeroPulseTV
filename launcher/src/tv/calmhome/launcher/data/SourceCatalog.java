package tv.calmhome.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import tv.calmhome.launcher.core.SourceEntry;

/** Private local catalog. Entries are added only by the owner's explicit Save action. */
public final class SourceCatalog {
    private final SharedPreferences prefs;
    public SourceCatalog(Context c) { prefs = c.getSharedPreferences("download_sources_v1", Context.MODE_PRIVATE); }

    private Set<String> rows() { return new HashSet<>(prefs.getStringSet("entries", Collections.emptySet())); }

    public void indexFile(long downloadId, SourceEntry.Category category) {
        prefs.edit().putString("file_" + downloadId, category.name()).apply();
    }

    public boolean containsFile(long downloadId, SourceEntry.Category category) {
        return category.name().equals(prefs.getString("file_" + downloadId, null));
    }

    public void forgetFile(long downloadId) {
        prefs.edit().remove("file_" + downloadId).apply();
    }

    public List<SourceEntry> all() {
        List<SourceEntry> entries = new ArrayList<>();
        for (String row : rows()) {
            try {
                JSONArray a = new JSONArray(row);
                entries.add(new SourceEntry(SourceEntry.Category.valueOf(a.getString(0)), a.getString(1),
                        a.getString(2), a.getString(3), a.getString(4)));
            } catch (JSONException | IllegalArgumentException ignored) { /* Skip an unreadable entry independently. */ }
        }
        entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return entries;
    }

    private String encoded(SourceEntry s) {
        return new JSONArray().put(s.category.name()).put(s.label).put(s.url).put(s.code).put(s.sha256).toString();
    }

    public void add(SourceEntry s) {
        s.checkNew(all());
        Set<String> rows = rows();
        rows.add(encoded(s));
        prefs.edit().putStringSet("entries", rows).apply();
    }

    public void remove(SourceEntry s) {
        Set<String> rows = rows();
        rows.remove(encoded(s));
        prefs.edit().putStringSet("entries", rows).apply();
    }
}
