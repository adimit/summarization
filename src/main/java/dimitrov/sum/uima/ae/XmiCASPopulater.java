package dimitrov.sum.uima.ae;

import org.apache.uima.cas.CAS;
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
public class XmiCASPopulater implements CasPopulater {

    protected static final Logger log = LoggerFactory.getLogger(XmiCASPopulater.class);

    private final boolean lenient;
    public XmiCASPopulater(final boolean allowTypeErrors) {
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
    }
}
