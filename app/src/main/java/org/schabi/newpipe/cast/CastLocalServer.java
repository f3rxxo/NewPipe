package org.schabi.newpipe.cast;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import fi.iki.elonen.NanoHTTPD;

/**
 * A minimal local HTTP server used to expose a generated DASH manifest to a Cast receiver on the
 * same local network. The receiver runs on a separate device and cannot access content that
 * only exists in the app's memory, so it needs a URL of its own to fetch the manifest from.
 */
public final class CastLocalServer extends NanoHTTPD {

    private static final String TAG = CastLocalServer.class.getSimpleName();
    private static final String MANIFEST_PATH = "/manifest.mpd";

    private final String manifestContent;

    private CastLocalServer(final int port, final String manifestContent) {
        super(port);
        this.manifestContent = manifestContent;
    }

    /**
     * Starts a new local server on an available port, serving the given manifest content.
     *
     * @param manifestContent the DASH manifest XML to serve
     * @return the started server
     * @throws IOException if the server could not be started
     */
    public static CastLocalServer start(final String manifestContent) throws IOException {
        // port 0 lets the OS pick any free port
        final CastLocalServer server = new CastLocalServer(0, manifestContent);
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Log.d(TAG, "Local Cast manifest server started on port " + server.getListeningPort());
        return server;
    }

    /**
     * @return the full URL at which the manifest can be fetched from other devices on the same
     * local network, or {@code null} if no suitable local network address could be found
     */
    @Nullable
    public String getManifestUrl() {
        final String host = findLocalIpAddress();
        if (host == null) {
            return null;
        }
        return "http://" + host + ":" + getListeningPort() + MANIFEST_PATH;
    }

    @Override
    public Response serve(final IHTTPSession session) {
        if (!MANIFEST_PATH.equals(session.getUri())) {
            return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
        }

        final byte[] bytes = manifestContent.getBytes(StandardCharsets.UTF_8);
        final Response response = newFixedLengthResponse(Response.Status.OK,
                "application/dash+xml", new ByteArrayInputStream(bytes), bytes.length);
        // The Cast receiver's own web sandbox fetches this URL cross-origin.
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    @Nullable
    private static String findLocalIpAddress() {
        try {
            final Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            for (final NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                for (final InetAddress address
                        : Collections.list(networkInterface.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (final IOException e) {
            Log.w(TAG, "Could not determine local IP address", e);
        }
        return null;
    }
}
