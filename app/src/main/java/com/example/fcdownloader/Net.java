package com.example.fcdownloader;

import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * Jaringan WebView lewat DNS Google (8.8.8.8 / 8.8.4.4).
 * WebView Android tidak bisa diberi DNS sendiri, jadi permintaan GET/HEAD dari halaman diambil alih
 * (shouldInterceptRequest) dan dikirim lewat OkHttp yang memakai DNS-over-HTTPS Google.
 * DoH dipakai, bukan DNS biasa port 53, karena banyak ISP membelokkan port 53 meski tujuannya 8.8.8.8.
 * Permintaan lain (POST, dsb.) dan kegagalan apa pun dikembalikan ke jaringan bawaan WebView.
 */
final class Net {

    static final String DOH_URL = "https://dns.google/dns-query";
    static final String[] BOOTSTRAP = {"8.8.8.8", "8.8.4.4"};
    static final long DNS_CACHE_MS = 5 * 60 * 1000;

    /** Diatur dari Pengaturan ("Pakai DNS Google"). */
    static volatile boolean enabled = true;

    private static OkHttpClient client;

    private Net() { }

    static synchronized OkHttpClient client() throws UnknownHostException {
        if (client == null) {
            OkHttpClient base = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
            List<InetAddress> boot = new ArrayList<>();
            for (String ip : BOOTSTRAP) boot.add(InetAddress.getByName(ip));   // alamat IP: tanpa lookup DNS
            DnsOverHttps doh = new DnsOverHttps.Builder()
                    .client(base)
                    .url(HttpUrl.get(DOH_URL))
                    .bootstrapDnsHosts(boot)
                    .build();
            client = base.newBuilder()
                    .dns(new CachingDns(doh))
                    .cookieJar(new WebViewCookieJar())
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
        }
        return client;
    }

    /** Jawaban DNS disimpan 5 menit agar tiap gambar/skrip tidak memicu permintaan DNS baru. */
    static final class CachingDns implements Dns {
        final Dns inner;
        final Map<String, Object[]> cache = new ConcurrentHashMap<>();   // host -> {List<InetAddress>, kedaluwarsa}

        CachingDns(Dns inner) {
            this.inner = inner;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<InetAddress> lookup(String host) throws UnknownHostException {
            Object[] e = cache.get(host);
            long now = System.currentTimeMillis();
            if (e != null && (Long) e[1] > now) return (List<InetAddress>) e[0];
            List<InetAddress> r = inner.lookup(host);
            if (cache.size() > 500) cache.clear();
            cache.put(host, new Object[]{r, now + DNS_CACHE_MS});
            return r;
        }
    }

    /** Cookie dibagi dengan WebView, jadi login di halaman tetap berlaku. */
    static final class WebViewCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            CookieManager cm = CookieManager.getInstance();
            for (Cookie c : cookies) cm.setCookie(url.toString(), c.toString());
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            String s = CookieManager.getInstance().getCookie(url.toString());
            if (s == null || s.isEmpty()) return Collections.emptyList();
            List<Cookie> out = new ArrayList<>();
            for (String part : s.split(";")) {
                Cookie c = Cookie.parse(url, part.trim());
                if (c != null) out.add(c);
            }
            return out;
        }
    }

    /**
     * Ambil satu permintaan WebView lewat DNS Google. null = biarkan WebView memakai jaringan bawaannya.
     * Dipanggil di thread jaringan WebView (bukan thread UI).
     */
    static WebResourceResponse fetch(WebResourceRequest req) {
        if (!enabled || req == null) return null;
        String method = req.getMethod() == null ? "GET" : req.getMethod().toUpperCase(Locale.ROOT);
        if (!method.equals("GET") && !method.equals("HEAD")) return null;   // isi POST tidak bisa dibaca dari WebView
        Uri u = req.getUrl();
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        Response r = null;
        try {
            Request.Builder rb = new Request.Builder().url(u.toString()).method(method, null);
            Map<String, String> hs = req.getRequestHeaders();
            if (hs != null) {
                for (Map.Entry<String, String> e : hs.entrySet()) {
                    String k = e.getKey();
                    if (k == null || e.getValue() == null) continue;
                    String lk = k.toLowerCase(Locale.ROOT);
                    // gzip dibuka OkHttp sendiri; header kondisional bisa menghasilkan 304 yang tidak boleh dikembalikan
                    if (lk.equals("accept-encoding") || lk.equals("if-none-match") || lk.equals("if-modified-since")
                            || lk.equals("cookie") || lk.equals("content-length")) continue;
                    rb.header(k, e.getValue());
                }
            }
            r = client().newCall(rb.build()).execute();
            if (req.isForMainFrame() && r.priorResponse() != null) {
                // Halaman utama dialihkan: WebView harus pindah ke alamat akhir agar link relatif benar.
                String target = r.request().url().toString();
                r.close();
                return redirectPage(target);
            }
            int code = r.code();
            if (code >= 300 && code < 400) {      // WebResourceResponse tidak menerima kode 3xx
                r.close();
                return null;
            }
            ResponseBody body = r.body();
            if (body == null) {
                r.close();
                return null;
            }
            MediaType mt = body.contentType();
            String mime = mt == null ? "application/octet-stream" : mt.type() + "/" + mt.subtype();
            String charset = mt != null && mt.charset() != null ? mt.charset().name()
                    : (mime.startsWith("text/") ? "utf-8" : null);
            Map<String, String> out = new HashMap<>();
            for (String name : r.headers().names()) {
                String ln = name.toLowerCase(Locale.ROOT);
                if (ln.equals("set-cookie") || ln.equals("set-cookie2") || ln.equals("connection")
                        || ln.equals("keep-alive") || ln.equals("transfer-encoding")) continue;
                out.put(name, String.join(", ", r.headers(name)));
            }
            String reason = r.message() == null || r.message().trim().isEmpty() ? "OK" : r.message();
            return new WebResourceResponse(mime, charset, code, reason, out, body.byteStream());
        } catch (Exception e) {
            if (r != null) r.close();
            return null;          // gagal lewat DNS Google: jatuh kembali ke jaringan bawaan
        }
    }

    static WebResourceResponse redirectPage(String target) {
        String html = "<!doctype html><meta charset=\"utf-8\"><script>location.replace("
                + JSONObject.quote(target) + ");</script>";
        return new WebResourceResponse("text/html", "utf-8",
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
    }
}
