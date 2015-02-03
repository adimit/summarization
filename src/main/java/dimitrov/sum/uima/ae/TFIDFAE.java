package dimitrov.sum.uima.ae;

import dimitrov.sum.Summarizer;
import dimitrov.sum.TermFrequencies;
import dimitrov.sum.uima.Log10TFIDF;
import dimitrov.sum.uima.SummarizerUtil;
import dimitrov.sum.uima.TFIDFComputer;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.*;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import java.io.*;
import java.util.*;

/**
 * Created by aleks on 20/01/15.
 */
public class TFIDFAE extends CasAnnotator_ImplBase {
    protected Logger log;

    private UimaContext context;
    private Type tokenType;
    private Type termFrequencyType;
    private Feature termFrequencyFeature;
    private Feature termSurfaceFeature;
    private TermFrequencies<String,TermFrequency.TermFreqRecord> documentFrequencies;
    private Feature tfidfFeature;

    private TFIDFComputer tfidfComputer;

    @Override
    @SuppressWarnings("unchecked") // Since we're deserializing a collection.
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        log = context.getLogger();
        this.context = context;

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
        final long totalDocumentCount = documentFrequencies.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(TermFrequency.TermFreqRecord::getDocumentURI))
                .distinct().count();

        this.tfidfComputer = new Log10TFIDF(totalDocumentCount);

        log = context.getLogger();
        log.log(Level.INFO, "Initialized TFIDFAE.");
    }

    @Override
    public void process(CAS aCAS) throws AnalysisEngineProcessException {
        log.log(Level.INFO, "Starting processing of TFIDFAE.");
        final Map<String, Double> tfidfs = new HashMap<>();
        final FSIndex<AnnotationFS> termIndex = aCAS.getAnnotationIndex(termFrequencyType);
        termIndex.forEach(term -> computeTFIDF(term, tfidfs));
        final FSIndex<AnnotationFS> tokenIndex = aCAS.getAnnotationIndex(tokenType);
        tokenIndex.forEach(token -> setTFIDFFeature(tfidfs, token));
    }

    private void setTFIDFFeature(Map<String, Double> tfidfs, AnnotationFS token) {
        final Double tfidf = tfidfs.get(token.getCoveredText());
        if (tfidf == null) {
            log.log(Level.SEVERE, "Could not get tfidf for token: " + token.getCoveredText());
            // setting it to one: if it's not in the doc collection, it has to be at least a little special.
            token.setDoubleValue(tfidfFeature, 1d);
        } else {
            token.setDoubleValue(tfidfFeature, tfidf);
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "tfidf for "+token.getCoveredText()+" is "+tfidf+".");
            }
        }
    }

    private void computeTFIDF(final AnnotationFS term, final Map<String, Double> tfidfs) {
        final String surface = term.getStringValue(termSurfaceFeature);
        final int termFrequency = term.getIntValue(termFrequencyFeature);
        final Optional<List<TermFrequency.TermFreqRecord>> termFreqRecords = documentFrequencies.get(surface);
        final long documentFrequency;
        if (termFreqRecords.isPresent())
            documentFrequency = termFreqRecords.get().size();
        else
            documentFrequency = 0;
        final Double tfidf = tfidfComputer.computeTFIDF(termFrequency, documentFrequency);
        tfidfs.put(surface, tfidf);
    }

    @Override
    public void typeSystemInit(TypeSystem typeSystem) throws AnalysisEngineProcessException {
        log.log(Level.INFO, "Initializing type system.");
        tokenType = AnnotatorUtil.getRequiredTypeParameter(this.context, typeSystem, UimaUtil.TOKEN_TYPE_PARAMETER);
        termFrequencyType = AnnotatorUtil.getRequiredTypeParameter
                (this.context, typeSystem, SummarizerUtil.TERM_TYPE_PARAMETER);
        termFrequencyFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.termFrequencyType,
                SummarizerUtil.TERM_FREQUENCY_FEATURE_PARAMETER, CAS.TYPE_NAME_INTEGER);
        termSurfaceFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.termFrequencyType,
                SummarizerUtil.TERM_SURFACE_FEATURE_PARAMETER, CAS.TYPE_NAME_STRING);
        tfidfFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.tokenType,
                SummarizerUtil.TFIDF_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
        log.log(Level.FINE, "Finished initializing type system.");
    }
}
