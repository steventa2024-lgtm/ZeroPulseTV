package tv.calmhome.launcher.streaming;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.core.M3uParser;
import tv.calmhome.launcher.data.ModuleData;

/**
 * ZeroPulse Streaming's own database (zp_streaming.db): the user's sources and the channels read from them, plus
 * favourites, recently watched and playback positions. Favourites, recents and positions are keyed by stream
 * URL, so they survive a source refresh. Never touches launcher data. Schema changes are additive
 * (new tables or columns in onUpgrade) and idempotent, so upgrade → downgrade → upgrade never fails.
 *
 * Version 1: sources, channels, favourites, recents, progress.
 * Version 2: sources.guide_url; XMLTV guide (guide_sources, guide_channels, programmes); event_favourites.
 */
public final class StreamingStore extends SQLiteOpenHelper {

    static final int VERSION = 2;

    public static final class Source {
        public final long id;
        public final String kind, location, title, status;
        public final long refreshed;
        public final int channels;
        /** HTTPS guide named by the playlist, or null. */
        public String guideUrl;

        Source(long id, String kind, String location, String title, long refreshed, String status, int channels) {
            this.id = id;
            this.kind = kind;
            this.location = location;
            this.title = title;
            this.refreshed = refreshed;
            this.status = status;
            this.channels = channels;
        }
    }

    public static final class Channel implements tv.calmhome.launcher.core.ChannelFilter.Item {
        public final long id, sourceId;
        public final String name, url, tvgId, tvgName, logo, group;

        Channel(long id, long sourceId, String name, String url, String tvgId, String tvgName, String logo, String group) {
            this.id = id;
            this.sourceId = sourceId;
            this.name = name;
            this.url = url;
            this.tvgId = tvgId;
            this.tvgName = tvgName;
            this.logo = logo;
            this.group = group;
        }

        @Override public String name() { return name; }
        @Override public String group() { return group; }
        @Override public String url() { return url; }
    }

    /** "url" = an HTTPS playlist link; "file" = a playlist document chosen with the system picker. */
    public static final String KIND_URL = "url", KIND_FILE = "file";

    public StreamingStore(Context c) {
        super(c, ModuleData.STREAMING_DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sources (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, location TEXT NOT NULL UNIQUE, "
                + "title TEXT NOT NULL, added INTEGER NOT NULL, refreshed INTEGER NOT NULL DEFAULT 0, status TEXT)");
        db.execSQL("CREATE TABLE channels (id INTEGER PRIMARY KEY, source_id INTEGER NOT NULL REFERENCES sources(id) "
                + "ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, tvg_id TEXT, "
                + "tvg_name TEXT, logo TEXT, grp TEXT, UNIQUE (source_id, url))");
        db.execSQL("CREATE INDEX channels_by_source ON channels (source_id, position)");
        db.execSQL("CREATE TABLE favourites (url TEXT PRIMARY KEY, added INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE recents (url TEXT PRIMARY KEY, name TEXT NOT NULL, watched INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE progress (url TEXT PRIMARY KEY, position_ms INTEGER NOT NULL, "
                + "duration_ms INTEGER NOT NULL, updated INTEGER NOT NULL)");
        onUpgrade(db, 1, VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if (from < 2) {
            if (!hasColumn(db, "sources", "guide_url")) db.execSQL("ALTER TABLE sources ADD COLUMN guide_url TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS guide_sources (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, "
                    + "location TEXT NOT NULL UNIQUE, title TEXT NOT NULL, added INTEGER NOT NULL, "
                    + "refreshed INTEGER NOT NULL DEFAULT 0, status TEXT, unzoned INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE IF NOT EXISTS guide_channels (source_id INTEGER NOT NULL REFERENCES "
                    + "guide_sources(id) ON DELETE CASCADE, xmltv_id TEXT NOT NULL, names TEXT NOT NULL, "
                    + "PRIMARY KEY (source_id, xmltv_id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS programmes (source_id INTEGER NOT NULL REFERENCES guide_sources(id) "
                    + "ON DELETE CASCADE, channel TEXT NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL, "
                    + "title TEXT NOT NULL, subtitle TEXT, description TEXT, category TEXT, episode TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS programmes_by_channel ON programmes (channel, start)");
            db.execSQL("CREATE INDEX IF NOT EXISTS programmes_by_time ON programmes (start)");
            db.execSQL("CREATE TABLE IF NOT EXISTS event_favourites (event_key TEXT PRIMARY KEY, added INTEGER NOT NULL)");
        }
    }

    static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (c.moveToNext()) if (column.equals(c.getString(1))) return true;
            return false;
        } finally {
            c.close();
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int from, int to) {
        // An older build opening a newer file keeps it as is (no crash, no data loss): newer tables and columns are
        // simply unused by the older code.
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ------------------------------------------------------------------ sources

    /** Adds a source, or returns the existing one with the same location. */
    public long addSource(String kind, String location, String title) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM sources WHERE location = ?", new String[]{location});
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
        return db.insertOrThrow("sources", null, v);
    }

    /** Replaces a source's channels with a fresh read, in one transaction. */
    public void replaceChannels(long sourceId, List<M3uParser.Entry> entries, String status) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("channels", "source_id = ?", new String[]{String.valueOf(sourceId)});
            int pos = 0;
            for (M3uParser.Entry e : entries) {
                ContentValues v = new ContentValues();
                v.put("source_id", sourceId);
                v.put("position", pos++);
                v.put("name", e.name);
                v.put("url", e.url);
                v.put("tvg_id", e.tvgId);
                v.put("tvg_name", e.tvgName);
                v.put("logo", e.logo);
                v.put("grp", e.group);
                db.insertWithOnConflict("channels", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            ContentValues s = new ContentValues();
            s.put("refreshed", System.currentTimeMillis());
            s.put("status", status);
            db.update("sources", s, "id = ?", new String[]{String.valueOf(sourceId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void setGuideUrl(long sourceId, String url) {
        ContentValues s = new ContentValues();
        s.put("guide_url", url);
        getWritableDatabase().update("sources", s, "id = ?", new String[]{String.valueOf(sourceId)});
    }

    public void setStatus(long sourceId, String status) {
        ContentValues s = new ContentValues();
        s.put("status", status);
        getWritableDatabase().update("sources", s, "id = ?", new String[]{String.valueOf(sourceId)});
    }

    public void removeSource(long sourceId) {
        getWritableDatabase().delete("sources", "id = ?", new String[]{String.valueOf(sourceId)});
    }

    public List<Source> sources() {
        List<Source> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT s.id, s.kind, s.location, s.title, s.refreshed, s.status, "
                + "(SELECT COUNT(*) FROM channels ch WHERE ch.source_id = s.id), s.guide_url FROM sources s ORDER BY s.added", null);
        try {
            while (c.moveToNext()) {
                Source src = new Source(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                        c.getString(5), c.getInt(6));
                src.guideUrl = c.getString(7);
                out.add(src);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------ channels

    /** All channels in source order then playlist order. */
    public List<Channel> channels() {
        return queryChannels("SELECT ch.id, ch.source_id, ch.name, ch.url, ch.tvg_id, ch.tvg_name, ch.logo, ch.grp FROM channels ch "
                + "JOIN sources s ON s.id = ch.source_id ORDER BY s.added, ch.position", null);
    }

    public Channel channel(long id) {
        List<Channel> l = queryChannels("SELECT id, source_id, name, url, tvg_id, tvg_name, logo, grp FROM channels WHERE id = ?",
                new String[]{String.valueOf(id)});
        return l.isEmpty() ? null : l.get(0);
    }

    private List<Channel> queryChannels(String sql, String[] args) {
        List<Channel> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                out.add(new Channel(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getString(6), c.getString(7)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------ favourites, recents, progress

    public boolean isFavourite(String url) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM favourites WHERE url = ?", new String[]{url});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public java.util.Set<String> favouriteUrls() {
        java.util.Set<String> out = new java.util.HashSet<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT url FROM favourites", null);
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally {
            c.close();
        }
        return out;
    }

    public void setFavourite(String url, boolean on) {
        if (on) {
            ContentValues v = new ContentValues();
            v.put("url", url);
            v.put("added", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("favourites", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } else {
            getWritableDatabase().delete("favourites", "url = ?", new String[]{url});
        }
    }

    public void markWatched(String url, String name) {
        ContentValues v = new ContentValues();
        v.put("url", url);
        v.put("name", name);
        v.put("watched", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("recents", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Stream URLs, most recently watched first. */
    public List<String> recentUrls(int limit) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT url FROM recents ORDER BY watched DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally {
            c.close();
        }
        return out;
    }

    /** Within this much of the end (or past {@link #FINISHED_FRACTION}) a video counts as watched to the end. */
    static final long FINISHED_MS = 30_000;
    static final double FINISHED_FRACTION = 0.95;

    /**
     * Saves where to resume. A position at the very end is saved as 0 (start again next time): on the TCL a film
     * left playing ran to its end and then reopened at "Ended 12:14 / 12:14".
     */
    public void saveProgress(String url, long positionMs, long durationMs) {
        ContentValues v = new ContentValues();
        v.put("url", url);
        v.put("position_ms", finished(positionMs, durationMs) ? 0 : positionMs);
        v.put("duration_ms", durationMs);
        v.put("updated", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    static boolean finished(long positionMs, long durationMs) {
        return durationMs > 0 && (durationMs - positionMs < FINISHED_MS || positionMs >= durationMs * FINISHED_FRACTION);
    }

    /** Saved position, or 0 (also for one saved at the end before this rule existed). */
    public long progress(String url) {
        Cursor c = getReadableDatabase().rawQuery("SELECT position_ms, duration_ms FROM progress WHERE url = ?", new String[]{url});
        try {
            return c.moveToFirst() && !finished(c.getLong(0), c.getLong(1)) ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }
}
