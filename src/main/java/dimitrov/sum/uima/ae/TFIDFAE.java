package dimitrov.sum.uima.ae;

import dimitrov.sum.Summarizer;
import dimitrov.sum.TermFrequencies;
import dimitrov.sum.uima.Names;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

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

    private long totalDocumentCount;

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
        totalDocumentCount = documentFrequencies.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(TermFrequency.TermFreqRecord::getDocumentURI))
                .distinct().count();

        log = context.getLogger();
        log.log(Level.INFO, "Initialized TFIDFAE.");
    }

    @Override
    public void process(CAS aCAS) throws AnalysisEngineProcessException {
        log.log(Level.INFO, "Starting processing of TFIDFAE.");
        final Map<String, Double> tfidfs = new HashMap<>();
        final FSIndex<AnnotationFS> termIndex = aCAS.getAnnotationIndex(termFrequencyType);
        termIndex.forEach(term -> putTFIDF(term, aCAS, tfidfs));
        final FSIndex<AnnotationFS> tokenIndex = aCAS.getAnnotationIndex(tokenType);
        tokenIndex.forEach(token -> setTFIDF(tfidfs, token));
    }

    private void setTFIDF(Map<String, Double> tfidfs, AnnotationFS token) {
        final Double tfidf = tfidfs.getOrDefault(token.getCoveredText(), 0d);
        token.setDoubleValue(tfidfFeature, tfidf);
    }

    private void putTFIDF(final AnnotationFS term, final CAS aCAS, final Map<String, Double> tfidfs) {
        final int frequency = term.getIntValue(termFrequencyFeature);
        final String surface = term.getStringValue(termSurfaceFeature);
        final Double tfidf = computeTFIDF(frequency, docFreqOf(surface));
        if (tfidf < 0) {
            log.log(Level.WARNING, "Negative TFIDF for " + surface + ": " + tfidf);
        }
        tfidfs.put(surface, tfidf);
    }

    private Double computeTFIDF(int termFrequency, long documentFrequency) {
        final double idf = Math.log10((double)totalDocumentCount / (double)documentFrequency);
        return (double)termFrequency * idf;
    }

    private long docFreqOf(final String t) {
        return documentFrequencies.get(t).orElse(new LinkedList<>()).stream().count();
    }

    @Override
    public void typeSystemInit(TypeSystem typeSystem) throws AnalysisEngineProcessException {
        log.log(Level.INFO, "Initializing type system.");
        tokenType = AnnotatorUtil.getRequiredTypeParameter(this.context, typeSystem, UimaUtil.TOKEN_TYPE_PARAMETER);
        termFrequencyType = AnnotatorUtil.getRequiredTypeParameter
                (this.context, typeSystem, Names.TERM_FREQUENCY_TYPE_PARAMETER);
        termFrequencyFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.termFrequencyType,
                Names.TERM_FREQUENCY_FEATURE_PARAMETER, CAS.TYPE_NAME_INTEGER);
        termSurfaceFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.termFrequencyType,
                Names.TERM_SURFACE_FEATURE_PARAMETER, CAS.TYPE_NAME_STRING);
        tfidfFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.tokenType,
                Names.TFIDF_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
        log.log(Level.FINE, "Finished initializing type system.");
    }
}
