package org.schabi.newpipe.cast;

import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Builds a single DASH manifest combining a video-only stream and an audio-only stream, for use
 * when casting to a receiver that only accepts a single content URL and therefore cannot be
 * given the two separate stream URLs directly.
 *
 * Each individual stream's manifest is generated using NewPipeExtractor's existing, proven
 * single-stream DASH manifest generators (the same ones used for local ExoPlayer playback); this
 * class only splices their {@code AdaptationSet} elements together into one combined document,
 * rather than re-implementing manifest generation itself.
 */
public final class CastManifestBuilder {

    private CastManifestBuilder() {
    }

    /**
     * Builds a combined DASH manifest (MPD) containing both a video and an audio adaptation set.
     *
     * @param videoStream the video-only stream to include
     * @param audioStream the audio-only stream to include
     * @param streamInfo  the stream info, used to determine the content duration
     * @return the combined manifest as an XML string
     * @throws CreationException if manifest generation for either stream fails
     * @throws IOException       if fetching stream data for manifest generation, or combining
     *                           the two generated manifests, fails
     */
    public static String buildCombinedManifest(final VideoStream videoStream,
                                                final AudioStream audioStream,
                                                final StreamInfo streamInfo)
            throws CreationException, IOException {
        final String videoManifest = buildSingleTrackManifest(videoStream, streamInfo);
        final String audioManifest = buildSingleTrackManifest(audioStream, streamInfo);

        try {
            final DocumentBuilder builder = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder();

            final Document videoDoc = builder.parse(
                    new InputSource(new StringReader(videoManifest)));
            final Document audioDoc = builder.parse(
                    new InputSource(new StringReader(audioManifest)));

            final Element videoPeriod = getFirstElementByTagName(videoDoc, "Period");
            final Element audioPeriod = getFirstElementByTagName(audioDoc, "Period");

            final Set<String> usedIds = collectAdaptationSetIds(videoPeriod);

            final NodeList audioAdaptationSets =
                    audioPeriod.getElementsByTagName("AdaptationSet");
            int nextId = usedIds.size();
            for (int i = 0; i < audioAdaptationSets.getLength(); i++) {
                final Element imported = (Element) videoDoc.importNode(
                        audioAdaptationSets.item(i), true);

                // Each single-track manifest is generated independently and numbers its own
                // AdaptationSet starting from 0, so IDs can collide once spliced together;
                // AdaptationSet ids must be unique within a Period per the DASH spec.
                if (usedIds.contains(imported.getAttribute("id"))) {
                    while (usedIds.contains(String.valueOf(nextId))) {
                        nextId++;
                    }
                    imported.setAttribute("id", String.valueOf(nextId));
                }
                usedIds.add(imported.getAttribute("id"));

                videoPeriod.appendChild(imported);
            }

            return serialize(videoDoc);
        } catch (final ParserConfigurationException | SAXException | TransformerException e) {
            throw new IOException("Could not combine video and audio DASH manifests", e);
        }
    }

    private static Set<String> collectAdaptationSetIds(final Element period) {
        final Set<String> ids = new HashSet<>();
        final NodeList adaptationSets = period.getElementsByTagName("AdaptationSet");
        for (int i = 0; i < adaptationSets.getLength(); i++) {
            ids.add(((Element) adaptationSets.item(i)).getAttribute("id"));
        }
        return ids;
    }

    private static Element getFirstElementByTagName(final Document document, final String tag)
            throws IOException {
        final NodeList nodes = document.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            throw new IOException("No <" + tag + "> element found in generated manifest");
        }
        return (Element) nodes.item(0);
    }

    private static String serialize(final Document document) throws TransformerException {
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        final StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static String buildSingleTrackManifest(final Stream stream,
                                                     final StreamInfo streamInfo)
            throws CreationException, IOException {
        final ItagItem itagItem = Objects.requireNonNull(stream.getItagItem(),
                "Stream has no itag information, cannot generate a DASH manifest for it");

        switch (stream.getDeliveryMethod()) {
            case DASH:
                return YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(
                        stream.getContent(), itagItem, streamInfo.getDuration());
            case PROGRESSIVE_HTTP:
                return YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                        stream.getContent(), itagItem, streamInfo.getDuration());
            default:
                throw new IOException("Unsupported delivery method for casting: "
                        + stream.getDeliveryMethod());
        }
    }
}
