package dimitrov.sum.uima.ae;

import dimitrov.sum.Summarizer;
import dimitrov.sum.TermFrequencies;
import dimitrov.sum.uima.Log10TFIDF;
import dimitrov.sum.uima.TFIDFComputer;
import dimitrov.sum.uima.types.Term;
import dimitrov.sum.uima.types.Token;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by aleks on 03/02/15.
 */
public class TFIDFAE extends JCasAnnotator_ImplBase {
    protected static final Logger log = LoggerFactory.getLogger(TFIDFAE.class);

    private TermFrequencies<String,TermFrequency.TermFreqRecord> documentFrequencies;
    private TFIDFComputer tfidfComputer;

    /**
     * This method should be overriden by subclasses. Inputs a JCAS to the AnalysisComponent. The
     * AnalysisComponent "owns" this JCAS until such time as {@link #hasNext()} is called and returns
     * false (see {@link org.apache.uima.analysis_component.AnalysisComponent} for details).
     *
     * @param aJCas a JCAS that this AnalysisComponent should process.
     * @throws org.apache.uima.analysis_engine.AnalysisEngineProcessException if a problem occurs during processing
     */
    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        log.info("Starting processing of TFIDFAE");
        final Map<String, Double> tfidfs = new HashMap<>();
        JCasUtil.iterator(aJCas, Term.class)
                .forEachRemaining(term -> {
                    final Double tfidf = computeTFIDF(term, tfidfs);
                    final FSArray observations = term.getObservations();
                    // FSArrays are not collections :-(
                    for (int i = 0; i < observations.size(); i++) {
                        final Token t = (Token) observations.get(i);
                        t.setTfidf(tfidf);
                    }
                });
    }

    private Double computeTFIDF(final Term term, final Map<String, Double> tfidfs) {
        final String surface = term.getSurface();
        final int termFrequency = term.getCasFrequency();
        final Optional<List<TermFrequency.TermFreqRecord>> termFreqRecords = documentFrequencies.get(surface);
        final long documentFrequency;
        if (termFreqRecords.isPresent())
            documentFrequency = termFreqRecords.get().size();
        else
            documentFrequency = 0;

        final Double tfidf = tfidfComputer.computeTFIDF(termFrequency, documentFrequency);
        tfidfs.put(surface, tfidf);
        return tfidf;
    }

    @Override
    @SuppressWarnings("unchecked") // Since we're reading a Collection as an object file.
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        // Deserialize the term frequencies from a file
        try (final InputStream file = new FileInputStream(Summarizer.termFrequencySerializationFile);
             final InputStream buffer = new BufferedInputStream(file);
             final ObjectInput input = new ObjectInputStream(buffer)
        ) {
            log.info("Attempting to deserialize term frequency data");
            final Object tempObject = input.readObject();
            documentFrequencies = (TermFrequencies) tempObject;
        } catch (IOException e) {
            log.error("Could not read from file {}.", Summarizer.termFrequencySerializationFile);
            throw new ResourceInitializationException(e);
        } catch (ClassNotFoundException e) {
            log.error("Could not find class of object in file {}.", Summarizer.termFrequencySerializationFile);
            throw new ResourceInitializationException(e);
        }

        log.info("Got termFrequencies with {} entries.", documentFrequencies.entrySet().size());
        final long totalDocumentCount = documentFrequencies.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(TermFrequency.TermFreqRecord::getDocumentURI))
                .distinct().count();
        this.tfidfComputer = new Log10TFIDF(totalDocumentCount);
    }
}
