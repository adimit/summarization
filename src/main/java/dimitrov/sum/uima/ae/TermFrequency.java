package dimitrov.sum.uima.ae;

import dimitrov.sum.Summarizer;
import dimitrov.sum.TermFrequencies;
import dimitrov.sum.uima.LocalSourceInfo;
import dimitrov.sum.uima.Names;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.*;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by aleks on 05/12/14.
 */
public class TermFrequency extends CasAnnotator_ImplBase {

    protected UimaContext context;
    protected Logger log;

    private Type tokenType;
    private Type termFrequencyType;
    private Feature termFrequencyFeature;
    private Feature termSurfaceFeature;
    private Feature termObservationsFeature;

    private TermFrequencies<String,TermFreqRecord> documentFrequencies;


    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.log = context.getLogger();
        log.log(Level.INFO, "Initializing Term Frequency AE.");
        this.context = context;
        documentFrequencies = new TermFrequencies<>();
    }

    /**
     * Notifies this AnalysisComponent that its configuration parameters have changed. This
     * implementation just calls {@link #destroy()} followed by {@link #initialize(org.apache.uima.UimaContext)}. Subclasses can
     * override to provide more efficient reconfiguration logic if necessary.
     *
     * @see org.apache.uima.analysis_component.AnalysisComponent#reconfigure()
     */
    @Override
    public void reconfigure() throws ResourceConfigurationException, ResourceInitializationException {
        log.log(Level.WARNING, "Reconfiguring Term Frequency AE. There is nothing to reconfigure.");
    }

    /**
     * Inputs a CAS to the AnalysisComponent. This method should be overriden by subclasses to perform
     * analysis of the CAS.
     *
     * @param aCAS A CAS that this AnalysisComponent should process.
     * @throws org.apache.uima.analysis_engine.AnalysisEngineProcessException if a problem occurs during processing
     */
    @Override
    public void process(CAS aCAS) throws AnalysisEngineProcessException {
        final TermFrequencies<String, AnnotationFS> tf = new TermFrequencies<>();
        final LocalSourceInfo sourceInfo = new LocalSourceInfo(aCAS);
        log.log(Level.INFO, "Starting Term Frequency annotation.");
        final FSIndex<AnnotationFS> tokens = aCAS.getAnnotationIndex(tokenType);
        tokens.forEach(token -> tf.observe(token.getCoveredText(), token));
        tf.entrySet().forEach(observation ->
                recordObservationInCas(aCAS, observation.getKey(), observation.getValue(),
                        sourceInfo.getUri().toASCIIString()));
        log.log(Level.INFO, "Finished Term Frequency annotation.");
    }

    private void recordObservationInCas(final CAS aCAS, final String term,
                                        final List<AnnotationFS> observations, final String docUri) {
        // new Feature structure
        final AnnotationFS tfAnnotation = aCAS.createAnnotation(termFrequencyType,0,0);
        // Set term
        tfAnnotation.setStringValue(termSurfaceFeature, term);
        // Set frequency
        final int frequency = observations.size();
        tfAnnotation.setIntValue(termFrequencyFeature, frequency);
        final int numObs = observations.size();
        final ArrayFS observationsFS = aCAS.createArrayFS(numObs);
        final FeatureStructure[] observationsArr = new FeatureStructure[numObs];
        observations.toArray(observationsArr);
        observationsFS.copyFromArray(observationsArr,0,0,numObs);
        tfAnnotation.setFeatureValue(termObservationsFeature, observationsFS);
        aCAS.addFsToIndexes(tfAnnotation);
        documentFrequencies.observe(term,new TermFreqRecord(numObs, docUri));
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
        termObservationsFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.termFrequencyType,
                Names.TERM_OBSERVATIONS_FEATURE_PARAMETER, CAS.TYPE_NAME_FS_ARRAY);
        log.log(Level.INFO, "Finished initializing type system.");
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        log.log(Level.INFO, "COLLECTION PROCESS COMPLETE.");
        final Collection<Map.Entry<String,List<TermFreqRecord>>> docFreqs = documentFrequencies.entrySet();
        final int totalNumberOfTerms = docFreqs.size();
        final int totalNumberOfObservations = docFreqs.stream()
                .flatMap(entry -> entry.getValue().stream().map(TermFreqRecord::getObservations))
                .reduce(0,(a,b) -> a + b);
        log.log(Level.INFO, "Observed " + totalNumberOfTerms
                + " distinct terms over " + totalNumberOfObservations + " observations.");

        // Serialize the term frequencies to a file
        try (final OutputStream file = new FileOutputStream(Summarizer.termFrequencySerializationFile);
             final OutputStream buffer = new BufferedOutputStream(file);
             final ObjectOutput output = new ObjectOutputStream(buffer)
        ) {
            output.writeObject(documentFrequencies);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not write to file " + Summarizer.termFrequencySerializationFile);
            throw new AnalysisEngineProcessException(e);
        }
    }

    public static class TermFreqRecord implements Serializable {
        public final static long serialVersionUID = 1L;
        final int observations;
        final String documentURI;

        TermFreqRecord(final int observations, final String documentURI) {
            this.observations = observations;
            this.documentURI = documentURI;
        }

        public String getDocumentURI() { return documentURI; }
        int getObservations() { return observations; }
    }
}
