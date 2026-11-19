package com.example.fcdownloader;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.util.Locale;

/**
 * Membaca spesifikasi HP (RAM, jumlah inti CPU, mode RAM rendah) lalu menghitung
 * berapa proses sekaligus yang masuk akal. Tiap proses pengumpul link membuka satu
 * WebView sungguhan, jadi bebannya paling terasa di RAM dan CPU.
 */
final class DeviceProfile {
    final long ramMb;
    final int cores, sdk, heapMb;
    final boolean lowRam;
    /** Rekomendasi jumlah proses pengumpulan link bersamaan (1 sampai REC_MAX). */
    final int recCollect;
    /** Rekomendasi jumlah unduhan bersamaan (beban jaringan saja, jauh lebih ringan). */
    final int recDownload;

    private DeviceProfile(long ramMb, int cores, int sdk, int heapMb, boolean lowRam) {
        this.ramMb = ramMb;
        this.cores = cores;
        this.sdk = sdk;
        this.heapMb = heapMb;
        this.lowRam = lowRam;
        this.recCollect = pickCollect(ramMb, cores, lowRam, heapMb);
        this.recDownload = pickDownload(ramMb, cores, lowRam);
    }

    static DeviceProfile read(Context c) {
        long ram = 0;
        boolean low = false;
        int heap = 0;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                ram = mi.totalMem / (1024L * 1024L);
                low = am.isLowRamDevice();
                heap = am.getMemoryClass();
            }
        } catch (Exception ignored) { }
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new DeviceProfile(ram, cores, Build.VERSION.SDK_INT, heap, low);
    }

    // Catatan: RAM yang dilaporkan Android selalu sedikit di bawah angka di kotak HP
    // (HP "4 GB" biasanya terbaca sekitar 3,7 GB), jadi batasnya dibuat longgar.
    private static int pickCollect(long ramMb, int cores, boolean lowRam, int heapMb) {
        if (lowRam) return 1;
        if (ramMb <= 0) return 2;                          // tidak terbaca: ambil jalan tengah
        int byRam = ramMb < 3400 ? 1                       // 3 GB ke bawah
                : ramMb < 5000 ? 2                         // 4 sampai 5 GB
                : ramMb < 7000 ? 3                         // 6 GB
                : 4;                                       // 8 GB ke atas
        int byCpu = cores >= 8 ? 4 : cores >= 6 ? 3 : cores >= 4 ? 2 : 1;
        int rec = Math.min(byRam, byCpu);
        if (heapMb > 0 && heapMb < 100) rec = 1;           // batas memori per aplikasi sangat kecil
        return Math.max(1, Math.min(MainActivity.REC_MAX, rec));
    }

    private static int pickDownload(long ramMb, int cores, boolean lowRam) {
        if (lowRam || (ramMb > 0 && ramMb < 3400)) return 2;
        int rec = (ramMb > 0 && ramMb < 7000) ? 3 : 5;
        if (cores < 4) rec = Math.min(rec, 3);
        return rec;
    }

    String describe() {
        String ram = ramMb > 0
                ? String.format(Locale.getDefault(), "%.1f GB", ramMb / 1024.0)
                : "tidak terbaca";
        return "HP ini: RAM " + ram + ", " + cores + " inti CPU, Android " + Build.VERSION.RELEASE
                + (lowRam ? ", mode RAM rendah" : "") + ".\n"
                + "Rekomendasi: kumpulkan link " + recCollect + " proses sekaligus, unduh " + recDownload
                + " sekaligus. Tiap proses pengumpul membuka satu halaman web sungguhan, jadi paling berat "
                + "di RAM. Jika memori HP menipis saat bekerja, aplikasi menurunkan jumlah proses sendiri.";
    }
}
