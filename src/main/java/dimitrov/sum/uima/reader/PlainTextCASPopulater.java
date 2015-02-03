package dimitrov.sum.uima.reader;

import org.apache.commons.io.FileUtils;
import org.apache.uima.collection.CollectionException;
import dimitrov.sum.uima.types.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Created by aleks on 20/01/15.
 */

public class PlainTextCASPopulater implements CasPopulater {
    protected static final Logger log = LoggerFactory.getLogger(PlainTextCASPopulater.class);

    private final Charset encoding;

    public PlainTextCASPopulater(final Charset encoding) {
        this.encoding = encoding;
    }

    @Override
    public void populateCAS(JCas cas, File fromFile) throws IOException, CollectionException {
        // FIXME: We just use the default encoding, which shouldn't be the case.
        final String fContents = FileUtils.readFileToString(fromFile, this.encoding);
        cas.setDocumentText(fContents);

        final SourceDocumentInformation srcInfo = new SourceDocumentInformation(cas);
        srcInfo.setUri(fromFile.getAbsoluteFile().toURI().toString());
        srcInfo.setDocumentSize(fromFile.length());
        srcInfo.addToIndexes();
    }
}

