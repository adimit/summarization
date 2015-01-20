package dimitrov.sum.uima.ae;

import dimitrov.sum.Summarizer;
import dimitrov.sum.TermFrequencies;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import java.io.*;

/**
 * Created by aleks on 20/01/15.
 */
public class TFIDFAE extends CasAnnotator_ImplBase {
    protected Logger log;

    private TermFrequencies<String,TermFrequency.TermFreqRecord> documentFrequencies;

    @Override
    @SuppressWarnings("unchecked") // Since we're deserializing a collection.
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        log = context.getLogger();

        // Deserialize the term frequencies from a file
        try (final InputStream file = new FileInputStream(Summarizer.termFrequencySerializationFile);
             final InputStream buffer = new BufferedInputStream(file);
             final ObjectInput input = new ObjectInputStream(buffer)
        ) {
            log.log(Level.INFO, "Attempting to deserialize term frequency data");
            final Object tempObject = input.readObject();
            documentFrequencies = (TermFrequencies) tempObject;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not read from file " + Summarizer.termFrequencySerializationFile);
            throw new ResourceInitializationException(e);
        } catch (ClassNotFoundException e) {
            log.log(Level.SEVERE, "Could not find class of object in file " + Summarizer.termFrequencySerializationFile);
            throw new ResourceInitializationException(e);
        }

        log.log(Level.INFO, "Got termFrequencies with " + documentFrequencies.entrySet().size() + " entries.");

        log = context.getLogger();
        log.log(Level.INFO, "Initialized TFIDFAE.");
    }

    @Override
    public void process(CAS aCAS) throws AnalysisEngineProcessException {
        log.log(Level.INFO, "Starting processing of TFIDFAE.");
    }
}
