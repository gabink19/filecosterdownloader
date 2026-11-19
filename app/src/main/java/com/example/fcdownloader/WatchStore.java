package com.example.fcdownloader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

/**
 * Status tonton per video (seperti VLC): posisi terakhir, durasi, sudah selesai atau belum,
 * dan kapan terakhir diputar. Dipakai pemutar (melanjutkan) dan tab Pemutar (penanda).
 */
final class WatchStore {

    static final long MIN_RESUME_MS = 5000;
    static final int MAX_ENTRIES = 1000;

    static final class Entry {
        long pos, dur, time;
        boolean done;

        /** 0..100, atau -1 jika durasi belum diketahui. */
        int percent() {
            if (done) return 100;
            return dur > 0 ? (int) Math.min(100, pos * 100 / dur) : -1;
        }

        boolean resumable() {
            return !done && pos > MIN_RESUME_MS && (dur <= 0 || pos < dur - MIN_RESUME_MS);
        }
    }

    private WatchStore() { }

    static SharedPreferences store(Context c) {
        return c.getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    static Entry get(Context c, String uri) {
        if (uri == null) return null;
        String s = store(c).getString(uri, null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Entry e = new Entry();
            e.pos = o.optLong("pos");
            e.dur = o.optLong("dur");
            e.time = o.optLong("t");
            e.done = o.optBoolean("done");
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Simpan posisi. Dianggap selesai jika sisa kurang dari 5 detik atau 3% durasi. */
    static void save(Context c, String uri, long pos, long dur) {
        if (uri == null || pos < 0) return;
        boolean done = dur > 0 && pos >= dur - Math.max(MIN_RESUME_MS, dur * 3 / 100);
        put(c, uri, done ? 0 : pos, dur, done);
    }

    static void setDone(Context c, String uri, boolean done) {
        Entry e = get(c, uri);
        if (done) put(c, uri, 0, e == null ? 0 : e.dur, true);
        else store(c).edit().remove(uri).apply();
    }

    static void put(Context c, String uri, long pos, long dur, boolean done) {
        try {
            JSONObject o = new JSONObject();
            o.put("pos", pos);
            o.put("dur", dur);
            o.put("done", done);
            o.put("t", System.currentTimeMillis());
            SharedPreferences sp = store(c);
            SharedPreferences.Editor ed = sp.edit().putString(uri, o.toString());
            Map<String, ?> all = sp.getAll();
            if (all.size() > MAX_ENTRIES) {          // buang catatan paling lama
                String oldest = null;
                long ot = Long.MAX_VALUE;
                for (Map.Entry<String, ?> en : all.entrySet()) {
                    try {
                        long t = new JSONObject(String.valueOf(en.getValue())).optLong("t");
                        if (t < ot) {
                            ot = t;
                            oldest = en.getKey();
                        }
                    } catch (Exception ignored) { }
                }
                if (oldest != null && !oldest.equals(uri)) ed.remove(oldest);
            }
            ed.apply();
        } catch (Exception ignored) { }
    }
}
