package com.example.musecontainer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v5: chat bubbles + fast switching, host overlay only.
 *
 * - One small draggable bubble per ACTIVE clone whose bubble toggle is ON
 *   (default OFF, stored per clone in CloneStore). A bubble is a shortcut
 *   icon ONLY: it does NOT render conversation content inside the bubble.
 *   The guest app's Bubbles API support is unproven, so nothing here
 *   claims to show chats in a bubble window.
 * - One small floating switch button (shown when at least one ACTIVE clone
 *   has its fast-switch toggle ON). Tapping it opens a compact overlay
 *   list of the other ACTIVE clones; tapping one launches that clone
 *   without going back to the host home screen.
 * - Deliberately lightweight: one TextView per bubble, one TextView for
 *   the switch button, views built on demand. NO polling loops, NO
 *   timers, NO handlers. Views change only on user action or on an
 *   explicit refresh intent from MainActivity.
 * - Drag a bubble to the trash strip at the bottom of the screen to hide
 *   it: that turns the clone's bubble toggle OFF persistently, so the
 *   bubble does not come back on the next service start.
 * - Switching honesty (v5 limitation, do not soften): the engine exposes
 *   NO bring-to-front API. BlackBoxCore.launchApk() only builds the
 *   guest's launch intent and starts it with FLAG_ACTIVITY_NEW_TASK
 *   (LauncherActivity). Switching therefore reuses exactly the same
 *   launchApk path as the host's Launch button. The source clone's
 *   LOGIN AND STATE ARE NOT LOST by switching: guest data lives in the
 *   virtual user's storage and the launch reuses it, so the target
 *   clone opens logged in. But the exact foreground screen/scroll/chat
 *   of a previously opened clone is NOT guaranteed to be restored —
 *   the guest's main activity may be recreated. That must be verified
 *   on the phone; it is a fallback, not faked instant switching.
 */
public class BubbleService extends Service {
    public static final String ACTION_REFRESH = "com.example.musecontainer.BUBBLE_REFRESH";
    public static final String ACTION_STOP = "com.example.musecontainer.BUBBLE_STOP";
    private static final String TAG = "MuseBubble";
    private static volatile boolean running = false;
    public static boolean isRunning() { return running; }

    private WindowManager wm;
    private final Map<String, View> bubbles = new HashMap<>();
    private View switchBtn;
    private View groupBubble;
    private View collapseBubble;
    private boolean groupExpanded = false;
    private View switchPanel;
    private View trashView;
    private int screenW, screenH;

    private static final int[] PALETTE = {
        0xFF3F51B5, 0xFFE91E63, 0xFF009688, 0xFFFF5722, 0xFF9C27B0, 0xFF4CAF50
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels; screenH = dm.heightPixels;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("bubbles", getString(R.string.bubble_channel), NotificationManager.IMPORTANCE_LOW));
        }
        Intent home = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, home,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification n;
        if (Build.VERSION.SDK_INT >= 26) {
            n = new Notification.Builder(this, "bubbles")
                .setContentTitle(getString(R.string.bubble_notif_title))
                .setContentText(getString(R.string.bubble_notif_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).build();
        } else {
            n = new Notification.Builder(this)
                .setContentTitle(getString(R.string.bubble_notif_title))
                .setContentText(getString(R.string.bubble_notif_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).build();
        }
        startForeground(2, n);
        Log.i(TAG, "BubbleService started");
        refresh();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.i(TAG, "BubbleService stop requested");
            stopSelf();
            return START_NOT_STICKY;
        }
        refresh();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        removeAllViews();
        running = false;
        Log.i(TAG, "BubbleService stopped");
        super.onDestroy();
    }

    private WindowManager.LayoutParams overlayParams(int w, int h, int x, int y) {
        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = x; p.y = y;
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private synchronized void refresh() {
        if (wm == null) return;
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission NOT granted; refusing to add views and stopping");
            stopSelf();
            return;
        }
        removeAllViews();
        List<CloneEntry> clones;
        try {
            clones = CloneStore.load(this);
        } catch (Exception e) {
            Log.e(TAG, "refresh: CloneStore load failed: " + e.getMessage());
            return;
        }
        List<CloneEntry> active = new ArrayList<>();
        for (CloneEntry c : clones) if (c.active) active.add(c);
        SharedPreferences prefs = getSharedPreferences("bubbles", MODE_PRIVATE);
        List<CloneEntry> bubbleClones = new ArrayList<>();
        for (CloneEntry c : active) if (c.bubbleEnabled) bubbleClones.add(c);
        // v8, overflow A2: more than 3 bubbles collapse into ONE
        // grouped stack bubble showing the count, so bubbles never
        // clutter the screen. Tap the group to expand the
        // individual bubbles; tap the small amber collapse bubble
        // to stack them again. Dragging the group to trash does
        // NOT turn any toggle off (it only re-stacks).
        if (bubbleClones.size() > 3 && !groupExpanded) {
            addGroupBubble(bubbleClones, prefs);
        } else {
            int bubbleIndex = 0;
            for (CloneEntry c : bubbleClones) {
                int defX = screenW - dp(72);
                int defY = dp(96) + bubbleIndex * dp(68);
                int x = prefs.getInt("x_" + c.id, defX);
                int y = prefs.getInt("y_" + c.id, defY);
                addBubble(c, x, y);
                bubbleIndex++;
            }
            if (bubbleClones.size() > 3) addCollapseBubble();
        }
        boolean anySwitch = false;
        for (CloneEntry c : active) if (c.switchEnabled) { anySwitch = true; break; }
        if (anySwitch) addSwitchButton();
        Log.i(TAG, "refresh: active=" + active.size() + " bubbles=" + bubbles.size() + " switchBtn=" + (switchBtn != null));
    }

    private void removeAllViews() {
        for (View v : new ArrayList<>(bubbles.values())) {
            try { wm.removeView(v); } catch (Exception ignored) {}
        }
        bubbles.clear();
        if (switchPanel != null) { try { wm.removeView(switchPanel); } catch (Exception ignored) {} switchPanel = null; }
        if (switchBtn != null) { try { wm.removeView(switchBtn); } catch (Exception ignored) {} switchBtn = null; }
        if (trashView != null) { try { wm.removeView(trashView); } catch (Exception ignored) {} trashView = null; }
        if (groupBubble != null) { try { wm.removeView(groupBubble); } catch (Exception ignored) {} groupBubble = null; }
        if (collapseBubble != null) { try { wm.removeView(collapseBubble); } catch (Exception ignored) {} collapseBubble = null; }
    }

    private TextView circleView(String letter, int color) {
        TextView tv = new TextView(this);
        tv.setText(letter);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(22f);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        tv.setBackground(d);
        return tv;
    }

    private void addBubble(final CloneEntry clone, int startX, int startY) {
        String letter = (clone.name != null && !clone.name.isEmpty())
            ? clone.name.substring(0, 1).toUpperCase() : "?";
        int color = PALETTE[Math.abs(clone.id.hashCode()) % PALETTE.length];
        final TextView bubble = circleView(letter, color);
        final WindowManager.LayoutParams p = overlayParams(dp(56), dp(56), startX, startY);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            int downX, downY, startX, startY;
            float downRawX, downRawY;
            boolean moved = false;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = ev.getRawX(); downRawY = ev.getRawY();
                        startX = p.x; startY = p.y; moved = false;
                        showTrash();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (ev.getRawX() - downRawX);
                        int dy = (int) (ev.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) moved = true;
                        p.x = startX + dx; p.y = startY + dy;
                        try { wm.updateViewLayout(bubble, p); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        hideTrash();
                        int cx = p.x + dp(28);
                        int cy = p.y + dp(28);
                        boolean inTrash = Math.abs(cx - screenW / 2) < dp(110) && cy > screenH - dp(190);
                        if (inTrash) {
                            Log.i(TAG, "Bubble drag-to-trash: hiding '" + clone.name + "' and turning its bubble toggle OFF");
                            hideBubblePersistOff(clone);
                        } else if (!moved) {
                            Log.i(TAG, "Bubble tap: launching '" + clone.name + "'");
                            launchInBackground(clone);
                        } else {
                            getSharedPreferences("bubbles", MODE_PRIVATE).edit()
                                .putInt("x_" + clone.id, p.x).putInt("y_" + clone.id, p.y).apply();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        try {
            wm.addView(bubble, p);
            bubbles.put(clone.id, bubble);
            Log.i(TAG, "Bubble added: '" + clone.name + "' at " + startX + "," + startY);
        } catch (Exception e) {
            Log.e(TAG, "addView bubble failed for '" + clone.name + "': " + e.getMessage());
        }
    }

    private void addGroupBubble(final List<CloneEntry> clones, final SharedPreferences prefs) {
        final TextView group = circleView(String.valueOf(clones.size()), 0xFF004D5C);
        group.setTextSize(20f);
        final WindowManager.LayoutParams p = overlayParams(dp(56), dp(56),
            prefs.getInt("x_group", screenW - dp(72)), prefs.getInt("y_group", dp(96)));
        group.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downRawX, downRawY;
            boolean moved = false;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = ev.getRawX(); downRawY = ev.getRawY();
                        startX = p.x; startY = p.y; moved = false;
                        showTrash();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (ev.getRawX() - downRawX);
                        int dy = (int) (ev.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) moved = true;
                        p.x = startX + dx; p.y = startY + dy;
                        try { wm.updateViewLayout(group, p); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        hideTrash();
                        int cx = p.x + dp(28);
                        int cy = p.y + dp(28);
                        boolean inTrash = Math.abs(cx - screenW / 2) < dp(110) && cy > screenH - dp(190);
                        if (inTrash) {
                            Toast.makeText(BubbleService.this, R.string.bubble_group_toast, Toast.LENGTH_LONG).show();
                            refresh();
                        } else if (!moved) {
                            Log.i(TAG, "Grouped stack bubble tapped: expanding " + clones.size() + " bubbles");
                            groupExpanded = true;
                            refresh();
                        } else {
                            prefs.edit().putInt("x_group", p.x).putInt("y_group", p.y).apply();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        try {
            wm.addView(group, p);
            groupBubble = group;
            Log.i(TAG, "Grouped stack bubble added: " + clones.size() + " bubbles collapsed");
        } catch (Exception e) {
            Log.e(TAG, "addView group bubble failed: " + e.getMessage());
        }
    }

    private void addCollapseBubble() {
        final TextView c = circleView("\u2013", 0xFFF5A623);
        c.setTextColor(0xFF12333A);
        c.setTextSize(20f);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "Collapse bubble tapped: re-stacking bubbles");
                groupExpanded = false;
                refresh();
            }
        });
        try {
            wm.addView(c, overlayParams(dp(48), dp(48), screenW - dp(64), dp(24)));
            collapseBubble = c;
        } catch (Exception e) {
            Log.e(TAG, "addView collapse bubble failed: " + e.getMessage());
        }
    }

    private void showTrash() {
        if (trashView != null) return;
        TextView t = new TextView(this);
        t.setText(R.string.trash_text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14f);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(0xCCB71C1C);
        trashView = t;
        try {
            wm.addView(t, overlayParams(screenW, dp(90), 0, screenH - dp(120)));
        } catch (Exception ignored) { trashView = null; }
    }

    private void hideTrash() {
        if (trashView != null) {
            try { wm.removeView(trashView); } catch (Exception ignored) {}
            trashView = null;
        }
    }

    private void hideBubblePersistOff(final CloneEntry clone) {
        View v = bubbles.remove(clone.id);
        if (v != null) { try { wm.removeView(v); } catch (Exception ignored) {} }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    List<CloneEntry> current = CloneStore.load(BubbleService.this);
                    List<CloneEntry> updated = new ArrayList<>();
                    for (CloneEntry e : current) {
                        updated.add(e.id.equals(clone.id) ? e.withBubble(false) : e);
                    }
                    CloneStore.save(BubbleService.this, updated);
                    Log.i(TAG, "Bubble toggle saved OFF for '" + clone.name + "'");
                } catch (Exception e) {
                    Log.e(TAG, "hideBubblePersistOff save failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void addSwitchButton() {
        final TextView btn = circleView("⇄", 0xFF212121);
        btn.setTextSize(20f);
        final WindowManager.LayoutParams p = overlayParams(dp(48), dp(48),
            screenW - dp(64), screenH / 2);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (switchPanel != null) {
                    try { wm.removeView(switchPanel); } catch (Exception ignored) {}
                    switchPanel = null;
                    Log.i(TAG, "Switch panel closed");
                } else {
                    openSwitchPanel();
                }
            }
        });
        try {
            wm.addView(btn, p);
            switchBtn = btn;
            Log.i(TAG, "Switch button added");
        } catch (Exception e) {
            Log.e(TAG, "addView switch button failed: " + e.getMessage());
        }
    }

    private void openSwitchPanel() {
        List<CloneEntry> clones;
        try {
            clones = CloneStore.load(this);
        } catch (Exception e) {
            Log.e(TAG, "openSwitchPanel: CloneStore load failed: " + e.getMessage());
            return;
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEEFFFFFF);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView title = new TextView(this);
        title.setText(R.string.switch_title);
        title.setTextColor(0xFF004D5C);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView note = new TextView(this);
        note.setText(R.string.switch_note);
        note.setTextColor(0xFF5A6E72);
        note.setTextSize(13f);
        panel.addView(note);
        title.setTextColor(Color.BLACK);
        title.setTextSize(14f);
        panel.addView(title);
        int count = 0;
        for (final CloneEntry c : clones) {
            if (!c.active) continue;
            Button b = new Button(this);
            b.setText(getString(R.string.switch_open, c.name));
            b.setBackgroundResource(R.drawable.btn_primary);
            b.setTextColor(Color.WHITE);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Log.i(TAG, "Fast switch tap: jumping to '" + c.name + "' (launchApk path; bring-to-front NOT available in engine)");
                    closeSwitchPanel();
                    launchInBackground(c);
                }
            });
            panel.addView(b);
            count++;
        }
        if (count == 0) {
            TextView none = new TextView(this);
            none.setText(R.string.switch_none);
            none.setTextColor(Color.BLACK);
            panel.addView(none);
        }
        Button close = new Button(this);
        close.setText(R.string.switch_close);
        close.setBackgroundResource(R.drawable.btn_secondary);
        close.setTextColor(Color.parseColor("#12333A"));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSwitchPanel(); }
        });
        panel.addView(close);
        try {
            wm.addView(panel, overlayParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT,
                screenW - dp(316), screenH / 2 - dp(120)));
            switchPanel = panel;
            Log.i(TAG, "Switch panel opened with " + count + " active clones");
        } catch (Exception e) {
            Log.e(TAG, "addView switch panel failed: " + e.getMessage());
        }
    }

    private void closeSwitchPanel() {
        if (switchPanel != null) {
            try { wm.removeView(switchPanel); } catch (Exception ignored) {}
            switchPanel = null;
        }
    }

    /** Launch on a background thread: EngineLauncher does file/SHA work
     *  and engine calls that must never run on a touch/UI path. */
    private void launchInBackground(final CloneEntry clone) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    EngineLauncher.Result r = EngineLauncher.launchClone(clone, BubbleService.this);
                    Log.i(TAG, "launchClone from overlay: clone='" + clone.name + "' launched=" + r.launched + " detail=" + r.detail);
                } catch (Exception e) {
                    Log.e(TAG, "launchClone from overlay threw: " + e.getMessage());
                }
            }
        }).start();
    }
}
