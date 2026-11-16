package tv.calmhome.launcher.streaming;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import tv.calmhome.launcher.core.Guide;

/**
 * XMLTV guide data inside zp_streaming.db: guide sources, their channels and programmes, and favourite events.
 * A guide is replaced in one transaction, so a failed or interrupted refresh leaves the previous guide intact.
 */
public final class GuideData {

    public static final class GuideSource {
        public final long id;
        public final String kind, location, title, status;
        public final long refreshed;
        public final boolean unzoned;

        GuideSource(long id, String kind, String location, String title, long refreshed, String status, boolean unzoned) {
            this.id = id;
            this.kind = kind;
            this.location = location;
            this.title = title;
            this.refreshed = refreshed;
            this.status = status;
            this.unzoned = unzoned;
        }
    }

    public static final class Programme {
        public final String channel, title, subtitle, description, category, episode;
        public final long start, stop;

        Programme(String channel, long start, long stop, String title, String subtitle, String description,
                  String category, String episode) {
            this.channel = channel;
            this.start = start;
            this.stop = stop;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.category = category;
            this.episode = episode;
        }

        public boolean onAt(long t) {
            return start <= t && t < stop;
        }

        /** Stable identity for favourites: channel + start + title. */
        public String key() {
            return channel + "|" + start + "|" + title;
        }
    }

    private final StreamingStore store;

    public GuideData(StreamingStore store) {
        this.store = store;
    }

    public long addSource(String kind, String location, String title) {
        SQLiteDatabase db = store.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM guide_sources WHERE location = ?", new String[]{location});
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("location", location);
        v.put("title", title);
        v.put("added", System.currentTimeMillis());
        return db.insertOrThrow("guide_sources", null, v);
    }

    public void removeSource(long id) {
        store.getWritableDatabase().delete("guide_sources", "id = ?", new String[]{String.valueOf(id)});
    }

    void removeIfNeverLoaded(long id) {
        store.getWritableDatabase().delete("guide_sources", "id = ? AND refreshed = 0", new String[]{String.valueOf(id)});
    }

    public void setStatus(long id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        store.getWritableDatabase().update("guide_sources", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<GuideSource> sources() {
        List<GuideSource> out = new ArrayList<>();
        Cursor c = store.getReadableDatabase().rawQuery(
                "SELECT id, kind, location, title, refreshed, status, unzoned FROM guide_sources ORDER BY added", null);
        try {
            while (c.moveToNext()) {
                out.add(new GuideSource(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                        c.getString(5), c.getInt(6) != 0));
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------ writing

    /** Replaces one guide source's data. Call {@link #commit} on success; {@link #close} alone rolls back. */
    public final class Writer implements AutoCloseable {
        private final long sourceId;
        private final SQLiteDatabase db;
        private final SQLiteStatement channel, programme;
        private boolean done;

        Writer(long sourceId) {
            this.sourceId = sourceId;
            db = store.getWritableDatabase();
            db.beginTransaction();
            db.delete("guide_channels", "source_id = ?", new String[]{String.valueOf(sourceId)});
            db.delete("programmes", "source_id = ?", new String[]{String.valueOf(sourceId)});
            channel = db.compileStatement("INSERT OR REPLACE INTO guide_channels (source_id, xmltv_id, names) VALUES (?, ?, ?)");
            programme = db.compileStatement("INSERT INTO programmes (source_id, channel, start, stop, title, subtitle, "
                    + "description, category, episode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        }

        public void channel(String id, List<String> names) {
            channel.clearBindings();
            channel.bindLong(1, sourceId);
            channel.bindString(2, id);
            channel.bindString(3, android.text.TextUtils.join("\n", names));
            channel.executeInsert();
        }

        public void programme(String ch, long start, long stop, String title, String sub, String desc, String cat, String ep) {
            programme.clearBindings();
            programme.bindLong(1, sourceId);
            programme.bindString(2, ch);
            programme.bindLong(3, start);
            programme.bindLong(4, stop);
            programme.bindString(5, title);
            bind(programme, 6, sub);
            bind(programme, 7, desc);
            bind(programme, 8, cat);
            bind(programme, 9, ep);
            programme.executeInsert();
        }

        public void commit(String status, boolean unzoned) {
            ContentValues v = new ContentValues();
            v.put("refreshed", System.currentTimeMillis());
            v.put("status", status);
            v.put("unzoned", unzoned ? 1 : 0);
            db.update("guide_sources", v, "id = ?", new String[]{String.valueOf(sourceId)});
            db.setTransactionSuccessful();
            done = true;
        }

        @Override
        public void close() {
            channel.close();
            programme.close();
            db.endTransaction();   // rolls back unless commit() ran: the previous guide stays
        }

        boolean committed() {
            return done;
        }
    }

    public Writer writer(long sourceId) {
        return new Writer(sourceId);
    }

    private static void bind(SQLiteStatement s, int i, String v) {
        if (v == null) s.bindNull(i);
        else s.bindString(i, v);
    }

    // ------------------------------------------------------------------ reading

    /** All guide channels, for matching playlist channels. */
    public Guide.Index index() {
        Guide.Index idx = new Guide.Index();
        Cursor c = store.getReadableDatabase().rawQuery("SELECT xmltv_id, names FROM guide_channels", null);
        try {
            while (c.moveToNext()) idx.add(c.getString(0), Arrays.asList(c.getString(1).split("\n")));
        } finally {
            c.close();
        }
        return idx;
    }

    /** Programmes on {@code channel} overlapping [from, to), in time order. */
    public List<Programme> programmes(String channel, long from, long to, int limit) {
        return query("SELECT channel, start, stop, title, subtitle, description, category, episode FROM programmes "
                + "WHERE channel = ? AND stop > ? AND start < ? ORDER BY start LIMIT ?",
                new String[]{channel, String.valueOf(from), String.valueOf(to), String.valueOf(limit)});
    }

    /** Programmes with a category, overlapping [from, to) (for Sports). */
    public List<Programme> categorised(long from, long to, int limit) {
        return query("SELECT channel, start, stop, title, subtitle, description, category, episode FROM programmes "
                + "WHERE category IS NOT NULL AND stop > ? AND start < ? ORDER BY start LIMIT ?",
                new String[]{String.valueOf(from), String.valueOf(to), String.valueOf(limit)});
    }

    /** End of the latest programme in the guide (0 when empty): used to say when the guide runs out. */
    public long lastStop() {
        Cursor c = store.getReadableDatabase().rawQuery("SELECT MAX(stop) FROM programmes", null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    private List<Programme> query(String sql, String[] args) {
        List<Programme> out = new ArrayList<>();
        Cursor c = store.getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                out.add(new Programme(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getString(6), c.getString(7)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------ favourite events

    public boolean isFavouriteEvent(String key) {
        Cursor c = store.getReadableDatabase().rawQuery("SELECT 1 FROM event_favourites WHERE event_key = ?", new String[]{key});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public void setFavouriteEvent(String key, boolean on) {
        if (on) {
            ContentValues v = new ContentValues();
            v.put("event_key", key);
            v.put("added", System.currentTimeMillis());
            store.getWritableDatabase().insertWithOnConflict("event_favourites", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } else {
            store.getWritableDatabase().delete("event_favourites", "event_key = ?", new String[]{key});
        }
    }
}
