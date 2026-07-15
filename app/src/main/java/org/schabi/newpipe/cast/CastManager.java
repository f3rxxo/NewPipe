package org.schabi.newpipe.cast;

import android.app.Activity;

import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;

public final class CastManager {

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
}
