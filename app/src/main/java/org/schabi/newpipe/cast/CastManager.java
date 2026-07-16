package org.schabi.newpipe.cast;

import android.app.Activity;
import android.util.Log;

import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;

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
}
  