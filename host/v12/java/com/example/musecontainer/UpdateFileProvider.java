package com.example.musecontainer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * v10: serves ONLY the downloaded update APK to the system installer.
 * Not exported, so no other app can reach it. The only file it will
 * ever serve is updates/update.apk inside the app cache dir; any other
 * path is rejected, so this cannot be used to leak other files.
 */
public class UpdateFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.example.musecontainer.updateprovider";
    public static final String FILE_NAME = "update.apk";

    public static File updateFile(android.content.Context ctx) {
        File dir = new File(ctx.getCacheDir(), "updates");
        return new File(dir, FILE_NAME);
    }

    public static Uri updateUri(android.content.Context ctx) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(FILE_NAME).build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!FILE_NAME.equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException("Not an update file: " + uri);
        }
        File f = updateFile(getContext());
        if (!f.exists()) throw new FileNotFoundException("No downloaded update");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues v) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    @Override public int update(Uri uri, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }
}
