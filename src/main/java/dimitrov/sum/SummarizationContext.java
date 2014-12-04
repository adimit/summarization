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
		
	private SummarizationContext() throws ContextInitializationException {
		log.info("Creating OpenNLP AE.");
		try {
			final ResourceManager rMgr =
					UIMAFramework.newDefaultResourceManager();
			final XMLInputSource in =
					new XMLInputSource(getClass().getResource(openNLPDesc));
			final ResourceSpecifier specifier =
					UIMAFramework.getXMLParser().parseResourceSpecifier(in);

			opennlp = UIMAFramework.produceAnalysisEngine(specifier, rMgr, null);
		} catch (IOException ioe) {
			throw new ContextInitializationException("Error accessing " + openNLPDesc,ioe);
		} catch (InvalidXMLException ixe) {
			throw new ContextInitializationException("Invalid XML in " + openNLPDesc,ixe);
		} catch (ResourceInitializationException rie) {
			throw new ContextInitializationException("Failed to produce analysis engine.",rie);
		}
		log.info("Finished initializing Context Singleton");
	}
	
	/**
	 * Retrieve the single instance of this class. This method is thread-safe,
	 * but may block during creation of the analysis engines, which may be
	 * lengthy. However, they are only created once, during the first call
	 * to getInstance.
	 *
	 * @return SummarizationContext singleton.
	 * @throws dimitrov.sum.ContextInitializationException
	 */
	public static synchronized SummarizationContext getInstance()
			throws ContextInitializationException {
		if (instance == null) {
			instance = new SummarizationContext();
		}
		return instance;
	}
	
	public AnalysisEngine getOpenNLPAE() {
		return opennlp;
	}

}

