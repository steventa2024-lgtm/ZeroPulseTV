package tv.calmhome.launcher.downloads;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class DownloadStore extends SQLiteOpenHelper {
    DownloadStore(Context context) { super(context, "downloads.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, state TEXT NOT NULL, url TEXT NOT NULL," +
                " finalUrl TEXT NOT NULL, fileName TEXT NOT NULL, expected TEXT NOT NULL, sha TEXT NOT NULL DEFAULT ''," +
                " bytes INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT -1, pkg TEXT NOT NULL DEFAULT ''," +
                " versionName TEXT NOT NULL DEFAULT '', versionCode INTEGER NOT NULL DEFAULT 0, minSdk INTEGER NOT NULL DEFAULT 0," +
                " permissions TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '', appName TEXT NOT NULL DEFAULT ''," +
                " sessionId INTEGER NOT NULL DEFAULT -1, attempt INTEGER NOT NULL DEFAULT 0, inspected INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported downloads database version.");
    }

    long insert(String url, String expected, String name) {
        ContentValues values = new ContentValues();
        values.put("state", "QUEUED");
        values.put("url", url);
        values.put("finalUrl", url);
        values.put("fileName", name);
        values.put("expected", expected);
        return getWritableDatabase().insertOrThrow("downloads", null, values);
    }

    DownloadRecord find(long id) {
        try (Cursor c = getReadableDatabase().query("downloads", null, "id=?", args(id), null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    List<DownloadRecord> list() {
        List<DownloadRecord> found = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("downloads", null, null, null, null, null, "id DESC")) {
            while (c.moveToNext()) found.add(read(c));
        }
        return found;
    }

    void save(DownloadRecord r) {
        ContentValues v = new ContentValues();
        v.put("state", r.state.name()); v.put("bytes", r.bytes); v.put("total", r.total);
        v.put("finalUrl", r.finalUrl); v.put("fileName", r.fileName); v.put("sha", r.sha256);
        v.put("pkg", r.packageName); v.put("versionName", r.versionName); v.put("versionCode", r.versionCode);
        v.put("minSdk", r.minSdk); v.put("permissions", r.permissions); v.put("error", r.error);
        v.put("inspected", r.inspected ? 1 : 0); v.put("sessionId", r.sessionId); v.put("attempt", r.attempt); v.put("appName", r.appName);
        getWritableDatabase().update("downloads", v, "id=?", args(r.id));
    }

    void remove(long id) { getWritableDatabase().delete("downloads", "id=?", args(id)); }
    private static String[] args(long id) { return new String[]{Long.toString(id)}; }

    private static DownloadRecord read(Cursor c) {
        DownloadRecord r = new DownloadRecord();
        r.id = c.getLong(0);
        r.state = DownloadRecord.State.valueOf(c.getString(1));
        r.url = c.getString(2); r.finalUrl = c.getString(3); r.fileName = c.getString(4);
        r.expectedSha256 = c.getString(5); r.sha256 = c.getString(6);
        r.bytes = c.getLong(7); r.total = c.getLong(8); r.packageName = c.getString(9);
        r.versionName = c.getString(10); r.versionCode = c.getLong(11); r.minSdk = c.getInt(12);
        r.permissions = c.getString(13); r.error = c.getString(14); r.appName = c.getString(15);
        r.sessionId = c.getInt(16); r.attempt = c.getLong(17); r.inspected = c.getInt(18) != 0;
        return r;
    }
}
