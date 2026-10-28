package com.example.musecontainer;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v4d file logging: every important MuseImport / MuseLaunch line is written
 * BOTH to logcat (so `adb logcat > muse-log.txt` captures it) AND to a
 * plain text file on the phone, so the import proof survives even if
 * logcat was not running at Pick time or the device buffer was cleared.
 *
 * File (primary, pullable without root):
 *   /sdcard/Android/data/com.example.musecontainer/files/muse-log.txt
 * Fallback if external storage is unavailable:
 *   app-private files/muse-log.txt (readable via `adb run-as` for a debug app)
 * The file is appended, never truncated by a new log line, so one file
 * holds the whole Pick -> Create -> Launch sequence. It is deleted with
 * the app on uninstall, like all app-private data.
 */
public class FileLogger {
    public static final String FILE_NAME = "muse-log.txt";

    public static synchronized void log(Context context, String tag, String msg) {
        Log.i(tag, msg);
        try {
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + tag + ": " + msg + "\n";
            File dir = null;
            try { dir = context.getExternalFilesDir(null); } catch (Exception ignored) {}
            if (dir != null) {
                dir.mkdirs();
                append(new File(dir, FILE_NAME), line);
            }
            // Always also keep an internal copy, in case external is gone.
            append(new File(context.getFilesDir(), FILE_NAME), line);
        } catch (Exception ignored) {
            // File logging must never break import or launch.
        }
    }

    private static void append(File f, String line) {
        try {
            FileOutputStream out = new FileOutputStream(f, true);
            out.write(line.getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {}
    }
}
