package com.example.fcdownloader;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Membagikan SATU file saja (APK pembaruan di cache aplikasi) ke penginstal APK bawaan Android,
 * sebagai jalur cadangan jika pemasangan lewat PackageInstaller ditolak HP (mis. MIUI).
 * Tidak di-export: penginstal hanya bisa membacanya lewat izin sementara (FLAG_GRANT_READ_URI_PERMISSION).
 */
public class ApkProvider extends ContentProvider {

    static final String NAME = "update.apk";
    static final String MIME = "application/vnd.android.package-archive";

    static Uri uri(Context c) {
        return Uri.parse("content://" + c.getPackageName() + ".apk/" + NAME);
    }

    static File file(Context c) {
        return new File(new File(c.getCacheDir(), "update"), NAME);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return MIME;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!NAME.equals(uri.getLastPathSegment()) || !"r".equals(mode) || getContext() == null) {
            throw new FileNotFoundException("tidak tersedia");
        }
        return ParcelFileDescriptor.open(file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!NAME.equals(uri.getLastPathSegment()) || getContext() == null) return null;
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"FilecosterDownloader.apk", file(getContext()).length()});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
