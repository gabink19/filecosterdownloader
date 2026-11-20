package com.example.fcdownloader;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Mendeteksi kapan aplikasi ditinggalkan (semua layar aplikasi tidak terlihat lagi). Saat aplikasi
 * dibuka kembali, PIN harus dimasukkan lagi (ditangani MainActivity; PlayerActivity mengalihkan ke sana).
 * Berpindah antar layar di dalam aplikasi (mis. ke pemutar) tidak dihitung sebagai meninggalkan aplikasi.
 */
final class AppLock {

    static int started;
    static boolean registered;
    static volatile boolean needLock, external, unlocked;

    private AppLock() { }

    static void install(Application app) {
        if (registered) return;
        registered = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                started++;
            }

            @Override
            public void onActivityStopped(Activity a) {
                started = Math.max(0, started - 1);
                if (started == 0 && !a.isChangingConfigurations()) {
                    // Disengaja: membuka pemilih file / penginstal / browser dari dalam aplikasi tidak
                    // memicu PIN saat kembali (lihat expectExternal). Selain itu PIN selalu diminta lagi.
                    if (external) external = false;
                    else {
                        needLock = true;
                        unlocked = false;
                    }
                }
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) { }

            @Override
            public void onActivityResumed(Activity a) {
                external = false;
            }

            @Override
            public void onActivityPaused(Activity a) { }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }

            @Override
            public void onActivityDestroyed(Activity a) { }
        });
    }

    /** Dipanggil tepat sebelum aplikasi sendiri membuka aplikasi lain (pemilih file, penginstal, browser). */
    static void expectExternal() {
        external = true;
    }
}
