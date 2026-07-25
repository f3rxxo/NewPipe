package org.schabi.newpipe.cast;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * A minimal local HTTP server used to expose a generated DASH manifest to a Cast receiver on the
 * same local network, and to proxy the receiver's segment requests through to the original CDN.
 *
 * The receiver runs on a separate device and cannot access content that only exists in the
 * app's memory, so it needs a URL of its own to fetch the manifest from. The manifest served
 * here points every {@code BaseURL} back at this server's {@code /proxy} endpoint rather than
 * at the CDN directly: the receiver's DASH player fetches segments via {@code fetch()}/XHR, and
 * CDNs that aren't scoped to allow arbitrary third-party origins will block those cross-origin
 * requests. Proxying keeps every request the receiver makes same-origin, while the actual CDN
 * request happens server-to-server, where CORS doesn't apply.
 */
public final class CastLocalServer extends NanoHTTPD {

    private static final String TAG = CastLocalServer.class.getSimpleName();
    private static final String MANIFEST_PATH = "/manifest.mpd";
    private static final String PROXY_PATH = "/proxy";

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
        final String uri = session.getUri();

        if (MANIFEST_PATH.equals(uri)) {
            return serveManifest();
        }
        if (PROXY_PATH.equals(uri)) {
            return serveProxiedSegment(session);
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
    }

    private Response serveManifest() {
        final byte[] bytes = manifestContent.getBytes(StandardCharsets.UTF_8);
        final Response response = newFixedLengthResponse(Response.Status.OK,
                "application/dash+xml", new ByteArrayInputStream(bytes), bytes.length);
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    /**
     * Fetches the CDN URL given in the {@code url} query parameter and streams its response
     * back, forwarding the incoming {@code Range} header so seeking and segment-by-segment
     * fetches work the same as a direct request would.
     */
    private Response serveProxiedSegment(final IHTTPSession session) {
        final Map<String, List<String>> params = session.getParameters();
        final List<String> urlParam = params.get("url");
        if (urlParam == null || urlParam.isEmpty()) {
            return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing url parameter");
        }

        final String targetUrl;
        try {
            targetUrl = URLDecoder.decode(urlParam.get(0), StandardCharsets.UTF_8.name());
        } catch (final IOException e) {
            return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid url parameter");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setInstanceFollowRedirects(true);

            final String range = session.getHeaders().get("range");
            if (range != null) {
                connection.setRequestProperty("Range", range);
            }

            final int responseCode = connection.getResponseCode();
            final Response.IStatus status = responseCode == HttpURLConnection.HTTP_PARTIAL
                    ? Response.Status.PARTIAL_CONTENT
                    : Response.Status.OK;

            final String contentType = connection.getContentType() != null
                    ? connection.getContentType()
                    : "application/octet-stream";
            final long contentLength = connection.getContentLengthLong();
            final InputStream body = connection.getInputStream();

            final Response response = contentLength >= 0
                    ? newFixedLengthResponse(status, contentType, body, contentLength)
                    : newChunkedResponse(status, contentType, body);

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Accept-Ranges", "bytes");
            final String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                response.addHeader("Content-Range", contentRange);
            }
            return response;
        } catch (final IOException e) {
            Log.w(TAG, "Failed to proxy Cast segment request for " + targetUrl, e);
            if (connection != null) {
                connection.disconnect();
            }
            return newFixedLengthResponse(Response.Status.BAD_GATEWAY, MIME_PLAINTEXT,
                    "Upstream request failed");
        }
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
