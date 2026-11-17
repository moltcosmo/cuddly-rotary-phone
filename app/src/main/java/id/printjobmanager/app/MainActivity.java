package id.printjobmanager.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private JobDb db;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new JobDb();
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private class Bridge {
        @JavascriptInterface public String listJobs() {
            JSONArray arr = new JSONArray();
            SQLiteDatabase r = db.getReadableDatabase();
            Cursor c = r.rawQuery("SELECT id, customer, title, qty, stage, status, created_at, updated_at FROM jobs ORDER BY id DESC", null);
            try {
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0)); o.put("customer", c.getString(1));
                    o.put("title", c.getString(2)); o.put("qty", c.getInt(3));
                    o.put("stage", c.getString(4)); o.put("status", c.getString(5));
                    o.put("created_at", c.getString(6)); o.put("updated_at", c.getString(7));
                    arr.put(o);
                }
            } catch (Exception ignored) { }
            finally { c.close(); }
            return arr.toString();
        }

        @JavascriptInterface public long createJob(String customer, String title, int qty) {
            ContentValues v = new ContentValues();
            v.put("customer", customer); v.put("title", title); v.put("qty", Math.max(1, qty));
            v.put("stage", "Artwork"); v.put("status", "In Progress");
            v.put("created_at", now()); v.put("updated_at", now());
            return db.getWritableDatabase().insert("jobs", null, v);
        }

        @JavascriptInterface public boolean advanceJob(long id) {
            String[] stages = {"Artwork", "Internal QC", "Customer Approval", "Printing", "Finishing", "Completed"};
            SQLiteDatabase w = db.getWritableDatabase();
            Cursor c = w.rawQuery("SELECT stage FROM jobs WHERE id=?", new String[]{String.valueOf(id)});
            if (!c.moveToFirst()) { c.close(); return false; }
            String current = c.getString(0); c.close();
            int index = 0;
            for (int i=0;i<stages.length;i++) if (stages[i].equals(current)) index=i;
            if (index >= stages.length-1) return false;
            ContentValues v = new ContentValues();
            v.put("stage", stages[index+1]); v.put("updated_at", now());
            if (index+1 == stages.length-1) v.put("status", "Completed");
            w.update("jobs", v, "id=?", new String[]{String.valueOf(id)});
            return true;
        }

        @JavascriptInterface public boolean rejectApproval(long id) {
            SQLiteDatabase w = db.getWritableDatabase();
            Cursor c = w.rawQuery("SELECT stage FROM jobs WHERE id=?", new String[]{String.valueOf(id)});
            if (!c.moveToFirst()) { c.close(); return false; }
            String stage = c.getString(0); c.close();
            if (!"Customer Approval".equals(stage)) return false;
            ContentValues v = new ContentValues();
            v.put("stage", "Artwork Revision"); v.put("updated_at", now());
            w.update("jobs", v, "id=?", new String[]{String.valueOf(id)});
            return true;
        }

        @JavascriptInterface public boolean resubmitApproval(long id) {
            SQLiteDatabase w = db.getWritableDatabase();
            Cursor c = w.rawQuery("SELECT stage FROM jobs WHERE id=?", new String[]{String.valueOf(id)});
            if (!c.moveToFirst()) { c.close(); return false; }
            String stage = c.getString(0); c.close();
            if (!"Artwork Revision".equals(stage)) return false;
            ContentValues v = new ContentValues();
            v.put("stage", "Internal QC"); v.put("updated_at", now());
            w.update("jobs", v, "id=?", new String[]{String.valueOf(id)});
            return true;
        }
    }

    private class JobDb extends SQLiteOpenHelper {
        JobDb() { super(MainActivity.this, "print_jobs.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE jobs (id INTEGER PRIMARY KEY AUTOINCREMENT, customer TEXT NOT NULL, title TEXT NOT NULL, qty INTEGER NOT NULL, stage TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase d, int oldV, int newV) { }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
