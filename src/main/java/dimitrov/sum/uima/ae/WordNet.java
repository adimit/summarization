package dimitrov.sum.uima.ae;

import net.sf.extjwnl.dictionary.Dictionary;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by aleks on 25/01/15.
 */
public class WordNet extends JCasAnnotator_ImplBase{
    public static final String WORD_NET_RESOURCE_KEY = "WordNetDictionary";
    @ExternalResource(key=WORD_NET_RESOURCE_KEY)
    private WordNetModelResource wordNetModelResource;

    protected static final Logger log = LoggerFactory.getLogger(WordNet.class);

    private Dictionary dictionary;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.dictionary = wordNetModelResource.getDictionary();
        log.info("Loaded WordNet dictionary version {}.", dictionary.getVersion());
    }

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
        log.info("WordNet annotation engine processing…");
    }
}
