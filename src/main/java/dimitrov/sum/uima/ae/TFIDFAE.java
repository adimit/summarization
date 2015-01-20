package dimitrov.sum.uima.ae;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

/**
 * Created by aleks on 20/01/15.
 */
public class TFIDFAE extends CasAnnotator_ImplBase {
    protected Logger log;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);

        log = context.getLogger();
        log.log(Level.INFO, "Initialized TFIDFAE.");
    }

    @Override
    public void process(CAS aCAS) throws AnalysisEngineProcessException {
        log.log(Level.INFO, "Starting processing of TFIDFAE.");
    }
}
