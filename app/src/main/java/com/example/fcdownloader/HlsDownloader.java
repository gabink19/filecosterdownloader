package com.example.fcdownloader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pengunduh video stream HLS (.m3u8), seperti yang dilakukan IDM:
 * baca playlist, pilih kualitas tertinggi, unduh semua potongan (beberapa sekaligus),
 * buka enkripsi AES-128 bila ada, lalu satukan berurutan menjadi satu file.
 * Hasil: .ts (MPEG-TS) atau .mp4 (fMP4) yang bisa diputar pemutar video biasa.
 * Dijalankan di thread latar belakang, bukan di thread UI.
 */
final class HlsDownloader {

    interface Listener {
        void onProgress(int done, int total, long bytes);

        void onLog(String s);
    }

    static final class Result {
        String location, uri;
        long bytes;
    }

    static final class Seg {
        String url, keyUri;
        byte[] iv;
        long off = -1, len = -1;
    }

    static final int CONNECT_TIMEOUT_MS = 15000, READ_TIMEOUT_MS = 30000;
    static final int THREADS = 4, RETRIES = 3;
    static final long MAX_PLAYLIST = 8L * 1024 * 1024, MAX_SEGMENT = 512L * 1024 * 1024;
    static final Pattern ATTR = Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)");

    final String ua, referer;
    final AtomicBoolean cancel;
    final Listener ls;
    final Map<String, byte[]> keys = new HashMap<>();
    long chosenBandwidth = -1;     // BANDWIDTH varian yang dipilih pickVariant

    HlsDownloader(String ua, String referer, AtomicBoolean cancel, Listener ls) {
        this.ua = ua;
        this.referer = referer;
        this.cancel = cancel;
        this.ls = ls;
    }

    Result run(Context ctx, String url, String baseDir, String sub, String baseName) throws Exception {
        String cur = url;
        String text = new String(fetch(cur, -1, -1, MAX_PLAYLIST), StandardCharsets.UTF_8);
        // playlist utama (master) berisi daftar kualitas: pilih yang tertinggi
        for (int hop = 0; hop < 3 && text.contains("#EXT-X-STREAM-INF"); hop++) {
            String next = pickVariant(text, cur);
            if (next == null) throw new IOException("daftar kualitas pada playlist kosong");
            cur = next;
            text = new String(fetch(cur, -1, -1, MAX_PLAYLIST), StandardCharsets.UTF_8);
        }
        if (!text.trim().startsWith("#EXTM3U") && !text.contains("#EXTINF")) {
            throw new IOException("bukan playlist m3u8 yang valid");
        }

        List<Seg> segs = new ArrayList<>();
        Seg init = null;
        String keyUri = null;
        byte[] keyIv = null;
        long mediaSeq = 0, nextOff = 0, pendLen = -1, pendOff = -1;
        boolean ended = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                try { mediaSeq = Long.parseLong(line.substring(22).trim()); } catch (NumberFormatException ignored) { }
            } else if (line.startsWith("#EXT-X-KEY:")) {
                Map<String, String> a = attrs(line.substring(11));
                String method = a.containsKey("METHOD") ? a.get("METHOD") : "NONE";
                if ("NONE".equals(method)) {
                    keyUri = null;
                    keyIv = null;
                } else if ("AES-128".equals(method) && a.get("URI") != null) {
                    keyUri = resolve(cur, a.get("URI"));
                    keyIv = a.get("IV") == null ? null : hex(a.get("IV"));
                } else {
                    throw new IOException("video dilindungi DRM/enkripsi " + method + ", tidak bisa diunduh");
                }
            } else if (line.startsWith("#EXT-X-MAP:")) {
                Map<String, String> a = attrs(line.substring(11));
                if (a.get("URI") != null) {
                    init = new Seg();
                    init.url = resolve(cur, a.get("URI"));
                    String br = a.get("BYTERANGE");
                    if (br != null) {
                        long[] r = byteRange(br, 0);
                        init.len = r[0];
                        init.off = r[1];
                    }
                }
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                long[] r = byteRange(line.substring(17), nextOff);
                pendLen = r[0];
                pendOff = r[1];
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                ended = true;
            } else if (!line.startsWith("#")) {
                Seg s = new Seg();
                s.url = resolve(cur, line);
                s.keyUri = keyUri;
                if (keyUri != null) s.iv = keyIv != null ? keyIv : seqIv(mediaSeq + segs.size());
                if (pendLen >= 0) {
                    s.len = pendLen;
                    s.off = pendOff;
                    nextOff = pendOff + pendLen;
                }
                pendLen = -1;
                pendOff = -1;
                segs.add(s);
            }
        }
        if (segs.isEmpty()) throw new IOException("playlist tidak berisi potongan video");
        if (!ended) ls.onLog("stream ini siaran langsung: hanya bagian yang sudah tersedia yang diunduh");

        String firstPath = Uri.parse(segs.get(0).url).getPath();
        boolean fmp4 = init != null
                || (firstPath != null && firstPath.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4s|m4v)$"));
        String ext = fmp4 ? ".mp4" : ".ts";
        String mime = fmp4 ? "video/mp4" : "video/mp2t";
        ls.onLog("stream: " + segs.size() + " potongan, disimpan sebagai " + ext);

        Out out = Out.open(ctx, baseDir, sub, baseName + ext, mime);
        boolean okDone = false;
        long bytes = 0;
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            if (init != null) {
                byte[] d = fetchRetry(init.url, init.off, init.len);
                out.os.write(d);
                bytes += d.length;
            }
            ArrayDeque<Future<byte[]>> window = new ArrayDeque<>();
            int next = 0, total = segs.size();
            for (int i = 0; i < total; i++) {
                while (next < total && window.size() < THREADS * 2) {
                    final Seg s = segs.get(next++);
                    window.add(pool.submit(() -> segment(s)));
                }
                if (cancel.get()) throw new IOException("dibatalkan");
                byte[] d;
                try {
                    d = window.poll().get(10, TimeUnit.MINUTES);
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    throw new IOException("potongan " + (i + 1) + " gagal: " + c.getMessage(), c);
                }
                out.os.write(d);
                bytes += d.length;
                ls.onProgress(i + 1, total, bytes);
            }
            out.commit(ctx);
            okDone = true;
        } finally {
            pool.shutdownNow();
            if (!okDone) out.abort(ctx);
        }
        Result r = new Result();
        r.location = out.label;
        r.uri = out.uri != null ? out.uri.toString() : (out.file != null ? Uri.fromFile(out.file).toString() : null);
        r.bytes = bytes;
        return r;
    }

    /**
     * Perkiraan ukuran & durasi stream sebelum diunduh: {byte, milidetik}, -1 jika tidak diketahui.
     * Ukuran = BANDWIDTH kualitas terpilih x durasi; tanpa BANDWIDTH: ukuran potongan pertama x jumlah potongan.
     * Dipanggil di thread latar belakang.
     */
    static long[] estimate(String url, String ua, String referer) {
        long[] out = {-1, -1};
        try {
            HlsDownloader d = new HlsDownloader(ua, referer, new AtomicBoolean(false), new Listener() {
                @Override
                public void onProgress(int done, int total, long bytes) { }

                @Override
                public void onLog(String s) { }
            });
            String cur = url;
            String text = new String(d.fetch(cur, -1, -1, MAX_PLAYLIST), StandardCharsets.UTF_8);
            long bw = -1;
            for (int hop = 0; hop < 3 && text.contains("#EXT-X-STREAM-INF"); hop++) {
                String next = d.pickVariant(text, cur);
                if (next == null) return out;
                bw = d.chosenBandwidth;
                cur = next;
                text = new String(d.fetch(cur, -1, -1, MAX_PLAYLIST), StandardCharsets.UTF_8);
            }
            double secs = 0;
            int count = 0;
            String first = null;
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.startsWith("#EXTINF:")) {
                    String v = line.substring(8);
                    int c = v.indexOf(',');
                    try {
                        secs += Double.parseDouble((c >= 0 ? v.substring(0, c) : v).trim());
                    } catch (NumberFormatException ignored) { }
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    count++;
                    if (first == null) first = resolve(cur, line);
                }
            }
            if (secs > 0) out[1] = (long) (secs * 1000);
            if (bw > 0 && secs > 0) {
                out[0] = (long) (bw / 8.0 * secs);
            } else if (first != null && count > 0) {
                long one = MainActivity.probeSize(first, referer, ua, CookieManager.getInstance().getCookie(first));
                if (one > 0) out[0] = one * count;
            }
        } catch (Exception ignored) {
            // ukuran tidak diketahui: tetap bisa diunduh
        }
        return out;
    }

    /**
     * Ambil 1 potongan awal stream m3u8 (dekripsi jika AES-128) untuk membuat thumbnail pratinjau.
     */
    static byte[] sampleSegment(String url, String ua, String referer) {
        try {
            HlsDownloader d = new HlsDownloader(ua, referer, new AtomicBoolean(false), new Listener() {
                @Override
                public void onProgress(int done, int total, long bytes) { }

                @Override
                public void onLog(String s) { }
            });
            String cur = url;
            String text = new String(d.fetch(cur, -1, -1, MAX_PLAYLIST), StandardCharsets.UTF_8);
            for (int hop = 0; hop < 3 && text.contains("#EXT-X-STREAM-INF"); hop++) {
                String next = d.pickVariant(text, cur);
                if (next == null) return null;
                cur = next;
                text = new String(d.fetch(cur, -1, -1, MAX_PLAYLIST), StandardCharsets.UTF_8);
            }
            String keyUri = null;
            byte[] keyIv = null;
            long mediaSeq = 0;
            Seg init = null;
            List<Seg> segs = new ArrayList<>();
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    try { mediaSeq = Long.parseLong(line.substring(22).trim()); } catch (NumberFormatException ignored) { }
                } else if (line.startsWith("#EXT-X-KEY:")) {
                    Map<String, String> a = attrs(line.substring(11));
                    String method = a.containsKey("METHOD") ? a.get("METHOD") : "NONE";
                    if ("AES-128".equals(method) && a.get("URI") != null) {
                        keyUri = resolve(cur, a.get("URI"));
                        keyIv = a.get("IV") == null ? null : hex(a.get("IV"));
                    } else if ("NONE".equals(method)) {
                        keyUri = null;
                        keyIv = null;
                    }
                } else if (line.startsWith("#EXT-X-MAP:")) {
                    Map<String, String> a = attrs(line.substring(11));
                    if (a.get("URI") != null) {
                        init = new Seg();
                        init.url = resolve(cur, a.get("URI"));
                    }
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    Seg s = new Seg();
                    s.url = resolve(cur, line);
                    s.keyUri = keyUri;
                    if (keyUri != null) s.iv = keyIv != null ? keyIv : seqIv(mediaSeq + segs.size());
                    segs.add(s);
                    if (segs.size() >= 2) break;
                }
            }
            if (segs.isEmpty()) return null;
            Seg target = segs.get(segs.size() > 1 ? 1 : 0);
            byte[] segBytes = d.segment(target);
            if (init != null) {
                byte[] initBytes = d.fetchRetry(init.url, -1, -1);
                byte[] combined = new byte[initBytes.length + segBytes.length];
                System.arraycopy(initBytes, 0, combined, 0, initBytes.length);
                System.arraycopy(segBytes, 0, combined, initBytes.length, segBytes.length);
                return combined;
            }
            return segBytes;
        } catch (Exception ignored) {
            return null;
        }
    }
    byte[] segment(Seg s) throws Exception {
        byte[] d = fetchRetry(s.url, s.off, s.len);
        if (s.keyUri == null) return d;
        byte[] key = key(s.keyUri);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(s.iv));
        return c.doFinal(d);
    }

    byte[] key(String uri) throws Exception {
        synchronized (keys) {
            byte[] k = keys.get(uri);
            if (k != null) return k;
        }
        byte[] k = fetchRetry(uri, -1, -1);
        if (k.length != 16) throw new IOException("kunci enkripsi tidak valid (" + k.length + " byte)");
        synchronized (keys) {
            keys.put(uri, k);
        }
        return k;
    }

    byte[] fetchRetry(String url, long off, long len) throws Exception {
        IOException last = null;
        for (int a = 0; a < RETRIES; a++) {
            if (cancel.get()) throw new IOException("dibatalkan");
            try {
                return fetch(url, off, len, MAX_SEGMENT);
            } catch (IOException e) {
                last = e;
                Thread.sleep(1000L * (a + 1));
            }
        }
        throw last != null ? last : new IOException("gagal mengambil " + url);
    }

    byte[] fetch(String url, long off, long len, long cap) throws IOException {
        URL u = new URL(url);
        String proto = u.getProtocol();
        if (!"http".equals(proto) && !"https".equals(proto)) throw new IOException("alamat bukan http/https");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            if (ua != null) c.setRequestProperty("User-Agent", ua);
            if (referer != null) c.setRequestProperty("Referer", referer);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (off >= 0 && len > 0) c.setRequestProperty("Range", "bytes=" + off + "-" + (off + len - 1));
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel.get()) throw new IOException("dibatalkan");
                    total += n;
                    if (total > cap) throw new IOException("data terlalu besar");
                    bo.write(buf, 0, n);
                }
                return bo.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** Pilih varian dengan BANDWIDTH tertinggi dari playlist master. */
    String pickVariant(String text, String base) {
        String best = null;
        long bestBw = -1;
        long pendBw = -2;
        boolean audioSplit = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-MEDIA:") && line.contains("TYPE=AUDIO") && line.contains("URI=")) {
                audioSplit = true;
            } else if (line.startsWith("#EXT-X-STREAM-INF:")) {
                Map<String, String> a = attrs(line.substring(18));
                try {
                    pendBw = Long.parseLong(a.containsKey("BANDWIDTH") ? a.get("BANDWIDTH") : "0");
                } catch (NumberFormatException e) {
                    pendBw = 0;
                }
            } else if (pendBw > -2 && !line.isEmpty() && !line.startsWith("#")) {
                if (pendBw > bestBw) {
                    bestBw = pendBw;
                    best = resolve(base, line);
                }
                pendBw = -2;
            }
        }
        if (audioSplit) ls.onLog("perhatian: audio stream ini terpisah, hasil unduhan mungkin tanpa suara");
        chosenBandwidth = bestBw;
        if (best != null) ls.onLog("kualitas dipilih: " + (bestBw > 0 ? (bestBw / 1000) + " kbps" : "default"));
        return best;
    }

    static Map<String, String> attrs(String s) {
        Map<String, String> m = new HashMap<>();
        Matcher x = ATTR.matcher(s);
        while (x.find()) {
            String v = x.group(2);
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            m.put(x.group(1), v);
        }
        return m;
    }

    static long[] byteRange(String s, long defOff) {
        String[] p = s.trim().split("@");
        long len = Long.parseLong(p[0].trim());
        long off = p.length > 1 ? Long.parseLong(p[1].trim()) : defOff;
        return new long[]{len, off};
    }

    static String resolve(String base, String ref) {
        try {
            return new URL(new URL(base), ref).toString();
        } catch (Exception e) {
            return ref;
        }
    }

    static byte[] hex(String s) {
        String h = s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
        if (h.length() % 2 == 1) h = "0" + h;
        byte[] raw = new byte[h.length() / 2];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        byte[] out = new byte[16];
        int n = Math.min(16, raw.length);
        System.arraycopy(raw, raw.length - n, out, 16 - n, n);
        return out;
    }

    static byte[] seqIv(long seq) {
        byte[] iv = new byte[16];
        for (int i = 15; i >= 8; i--) {
            iv[i] = (byte) (seq & 0xff);
            seq >>>= 8;
        }
        return iv;
    }

    /** Tujuan file: MediaStore (Android 10+) atau file biasa (Android 9 ke bawah). */
    static final class Out {
        OutputStream os;
        Uri uri;
        File file;
        String label;

        static Out open(Context ctx, String baseDir, String sub, String name, String mime) throws IOException {
            Out o = new Out();
            String rel = baseDir + (sub == null || sub.isEmpty() ? "" : "/" + sub);
            o.label = rel + "/" + name;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = ctx.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri coll = Environment.DIRECTORY_DOWNLOADS.equals(baseDir)
                        ? MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                o.uri = cr.insert(coll, cv);
                if (o.uri == null) throw new IOException("tidak bisa membuat file di " + rel);
                o.os = cr.openOutputStream(o.uri);
                if (o.os == null) {
                    cr.delete(o.uri, null, null);
                    throw new IOException("tidak bisa menulis file di " + rel);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(baseDir),
                        sub == null ? "" : sub);
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("tidak bisa membuat folder " + rel);
                File f = new File(dir, name);
                int dot = name.lastIndexOf('.');
                for (int k = 1; f.exists() && k < 1000; k++) {
                    f = new File(dir, name.substring(0, dot) + " (" + k + ")" + name.substring(dot));
                }
                o.file = f;
                o.label = rel + "/" + f.getName();
                o.os = new FileOutputStream(f);
            }
            return o;
        }

        void commit(Context ctx) throws IOException {
            os.close();
            if (uri != null) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, cv, null, null);
            } else if (file != null) {
                MediaScannerConnection.scanFile(ctx, new String[]{file.getAbsolutePath()}, null, null);
            }
        }

        /** Unduhan gagal/dibatalkan: hapus file setengah jadi. */
        void abort(Context ctx) {
            try { os.close(); } catch (Exception ignored) { }
            try {
                if (uri != null) ctx.getContentResolver().delete(uri, null, null);
                else if (file != null && !file.delete()) file.deleteOnExit();
            } catch (Exception ignored) { }
        }
    }
}
