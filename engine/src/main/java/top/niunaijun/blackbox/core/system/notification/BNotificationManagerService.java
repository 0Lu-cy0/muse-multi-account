package top.niunaijun.blackbox.core.system.notification;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import black.android.app.BRNotificationChannel;
import black.android.app.BRNotificationChannelGroup;
import black.android.app.BRNotificationO;
import black.android.app.NotificationChannelContext;
import black.android.app.NotificationChannelGroupContext;
import black.android.app.NotificationOContext;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Created by BlackBox on 2022/3/15.
 */
public class BNotificationManagerService extends IBNotificationManagerService.Stub implements ISystemService {
    private final static BNotificationManagerService sService = new BNotificationManagerService();
    public static final String CHANNEL_BLACK = "@black-";
    public static final String GROUP_BLACK = "@black-group-";

    private NotificationChannelManager mNotificationChannelManager;
    private final Map<String, NotificationRecord> mNotificationRecords = new HashMap<>();

    // v4 hybrid notification labelling: the host writes
    // clone_names.json (virtualUserId -> clone name) into the host
    // filesDir on every CloneStore.save. The engine only knows a
    // numeric userId, so without this file it could only label
    // "Clone 2". The file is read lazily and re-read only when its
    // last-modified time changes, so labelling adds no polling and
    // no extra process. If the file is missing or unparseable,
    // labelling is skipped and the notification is posted unchanged.
    private static final Object sCloneNamesLock = new Object();
    private static Map<Integer, String> sCloneNames = new HashMap<>();
    private static long sCloneNamesModified = -1L;

    private static String getCloneName(int userId) {
        try {
            android.content.Context ctx = BlackBoxCore.getContext();
            if (ctx == null) return null;
            File f = new File(ctx.getFilesDir(), "clone_names.json");
            if (!f.exists()) return null;
            long modified = f.lastModified();
            synchronized (sCloneNamesLock) {
                if (modified != sCloneNamesModified) {
                    Map<Integer, String> parsed = parseCloneNames(readFile(f));
                    sCloneNames = parsed;
                    sCloneNamesModified = modified;
                }
                return sCloneNames.get(userId);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    // Minimal parser for a flat {"0":"Name","1":"Other"} object.
    // Avoids an org.json dependency in Bcore. Handles \" and \\ escapes.
    private static Map<Integer, String> parseCloneNames(String json) {
        Map<Integer, String> out = new HashMap<>();
        if (json == null) return out;
        int i = 0;
        int len = json.length();
        while (i < len) {
            if (json.charAt(i) != '"') { i++; continue; }
            String[] keyAndNext = readJsonString(json, i);
            if (keyAndNext == null) break;
            String key = keyAndNext[0];
            i = Integer.parseInt(keyAndNext[1]);
            while (i < len && (json.charAt(i) == ' ' || json.charAt(i) == '\t' || json.charAt(i) == '\n' || json.charAt(i) == '\r')) i++;
            if (i >= len || json.charAt(i) != ':') continue;
            i++;
            while (i < len && (json.charAt(i) == ' ' || json.charAt(i) == '\t' || json.charAt(i) == '\n' || json.charAt(i) == '\r')) i++;
            if (i >= len || json.charAt(i) != '"') continue;
            String[] valAndNext = readJsonString(json, i);
            if (valAndNext == null) break;
            String value = valAndNext[0];
            i = Integer.parseInt(valAndNext[1]);
            try {
                out.put(Integer.parseInt(key), value);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private static String[] readJsonString(String json, int start) {
        // start points at the opening quote. Returns {value, nextIndex}.
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char e = json.charAt(i + 1);
                if (e == '"') sb.append('"');
                else if (e == '\\') sb.append('\\');
                else if (e == '/') sb.append('/');
                else if (e == 'n') sb.append('\n');
                else if (e == 't') sb.append('\t');
                else if (e == 'r') sb.append('\r');
                else if (e == 'b') sb.append('\b');
                else if (e == 'f') sb.append('\f');
                else sb.append(e);
                i += 2;
                continue;
            }
            if (c == '"') return new String[]{sb.toString(), String.valueOf(i + 1)};
            sb.append(c);
            i++;
        }
        return null;
    }

    private static void labelNotification(android.app.Notification notification, int userId) {
        try {
            String name = getCloneName(userId);
            if (name == null || name.isEmpty()) return;
            if (notification.extras == null) return;
            String prefix = "[" + name + "] ";
            // MessagingStyle notifications show the conversation title,
            // not the plain title, so prefix that when present and
            // only fall back to the plain title otherwise. This avoids
            // a double prefix in the shade.
            String conv = notification.extras.getString(android.app.Notification.EXTRA_CONVERSATION_TITLE);
            if (conv != null && !conv.isEmpty()) {
                if (!conv.startsWith(prefix)) {
                    notification.extras.putString(android.app.Notification.EXTRA_CONVERSATION_TITLE, prefix + conv);
                }
                return;
            }
            String title = notification.extras.getString(android.app.Notification.EXTRA_TITLE);
            if (title != null && !title.isEmpty() && !title.startsWith(prefix)) {
                notification.extras.putString(android.app.Notification.EXTRA_TITLE, prefix + title);
            }
        } catch (Exception ignored) {
            // Labelling must never block or drop a notification.
        }
    }

    private final NotificationManager mRealNotificationManager =
            (NotificationManager) BlackBoxCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);

    public static BNotificationManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        mNotificationChannelManager = NotificationChannelManager.get();
    }


    private NotificationRecord getNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            NotificationRecord notificationRecord = mNotificationRecords.get(key);
            if (notificationRecord == null) {
                notificationRecord = new NotificationRecord();
                mNotificationRecords.put(key, notificationRecord);
            }
            return notificationRecord;
        }
    }

    private void removeNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            mNotificationRecords.remove(key);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public NotificationChannel getNotificationChannel(String channelId, int userId) throws RemoteException {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return null;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            return notificationRecord.mNotificationChannels.get(channelId);
        }
    }

    @Override
    public List<NotificationChannel> getNotificationChannels(String packageName, int userId) throws RemoteException {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannels) {
            return new ArrayList<>(notificationRecord.mNotificationChannels.values());
        }
    }

    @Override
    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName, int userId) throws RemoteException {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            return new ArrayList<>(notificationRecord.mNotificationChannelGroups.values());
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationChannel notificationChannel, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        // v4: prefix the real channel name with the clone name, but
        // restore the original name before storing the channel in the
        // guest-visible record. Note: Android does not update the name
        // of an already-existing channel, so this only takes effect
        // for channels created for the first time under v4; the title
        // prefix in enqueueNotificationWithTag is the primary label.
        String origChannelName = null;
        try {
            String cloneName = getCloneName(userId);
            if (cloneName != null && !cloneName.isEmpty() && notificationChannel.getName() != null) {
                origChannelName = notificationChannel.getName().toString();
                String prefix = "[" + cloneName + "] ";
                if (!origChannelName.startsWith(prefix)) {
                    notificationChannel.setName(prefix + origChannelName);
                } else {
                    origChannelName = null;
                }
            }
        } catch (Exception ignored) {}
        handleNotificationChannel(notificationChannel, userId);
        mRealNotificationManager.createNotificationChannel(notificationChannel);

        resetNotificationChannel(notificationChannel);
        if (origChannelName != null) {
            try { notificationChannel.setName(origChannelName); } catch (Exception ignored) {}
        }
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            notificationRecord.mNotificationChannels.put(notificationChannel.getId(), notificationChannel);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannel(String channelId, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            NotificationChannel remove = notificationRecord.mNotificationChannels.remove(channelId);
            if (remove != null) {
                String blackChannelId = getBlackChannelId(remove.getId(), userId);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        handleNotificationGroup(notificationChannelGroup, userId);
        mRealNotificationManager.createNotificationChannelGroup(notificationChannelGroup);

        resetNotificationGroup(notificationChannelGroup);
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            notificationRecord.mNotificationChannelGroups.put(notificationChannelGroup.getId(), notificationChannelGroup);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannelGroup(String groupId, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            NotificationChannelGroup remove = notificationRecord.mNotificationChannelGroups.remove(groupId);
            if (remove != null) {
                String blackGroupId = getBlackGroupId(remove.getId(), userId);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
        }
    }

    @Override
    public void enqueueNotificationWithTag(int id, String tag, Notification notification, int userId) {
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        int notificationId = getNotificationId(userId, id, processByPid.getPackageName());

        if (BuildCompat.isOreo()) {
            NotificationOContext notificationOContext = BRNotificationO.get(notification);
            // channel
            if (notificationOContext._check_mChannelId() != null) {
                String blackChannelId = getBlackChannelId(notificationOContext.mChannelId(), userId);
                notificationOContext._set_mChannelId(blackChannelId);
            }
            // group
            if (notificationOContext._check_mGroupKey() != null) {
                String blackGroupId = getBlackGroupId(notificationOContext.mGroupKey(), userId);
                notificationOContext._set_mGroupKey(blackGroupId);
            }
        }
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mIds) {
            notificationRecord.mIds.add(notificationId);
        }
        // v4 hybrid labelling: prefix the title / conversation title
        // with the clone name from clone_names.json. The notification
        // object here is a parcel copy in the engine service, so this
        // does not mutate the guest's own copy. Tap routing is left
        // to the engine's existing PendingIntent proxy
        // (BActivityManagerService intent-sender records per userSpace);
        // it must be verified on a real phone, not assumed here.
        labelNotification(notification, userId);
        mRealNotificationManager.notify(notificationId, notification);
    }

    @Override
    public void cancelNotificationWithTag(int id, String tag, int userId) throws RemoteException {
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        int notificationId = getNotificationId(userId, id, processByPid.getPackageName());
        mRealNotificationManager.cancel(notificationId);

        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mIds) {
            notificationRecord.mIds.remove(notificationId);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void handleNotificationChannel(NotificationChannel notificationChannel, int userId) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String blackChannelId = getBlackChannelId(channelId, userId);
        channelContext._set_mId(blackChannelId);

        notificationChannel.setGroup(getBlackGroupId(notificationChannel.getGroup(), userId));
    }

    private void resetNotificationChannel(NotificationChannel notificationChannel) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String realChannelId = getRealChannelId(channelId);
        channelContext._set_mId(realChannelId);
    }

    private void handleNotificationGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String blackGroupId = getBlackGroupId(groupId, userId);
        groupContext._set_mId(blackGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                createNotificationChannel(notificationChannel, userId);
            }
        }
    }

    private void resetNotificationGroup(NotificationChannelGroup notificationChannelGroup) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String realGroupId = getRealGroupId(groupId);
        groupContext._set_mId(realGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                resetNotificationChannel(notificationChannel);
            }
        }
    }

    @SuppressLint("NewApi")
    public void deletePackageNotification(String packageName, int userId) {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        if (BuildCompat.isOreo()) {
            for (NotificationChannelGroup value : notificationRecord.mNotificationChannelGroups.values()) {
                String blackGroupId = getBlackGroupId(value.getId(), userId);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
            for (NotificationChannel value : notificationRecord.mNotificationChannels.values()) {
                String blackChannelId = getBlackChannelId(value.getId(), userId);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
        for (Integer id : notificationRecord.mIds) {
            mRealNotificationManager.cancel(id);
        }
        removeNotificationRecord(packageName, userId);
    }

    private String getBlackChannelId(String channelId, int userId) {
        if (channelId == null || channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId + CHANNEL_BLACK + userId;
    }

    private String getRealChannelId(String channelId) {
        if (channelId == null || !channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId.split(CHANNEL_BLACK)[0];
    }

    private String getBlackGroupId(String groupId, int userId) {
        if (groupId == null || groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId + GROUP_BLACK + userId;
    }

    private String getRealGroupId(String groupId) {
        if (groupId == null || !groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId.split(GROUP_BLACK)[0];
    }

    public static int getNotificationId(int userId, int notificationId, String packageName) {
        return (packageName + userId + notificationId).hashCode();
    }
}
