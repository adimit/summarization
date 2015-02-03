package dimitrov.sum.uima.reader;

import dimitrov.sum.uima.SummarizerUtil;
import dimitrov.sum.uima.types.SourceDocumentInformation;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by aleks on 20/01/15.
 */
public class XmiCASPopulater extends CasPopulater {

    protected static final Logger log = LoggerFactory.getLogger(XmiCASPopulater.class);

    private final boolean lenient;
    public XmiCASPopulater(final boolean allowTypeErrors, File inputDirectory, File outputDirectory) {
        super(inputDirectory, outputDirectory);
        this.lenient = allowTypeErrors;
    }

    @Override
    public void populateCAS(JCas cas, File fromfile) throws IOException, CollectionException {
        try (FileInputStream fis = new FileInputStream(fromfile)) {
            XmiCasDeserializer.deserialize(fis, cas.getCas(), lenient);
        } catch (SAXException e) {
            log.error("Failed to parse XMI: {}", e.getMessage());
            throw new CollectionException(e);
        }
        final SourceDocumentInformation srcDocInfo = SummarizerUtil.getJCasSourceDocumentInformation(cas);
        srcDocInfo.setOutputTarget(makeOutputPath(fromfile));
    }
}
