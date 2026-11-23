package com.taala.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local on-device database (the native counterpart of the browser IndexedDB). */
public class Db extends SQLiteOpenHelper {
    public Db(Context c) {
        super(c, "taala.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE p (id TEXT PRIMARY KEY, created INTEGER, data TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int a, int b) { }

    public List<Project> all() {
        List<Project> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT data FROM p ORDER BY created DESC", null)) {
            while (c.moveToNext()) {
                try { out.add(Project.fromJson(new JSONObject(c.getString(0)))); } catch (Exception ignored) { }
            }
        }
        return out;
    }

    public Project get(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT data FROM p WHERE id=?", new String[]{id})) {
            if (c.moveToFirst()) return Project.fromJson(new JSONObject(c.getString(0)));
        } catch (Exception ignored) { }
        return null;
    }

    public void put(Project p) {
        try {
            ContentValues v = new ContentValues();
            v.put("id", p.id);
            v.put("created", p.created);
            v.put("data", p.toJson().toString());
            getWritableDatabase().insertWithOnConflict("p", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(String id) {
        getWritableDatabase().delete("p", "id=?", new String[]{id});
    }
}
