package org.schabi.newpipe.cast;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.io.IOException;

public final class CastManager {

    private static final String TAG = CastManager.class.getSimpleName();

    @Nullable
    private static CastLocalServer localServer;

    private CastManager() {
    }
    public static void showChooser(final Activity activity) {
        final MediaRouteSelector selector =
                new MediaRouteSelector.Builder()
                        .addControlCategory(
                                CastMediaControlIntent.categoryForCast(
                                        CastMediaControlIntent
                                                .DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                        .build();

        final MediaRouteChooserDialog dialog =
                new MediaRouteChooserDialog(activity);

        dialog.setRouteSelector(selector);
        dialog.show();
    }
    public static boolean hasConnectedSession(final Activity activity) {

        final CastContext castContext = CastContext.getSharedInstance(activity);

        final SessionManager sessionManager = castContext.getSessionManager();

        final CastSession session = sessionManager.getCurrentCastSession();

        final boolean connected = session != null && session.isConnected();

        Log.d(TAG, "Cast connected = " + connected);

        return connected;
    }

    /**
     * Loads and starts playback of a media item on the currently connected Cast receiver.
     *
     * @param context        any context; used only to look up the shared {@link CastContext}
     * @param contentUrl     direct URL of the video/audio stream to play
     * @param contentType    MIME type of the stream (e.g. "video/mp4")
     * @param title          title to show on the receiver UI
     * @param subtitle       subtitle/uploader name to show on the receiver UI, may be null
     * @param imageUrl       URL of an artwork/thumbnail image, may be null
     * @param startPositionMs position, in milliseconds, to start playback from
     */
    public static void loadMedia(final Context context,
                                  final String contentUrl,
                                  final String contentType,
                                  final String title,
                                  @Nullable final String subtitle,
                                  @Nullable final String imageUrl,
                                  final long startPositionMs) {

        final RemoteMediaClient remoteMediaClient = getRemoteMediaClient(context);

        if (remoteMediaClient == null) {
            Log.w(TAG, "Cannot load media: no connected Cast session");
            return;
        }

        final MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, title);

        if (subtitle != null) {
            mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, subtitle);
        }

        if (imageUrl != null) {
            mediaMetadata.addImage(new WebImage(Uri.parse(imageUrl)));
        }

        final MediaInfo mediaInfo = new MediaInfo.Builder(contentUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(mediaMetadata)
                .build();

        final MediaLoadRequestData requestData = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(startPositionMs)
                .build();

        Log.d(TAG, "Loading media on Cast receiver: " + title);

        remoteMediaClient.load(requestData);
    }

    @Nullable
    private static RemoteMediaClient getRemoteMediaClient(final Context context) {
        final CastContext castContext = CastContext.getSharedInstance(context);

        final CastSession session = castContext.getSessionManager().getCurrentCastSession();

        if (session == null || !session.isConnected()) {
            Log.w(TAG, "No connected Cast session");
            return null;
        }

        return session.getRemoteMediaClient();
    }

    /**
     * Registers a listener that is notified when a Cast session starts/resumes/ends.
     * Callers should hold onto the listener instance and pass the same one to
     * {@link #unregisterSessionListener(Context, SessionManagerListener)} to avoid leaks.
     *
     * @param context  any context; used only to look up the shared {@link CastContext}
     * @param listener the listener to notify of Cast session lifecycle events
     */
    public static void registerSessionListener(
            final Context context,
            final SessionManagerListener<CastSession> listener) {
        CastContext.getSharedInstance(context).getSessionManager()
                .addSessionManagerListener(listener, CastSession.class);
    }

    /**
     * Unregisters a listener previously passed to
     * {@link #registerSessionListener(Context, SessionManagerListener)}.
     *
     * @param context  any context; used only to look up the shared {@link CastContext}
     * @param listener the listener instance to remove
     */
    public static void unregisterSessionListener(
            final Context context,
            final SessionManagerListener<CastSession> listener) {
        CastContext.getSharedInstance(context).getSessionManager()
                .removeSessionManagerListener(listener, CastSession.class);
    }

    /**
     * Serves the given combined (audio+video) DASH manifest from a local HTTP server. Any
     * previously running local server is stopped first. Safe to call from a background thread,
     * unlike {@link #loadMedia(Context, String, String, String, String, String, long)}, which
     * must be called from the main thread.
     *
     * @param manifestContent the combined DASH manifest XML to serve
     * @return the URL at which the manifest can be fetched from the Cast receiver
     * @throws IOException if the local server could not be started, or no local network
     *                      address could be found to serve the manifest from
     */
    public static String startLocalManifestServer(final String manifestContent)
            throws IOException {
        stopLocalServer();

        final CastLocalServer server = CastLocalServer.start(manifestContent);
        final String manifestUrl = server.getManifestUrl();

        if (manifestUrl == null) {
            server.stop();
            throw new IOException(
                    "Could not determine a local network address to serve the manifest from");
        }

        localServer = server;
        return manifestUrl;
    }

    /**
     * Stops the local manifest server started by {@link #startLocalManifestServer(String)}, if
     * running. Should be called once casting stops or the Cast session ends.
     */
    public static void stopLocalServer() {
        if (localServer != null) {
            localServer.stop();
            localServer = null;
        }
    }
}
