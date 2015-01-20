package dimitrov.sum.uima.ae;

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;

/**
 * Created by aleks on 20/01/15.
 */
public interface CasPopulater {
    public void populateCAS(JCas cas, File fromfile) throws IOException, CollectionException;
}
