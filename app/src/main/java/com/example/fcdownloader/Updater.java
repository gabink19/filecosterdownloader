package com.example.fcdownloader;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pembaruan aplikasi dari halaman Releases GitHub (rilis "build-N" yang dibuat workflow).
 * Alur: cek rilis terbaru -> unduh APK ke cache aplikasi -> periksa paket & versi -> pasang
 * lewat PackageInstaller (Android tetap meminta konfirmasi pengguna). Semua jaringan di thread latar.
 */
final class Updater {

    static final class Info {
        int build = -1;
        String tag, name, notes, apkUrl, pageUrl;
        long size = -1;
    }

    interface Progress {
        void on(long done, long total);
    }

    static final int CONNECT_TIMEOUT_MS = 15000, READ_TIMEOUT_MS = 30000;
    static final long MAX_JSON = 2L * 1024 * 1024, MAX_APK = 300L * 1024 * 1024;
    static final Pattern TAG = Pattern.compile("build-(\\d+)");
    static final Pattern REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

    private Updater() { }

    static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("alamat pembaruan harus https");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "FilecosterDownloader-Updater");
        return c;
    }

    /** Ambil info rilis terbaru. */
    static Info latest(String repo) throws Exception {
        if (repo == null || !REPO.matcher(repo).matches()) throw new IOException("nama repository tidak valid");
        HttpURLConnection c = open("https://api.github.com/repos/" + repo + "/releases/latest");
        String body;
        try {
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code == 404) throw new IOException("belum ada rilis");
            if (code == 403 || code == 429) throw new IOException("batas akses GitHub tercapai, coba lagi nanti");
            if (code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_JSON) throw new IOException("jawaban server terlalu besar");
                    bo.write(buf, 0, n);
                }
                body = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
        JSONObject o = new JSONObject(body);
        Info i = new Info();
        i.tag = o.optString("tag_name", "");
        Matcher m = TAG.matcher(i.tag);
        if (m.find()) i.build = Integer.parseInt(m.group(1));
        i.name = o.optString("name", i.tag);
        i.notes = o.optString("body", "");
        String page = o.optString("html_url", "");
        i.pageUrl = page.startsWith("https://github.com/") ? page : "https://github.com/" + repo + "/releases/latest";
        JSONArray as = o.optJSONArray("assets");
        if (as != null) {
            for (int k = 0; k < as.length(); k++) {
                JSONObject a = as.optJSONObject(k);
                if (a == null) continue;
                String url = a.optString("browser_download_url", "");
                if (a.optString("name", "").toLowerCase().endsWith(".apk") && url.startsWith("https://")) {
                    i.apkUrl = url;
                    i.size = a.optLong("size", -1);
                    break;
                }
            }
        }
        if (i.apkUrl == null) throw new IOException("rilis terbaru tidak berisi file APK");
        return i;
    }

    /** Unduh APK ke folder cache aplikasi (privat, tidak perlu izin penyimpanan). */
    static File download(Context ctx, Info info, AtomicBoolean cancel, Progress p) throws IOException {
        File f = ApkProvider.file(ctx);
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("tidak bisa membuat folder sementara");
        HttpURLConnection c = open(info.apkUrl);
        boolean ok = false;
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            if (!"https".equals(c.getURL().getProtocol())) throw new IOException("unduhan dialihkan ke alamat tidak aman");
            long total = c.getContentLengthLong();
            if (total <= 0) total = info.size;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0, lastReport = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel.get()) throw new IOException("dibatalkan");
                    done += n;
                    if (done > MAX_APK) throw new IOException("file pembaruan terlalu besar");
                    out.write(buf, 0, n);
                    if (done - lastReport >= 256 * 1024) {
                        lastReport = done;
                        p.on(done, total);
                    }
                }
                p.on(done, total);
            }
            ok = true;
            return f;
        } finally {
            c.disconnect();
            if (!ok && f.exists() && !f.delete()) f.deleteOnExit();
        }
    }

    /** Dikembalikan verify() jika kunci tanda tangan APK baru berbeda dengan aplikasi terpasang. */
    static final String KEY_MISMATCH = "Aplikasi yang terpasang ditandatangani dengan kunci lama (build 16 atau "
            + "sebelumnya), jadi Android menolak menimpanya. Hapus aplikasi ini sekali saja, lalu pasang APK "
            + "terbaru dari halaman unduhan. Setelah itu pembaruan dari dalam aplikasi akan berjalan.";

    /** Pastikan APK untuk aplikasi ini, versinya lebih baru, dan kuncinya sama. null = aman dipasang. */
    @SuppressWarnings("deprecation")
    static String verify(Context ctx, File apk) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo arc = pm.getPackageArchiveInfo(apk.getPath(), sigFlags());
            if (arc == null) return "file APK rusak atau tidak lengkap";
            if (!ctx.getPackageName().equals(arc.packageName)) return "APK bukan untuk aplikasi ini";
            PackageInfo cur = pm.getPackageInfo(ctx.getPackageName(), sigFlags());
            long arcCode = Build.VERSION.SDK_INT >= 28 ? arc.getLongVersionCode() : arc.versionCode;
            long curCode = Build.VERSION.SDK_INT >= 28 ? cur.getLongVersionCode() : cur.versionCode;
            if (arcCode <= curCode) return "versi di GitHub tidak lebih baru dari yang terpasang";
            List<byte[]> a = signers(arc), c = signers(cur);
            // Jika kunci APK tidak terbaca (Android lama + APK skema v2), biarkan penginstal Android yang memutuskan.
            if (!a.isEmpty() && !c.isEmpty() && !sameSigner(a, c)) return KEY_MISMATCH;
            return null;
        } catch (Exception e) {
            return "gagal memeriksa APK: " + e.getMessage();
        }
    }

    @SuppressWarnings("deprecation")
    static int sigFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    /** Sertifikat penanda tangan (termasuk riwayat rotasi kunci jika ada). */
    @SuppressWarnings("deprecation")
    static List<byte[]> signers(PackageInfo pi) {
        List<byte[]> out = new ArrayList<>();
        if (pi == null) return out;
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= 28) {
            if (pi.signingInfo != null) {
                sigs = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
            }
        } else {
            sigs = pi.signatures;
        }
        if (sigs != null) for (Signature s : sigs) if (s != null) out.add(s.toByteArray());
        return out;
    }

    static boolean sameSigner(List<byte[]> a, List<byte[]> b) {
        for (byte[] x : a) for (byte[] y : b) if (Arrays.equals(x, y)) return true;
        return false;
    }

    /** Nama pemilik sertifikat aplikasi yang terpasang (mis. "CN=Android Debug,..."), null jika tidak terbaca. */
    static String installedSigner(Context ctx) {
        try {
            PackageInfo cur = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), sigFlags());
            List<byte[]> c = signers(cur);
            if (c.isEmpty()) return null;
            X509Certificate x = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(c.get(c.size() - 1)));
            return x.getSubjectX500Principal().getName();
        } catch (Exception e) {
            return null;
        }
    }

    static boolean installedWithDebugKey(Context ctx) {
        String s = installedSigner(ctx);
        return s != null && s.contains("CN=Android Debug");
    }

    /** Serahkan APK ke penginstal Android. Hasilnya dikirim ke UpdateReceiver. */
    static void install(Context ctx, File apk) throws IOException {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        params.setSize(apk.length());
        int id = pi.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent i = new Intent(ctx, UpdateReceiver.class).setAction(UpdateReceiver.ACTION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(ctx, id, i, flags);
            s.commit(pend.getIntentSender());
            committed = true;
        } finally {
            if (!committed) {
                try { pi.abandonSession(id); } catch (Exception ignored) { }
            }
        }
    }
}
