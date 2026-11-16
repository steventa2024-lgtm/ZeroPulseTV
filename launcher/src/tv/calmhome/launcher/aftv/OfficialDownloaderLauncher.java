package tv.calmhome.launcher.aftv;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import tv.calmhome.launcher.core.DownloaderCodes;

/**
 * Opens, or helps install, the official Downloader by AFTVnews app ({@value #DOWNLOADER}), and sends it AFTVnews short codes.
 * The launcher does not download anything here: an installed Downloader is simply started, and a missing one is offered through
 * the TV's own Play Store (market://details). A TV with no store goes on to the reviewed APK install in
 * {@link tv.calmhome.launcher.screens.DownloaderPanel}. {@link #VLC} gets the same open-or-install helper.
 *
 * Every launch is a new task and every start is guarded: an app that cannot take the intent never crashes the launcher.
 */
public final class OfficialDownloaderLauncher {

    /** Downloader by AFTVnews (the orange app). */
    public static final String DOWNLOADER = "com.esaba.downloader";
    /** VLC media player (the orange cone). */
    public static final String VLC = "org.videolan.vlc";

    public enum Outcome {
        /** The app was installed and has been started. */
        OPENED,
        /** The app is not installed; its Play Store page was opened on the Install button. */
        STORE_OPENED,
        /** The app is not installed and this TV has no store that can open its page. */
        STORE_UNAVAILABLE,
        /** The code was handed to Downloader as a link. */
        CODE_SENT,
        /** Downloader would not take the link: its code is on the clipboard and the app was opened. */
        CODE_COPIED_AND_OPENED,
        /** Downloader would not take the link and would not open: its code is on the clipboard. */
        CODE_COPIED,
        /** Downloader is installed but nothing could start it or take the code. */
        FAILED,
        INVALID_CODE
    }

    private OfficialDownloaderLauncher() { }

    /** The intent that starts an installed app: its TV (leanback) entry first, else its ordinary launcher entry; null if neither. */
    public static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    public static boolean isInstalled(Context context, String pkg) {
        return launchIntent(context.getPackageManager(), pkg) != null;
    }

    /** Starts the app if it is installed; otherwise opens its Play Store page on the Install button. */
    public static Outcome openOrInstall(Context context, String pkg) {
        Intent launch = launchIntent(context.getPackageManager(), pkg);
        if (launch != null && start(context, launch)) return Outcome.OPENED;
        // Not installed (or installed but cannot start): the store page offers Install (or Open / Enable).
        if (start(context, storeIntent(pkg))) return Outcome.STORE_OPENED;
        return launch != null ? Outcome.FAILED : Outcome.STORE_UNAVAILABLE;
    }

    public static Outcome openOrInstallDownloader(Context context) {
        return openOrInstall(context, DOWNLOADER);
    }

    public static Outcome openOrInstallVlc(Context context) {
        return openOrInstall(context, VLC);
    }

    /** The Google TV Play Store page of an app. */
    public static Intent storeIntent(String pkg) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
    }

    /** The link a code is sent as, addressed to Downloader alone. */
    public static Intent codeIntent(String code) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(DownloaderCodes.url(code))).setPackage(DOWNLOADER);
    }

    /**
     * Sends {@code http://aftv.news/<code>} into Downloader so it starts fetching that code. If Downloader does not take a link
     * (its own screens only document typing a code), the code goes on the clipboard and the app opens, to paste it. Not installed:
     * as {@link #openOrInstallDownloader}.
     */
    public static Outcome sendCode(Context context, String code) {
        if (!DownloaderCodes.isCode(code)) return Outcome.INVALID_CODE;
        if (!isInstalled(context, DOWNLOADER)) return openOrInstall(context, DOWNLOADER);
        if (start(context, codeIntent(code))) return Outcome.CODE_SENT;
        boolean copied = copy(context, code);
        Intent launch = launchIntent(context.getPackageManager(), DOWNLOADER);
        if (launch != null && start(context, launch)) return copied ? Outcome.CODE_COPIED_AND_OPENED : Outcome.OPENED;
        return copied ? Outcome.CODE_COPIED : Outcome.FAILED;
    }

    /** What to tell the viewer about an outcome (one line, for a toast or the Assistant's status). */
    public static String message(Outcome outcome, String code) {
        switch (outcome) {
            case OPENED: return "Opening Downloader.";
            case STORE_OPENED: return "Downloader isn't installed. Opening its page in the Play Store: select Install.";
            case STORE_UNAVAILABLE: return "Downloader isn't installed, and this TV has no Play Store to get it from.";
            case CODE_SENT: return "Sent code " + code + " to Downloader.";
            case CODE_COPIED_AND_OPENED:
                return "Downloader opened but did not take the link, so code " + code + " is copied: paste it into Downloader's box.";
            case CODE_COPIED: return "Code " + code + " is copied, but Downloader would not open.";
            case FAILED: return "Downloader would not open.";
            default: return "A code is 1 to 16 digits.";
        }
    }

    private static boolean start(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException cannotStart) {
            return false;
        }
    }

    private static boolean copy(Context context, String code) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("Downloader code", code));
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
