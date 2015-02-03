package dimitrov.sum.uima;

import org.apache.uima.cas.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by aleks on 18/01/15.
 */
public class LocalSourceInfo {
    protected static final Logger log = LoggerFactory.getLogger(LocalSourceInfo.class);

    private URI uri;
    public final int documentSize;
    public final boolean documentIsGeneric;

    private static int genericFileCounter = 1;
    private static final Set<String> xmiNames = new HashSet<>();

    public LocalSourceInfo(final CAS cas) {

        final Type srcDocInfoType = cas.getTypeSystem()
                .getType("dimitrov.sum.uima.types.SourceDocumentInformation");
        FSIterator<FeatureStructure> it = null;
        if (srcDocInfoType != null) {
            it = cas.getIndexRepository().getAllIndexedFS(srcDocInfoType);
        } else {
            log.error("CAS type system doesn't know about SourceDocInfo. Something is very, VERY fishy! Proceeding anyway");
        }
        if (it != null && it.hasNext()) {
            final FeatureStructure srcDocInfoFs = it.get();

            final Feature uriFeat = srcDocInfoType.getFeatureByBaseName("uri");
            final Feature documentSizeFeat = srcDocInfoType.getFeatureByBaseName("documentSize");

            final String uriString = srcDocInfoFs.getStringValue(uriFeat);
            try {
                this.uri = new URI(uriString);
            } catch (URISyntaxException e) {
                this.uri = makeGenericURI();
                log.error("Malformed URI in SourceDocInfo: {}", uriString);
            }

            this.documentSize = srcDocInfoFs.getIntValue(documentSizeFeat);
            this.documentIsGeneric = false;
        } else {
            this.uri = makeGenericURI();
            this.documentSize = 0;
            this.documentIsGeneric = true;
        }
    }

    private synchronized URI makeGenericURI() {
        final String uri = "file://UnknownFile_" + genericFileCounter++;
        log.warn("Encountered CAS without, or with erroneous SourceDocInfo annotation. Making up name: {}", uri);
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Too dumb to make URI. I shall go kill myself now.");
        }
    }

    public URI getUri() { return this.uri; }

    public String generateXmiFileName() { return generateXmiFileName(this.uri); }

    public static String generateXmiFileName(final URI uri) {
        final File temp = new File(uri.getPath());
        String fName = temp.getName();
        while (xmiNames.contains(fName)) {
            fName += "_";
            log.warn("Renaming because of name collision: {}", fName);
        }
        xmiNames.add(fName);
        return fName + ".xmi";
    }

    /**
     * This is a hack, and a bad one. Ideally, we'd have access to the Phase settings here, which probably means
     * we should make {@link dimitrov.sum.uima.LocalSourceInfo} uninstantiable and use a LocalSourceFactory instead,
     * but I'm too lazy to implement that right now.
     *
     * As it stands this *needs* to be called between phases, otherwise we'll have to rename every file that was
     * already produced in an earlier phase, because the static set of names has already been filled.
     */
    public static void phaseComplete() { xmiNames.clear(); }
}
