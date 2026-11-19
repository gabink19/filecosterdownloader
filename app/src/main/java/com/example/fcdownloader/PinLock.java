package com.example.fcdownloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PIN 6 digit untuk membuka aplikasi. PIN tidak disimpan apa adanya: yang disimpan hanya
 * hash PBKDF2 + salt acak, di penyimpanan privat aplikasi.
 */
final class PinLock {

    static final int LEN = 6;
    static final int ITERATIONS = 20000;
    static final String ALGO = "PBKDF2WithHmacSHA1";   // tersedia di semua versi Android yang didukung (minSdk 24)

    private PinLock() { }

    static SharedPreferences store(Context c) {
        return c.getSharedPreferences("lock", Context.MODE_PRIVATE);
    }

    static boolean isSet(Context c) {
        return store(c).contains("hash");
    }

    static boolean valid(String pin) {
        return pin != null && pin.length() == LEN && pin.matches("[0-9]{" + LEN + "}");
    }

    static void set(Context c, String pin) throws Exception {
        if (!valid(pin)) throw new IllegalArgumentException("PIN harus " + LEN + " digit angka");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(pin, salt);
        store(c).edit()
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
    }

    static boolean check(Context c, String pin) {
        if (!valid(pin)) return false;
        try {
            SharedPreferences p = store(c);
            String s = p.getString("salt", null), hs = p.getString("hash", null);
            if (s == null || hs == null) return false;
            byte[] want = Base64.decode(hs, Base64.NO_WRAP);
            byte[] got = derive(pin, Base64.decode(s, Base64.NO_WRAP));
            return MessageDigest.isEqual(want, got);     // perbandingan waktu-konstan
        } catch (Exception e) {
            return false;
        }
    }

    static void clear(Context c) {
        store(c).edit().remove("salt").remove("hash").apply();
    }

    static byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
