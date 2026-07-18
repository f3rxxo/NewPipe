package org.schabi.newpipe.cast;

import android.app.Activity;
import android.util.Log;
import android.net.Uri;

import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.annotation.NonNull;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;

public final class CastManager {

    private static final String TAG = CastManager.class.getSimpleName();
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
    public static void cast(final Activity activity,
                            @NonNull final MediaItemTag tag,
                            @NonNull final VideoStream stream) {

        final CastSession session =
                CastContext.getSharedInstance(activity)
                        .getSessionManager()
                        .getCurrentCastSession();

        if (session == null || !session.isConnected()) {
                Log.w(TAG, "No active Cast session");
                return;
        }

        final RemoteMediaClient remoteMediaClient = session.getRemoteMediaClient();

        if (remoteMediaClient == null) {
                Log.w(TAG, "RemoteMediaClient unavailable");
                return;
        }

        final MediaMetadata metadata =
        new MediaMetadata();

        metadata.putString(MediaMetadata.KEY_TITLE, tag.getTitle());
        metadata.putString(MediaMetadata.KEY_SUBTITLE, tag.getUploaderName());

        final String thumbnail = tag.getThumbnailUrl();
        if (thumbnail != null && !thumbnail.isEmpty()) {
                metadata.addImage(new WebImage(Uri.parse(thumbnail)));
        }

        final MediaInfo mediaInfo =
                new MediaInfo.Builder(stream.getContent())
                        .setContentType("video/mp4")
                        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                        .setMetadata(metadata)
                        .build();

        final MediaLoadRequestData request =
                new MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .build();

        remoteMediaClient.load(request);

        Log.d(TAG, "Casting: " + tag.getTitle());
    }
}
