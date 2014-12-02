package dimitrov.sum;

import java.io.IOException;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author aleks
 *
 * We use a Singleton pattern to ensure the expensive models only get loaded
 * once, and are easily accessible by everybody.
 * 
 * Use the static method .getInstance() to retrieve the Singleton, and use its
 * methods to obtain the analysis engines.
 */
public final class SummarizationContext {

	private static SummarizationContext instance = null;
	private final AnalysisEngine opennlp;
	
	private static final String openNLPDesc = "/opennlp/OpenNlpTextAnalyzer.xml";
	
	private static final Logger log = LoggerFactory.getLogger(SummarizationContext.class);
		
	private SummarizationContext() throws IOException, ResourceInitializationException, InvalidXMLException 
	{
		log.info("Installing OpenNLP PEAR.");
		final ResourceManager rMgr =
				UIMAFramework.newDefaultResourceManager();
		final XMLInputSource in =
				new XMLInputSource(getClass().getResource(openNLPDesc));
		log.info("Creating OpenNLP AE.");
		final ResourceSpecifier specifier =
				UIMAFramework.getXMLParser().parseResourceSpecifier(in);
		
		opennlp = UIMAFramework.produceAnalysisEngine(specifier, rMgr, null);
		log.info("Finished initializing Context Singleton");
	}
	
	/**
	 * Retrieve the single instance of this class. This method is thread-safe.
	 * 
	 * @return SummarizationContext singleton.
	 * @throws Exception Cheap way out. Detailed exception handling NYI.
	 */
	public static synchronized SummarizationContext getInstance() 
			throws ContextInitializationException {
		if (instance == null) {
			try {
				instance = new SummarizationContext();
			} catch (Exception e) {
				throw new ContextInitializationException("Failed to initialize Context.",e);
			}
		}
		return instance;
	}
	
	public AnalysisEngine getOpenNLPAE() {
		return opennlp;
	}

}

