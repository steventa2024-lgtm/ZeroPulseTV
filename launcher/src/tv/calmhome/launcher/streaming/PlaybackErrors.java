package tv.calmhome.launcher.streaming;

import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;

import java.io.IOException;

/**
 * Plain-language explanations for player failures. Never includes the stream URL (it may carry a token).
 * Unsupported codecs, DRM, region locks and expired links are reported as such; nothing tries to work around them.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlaybackErrors {

    /** Thrown for any non-HTTPS request the player makes (manifest, segment, key, redirect target). */
    public static final class HttpsOnly extends IOException {
        private static final long serialVersionUID = 1L;

        public HttpsOnly() {
            super("HTTPS only");
        }
    }

    private PlaybackErrors() { }

    public static String title(PlaybackException e) {
        if (has(e, HttpsOnly.class)) return "HTTP streams aren't supported";
        int code = e.errorCode;
        if (code >= 6000 && code < 7000) return "This stream is copy-protected";
        switch (code) {
            case PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED: return "HTTP streams aren't supported";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS: return status(e) == 401 || status(e) == 403 || status(e) == 451
                    ? "The stream refused access" : "The stream isn't available";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: return "The stream couldn't be reached";
            case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND: return "The stream isn't available";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED: return "The stream is damaged";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED: return "This stream format isn't supported";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES: return "This TV can't decode this stream";
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW: return "Fell behind the live stream";
            default: return "Playback stopped";
        }
    }

    public static String detail(PlaybackException e) {
        if (has(e, HttpsOnly.class) || e.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED) {
            return "Part of this stream uses an unencrypted HTTP address. This beta plays HTTPS streams only and "
                    + "doesn't fall back to HTTP.";
        }
        int code = e.errorCode;
        if (code >= 6000 && code < 7000) {
            return "It needs digital-rights management (DRM) that ZeroPulse doesn't provide. Use the provider's official app.";
        }
        switch (code) {
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                int s = status(e);
                return s == 401 || s == 403 || s == 451
                        ? "The server said no (HTTP " + s + "). It may need a subscription or login, or may not be available in your region."
                        : "The server answered HTTP " + (s > 0 ? s : "error") + ". The link may have expired or moved.";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Check the TV's internet connection. The stream's server may also be down.";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
                return "Its video or audio format (or resolution) isn't supported by this TV's decoders. "
                        + "ZeroPulse doesn't convert streams on the TV.";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                return "ZeroPulse plays HLS, MPEG-DASH and common progressive formats such as MP4.";
            default:
                return "Error " + e.getErrorCodeName().replace("ERROR_CODE_", "").toLowerCase(java.util.Locale.ROOT)
                        + ". Press OK to try again.";
        }
    }

    static int status(PlaybackException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
        }
        return 0;
    }

    static boolean has(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) if (type.isInstance(t)) return true;
        return false;
    }
}
