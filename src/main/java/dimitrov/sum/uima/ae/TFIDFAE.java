package dimitrov.sum.uima.ae;

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
import org.apache.uima.util.Logger;

/**
 * Created by aleks on 05/12/14.
 */
public class TFIDFAE extends CasAnnotator_ImplBase {

    protected UimaContext context;
    protected Logger log;

    private Type tokenType;
    private Feature tfidfFeature;
    private TermFrequencies<String> tf;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.context = context;
        this.log = context.getLogger();
        this.tf = new TermFrequencies<>();
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
        final FSIndex<AnnotationFS> tokens = aCAS.getAnnotationIndex(tokenType);

        for (AnnotationFS token:tokens) {
            tf.observe(token.getCoveredText());
            token.setDoubleValue(this.tfidfFeature, 0.1);
        }
    }

    @Override
    public void typeSystemInit(TypeSystem typeSystem) throws AnalysisEngineProcessException {
        tokenType = AnnotatorUtil.getRequiredTypeParameter(this.context, typeSystem, UimaUtil.TOKEN_TYPE_PARAMETER);
        tfidfFeature = AnnotatorUtil.getRequiredFeatureParameter(this.context, this.tokenType,
                Names.TFIDF_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
    }
}
