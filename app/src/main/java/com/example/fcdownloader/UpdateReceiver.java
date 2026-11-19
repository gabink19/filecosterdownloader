package com.example.fcdownloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Menerima hasil pemasangan pembaruan dari PackageInstaller. */
public class UpdateReceiver extends BroadcastReceiver {

    static final String ACTION = "com.example.fcdownloader.UPDATE_STATUS";

    interface Listener {
        /** status = PackageInstaller.STATUS_*; msg sudah dalam bahasa pengguna, raw = pesan asli Android. */
        void onStatus(boolean ok, int status, String msg, String raw);
    }

    /** Diisi MainActivity selama tampil, agar hasilnya muncul di baris status. */
    static volatile Listener listener;

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        int st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    ctx.startActivity(confirm);
                } catch (Exception e) {
                    report(ctx, false, PackageInstaller.STATUS_FAILURE, "Tidak bisa membuka layar pemasangan.", e.getMessage());
                }
            }
            return;
        }
        String raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (st == PackageInstaller.STATUS_SUCCESS) {
            report(ctx, true, st, "Pembaruan terpasang.", raw);
            return;
        }
        String msg;
        if (st == PackageInstaller.STATUS_FAILURE_CONFLICT || st == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
            msg = "Pembaruan ditolak karena tanda tangan APK berbeda dengan aplikasi terpasang. "
                    + "Hapus aplikasi lama sekali saja, lalu pasang APK terbaru dari GitHub.";
        } else if (st == PackageInstaller.STATUS_FAILURE_ABORTED) {
            msg = "Pemasangan pembaruan dibatalkan.";
        } else if (st == PackageInstaller.STATUS_FAILURE_STORAGE) {
            msg = "Penyimpanan HP penuh, pembaruan tidak bisa dipasang.";
        } else if (st == PackageInstaller.STATUS_FAILURE_BLOCKED) {
            msg = "Pemasangan diblokir oleh HP (mis. Play Protect atau pengaturan keamanan pabrikan).";
        } else {
            msg = "Pembaruan gagal dipasang.";
        }
        report(ctx, false, st, msg, raw);
    }

    static void report(Context ctx, boolean ok, int status, String msg, String raw) {
        Listener l = listener;
        if (l != null) l.onStatus(ok, status, msg, raw);
        else Toast.makeText(ctx.getApplicationContext(), msg + (raw != null ? " (" + raw + ")" : ""), Toast.LENGTH_LONG).show();
    }
}
