package dimitrov.sum.uima;

import org.apache.uima.cas.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by aleks on 18/01/15.
 */
public class LocalSourceInfo {
    protected static final Logger log = LoggerFactory.getLogger(LocalSourceInfo.class);

    private URI uri;
    public final int documentSize;
    public final int offsetInSource;
    public final boolean documentIsFinal;
    public final boolean documentIsGeneric;

    private static int genericFileCounter = 1;

    public LocalSourceInfo(final CAS cas) {

        final Type srcDocInfoType = cas.getTypeSystem()
                .getType("org.apache.uima.examples.SourceDocumentInformation");
        FSIterator<FeatureStructure> it = null;
        if (srcDocInfoType != null) {
            it = cas.getIndexRepository().getAllIndexedFS(srcDocInfoType);
        } else {
            log.error("CAS type system doesn't know about SourceDocInfo. Something is very, VERY fishy! Proceeding anyway");
        }
        if (it != null && it.hasNext()) {
            final FeatureStructure srcDocInfoFs = it.get();

            final Feature uriFeat = srcDocInfoType.getFeatureByBaseName("uri");
            final Feature offsetInSourceFeat = srcDocInfoType.getFeatureByBaseName("offsetInSource");
            final Feature documentSizeFeat = srcDocInfoType.getFeatureByBaseName("documentSize");
            final Feature lastSegmentFeat = srcDocInfoType.getFeatureByBaseName("lastSegment");

            final String uriString = srcDocInfoFs.getStringValue(uriFeat);
            try {
                this.uri = new URI(uriString);
            } catch (URISyntaxException e) {
                this.uri = makeGenericURI();
                log.error("Malformed URI in SourceDocInfo: {}", uriString);
            }

            this.offsetInSource = srcDocInfoFs.getIntValue(offsetInSourceFeat);
            this.documentSize = srcDocInfoFs.getIntValue(documentSizeFeat);
            this.documentIsFinal = srcDocInfoFs.getBooleanValue(lastSegmentFeat);
            this.documentIsGeneric = false;
        } else {
            this.uri = makeGenericURI();
            this.documentSize = 0;
            this.offsetInSource = 0;
            this.documentIsFinal = false;
            this.documentIsGeneric = true;
        }
    }

    private synchronized URI makeGenericURI() {
        final String uri = "file://UnknownFile_" + genericFileCounter++;
        log.warn("Encountered CAS without SourceDocInfo annotation. Making up name: {}", uri);
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Too dumb to make URI. I shall go kill myself now.");
        }
    }

    public URI getUri() { return this.uri; }

    public String generateXmiFileName() {
        return generateXmiFileName(this.uri, this.offsetInSource);
    }

    public static String generateXmiFileName(final URI uri, final int offsetInSource) {
        final File temp = new File(uri.getPath());
        final String offset = offsetInSource == 0 ? "" : "_" + offsetInSource;
        return temp.getName() + offset + ".xmi";
    }
}
