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
 * once, and are easily accessible by everybody. This goal also necessicates that
 * the class be final — if you want to load additional models, go make your won singleton.
 * 
 * Use the static method .getInstance() to retrieve the Singleton, and use its
 * non-static methods to obtain the analysis engines.
 *
 */
public final class SummarizationContext {

	private static SummarizationContext instance = null;
	private final AnalysisEngine opennlp;
	private final AnalysisEngine tfidfAE;
	
	private static final String openNLPDesc = "/opennlp/OpenNlpTextAnalyzer.xml";
	private static final String tfidfDesc = "/sum/TFIDFAE.xml";
	
	private static final Logger log = LoggerFactory.getLogger(SummarizationContext.class);
		
	private SummarizationContext() throws ContextInitializationException {
		final ResourceManager rMgr =
				UIMAFramework.newDefaultResourceManager();

		log.info("Creating OpenNLP AE.");
		opennlp = produceAE("OpenNLP", openNLPDesc, rMgr);

		log.info("Creating TF/IDF AE.");
		tfidfAE = produceAE("TF/IDF", tfidfDesc, rMgr);

		log.info("Finished initializing Context Singleton");

	}

	private AnalysisEngine produceAE(final String name, final String descriptor, final ResourceManager rMgr)
			throws ContextInitializationException {
		try {
			final XMLInputSource in =
					new XMLInputSource(getClass().getResource(descriptor));
			final ResourceSpecifier specifier =
					UIMAFramework.getXMLParser().parseResourceSpecifier(in);
			return UIMAFramework.produceAnalysisEngine(specifier, rMgr, null);
		} catch (IOException ioe) {
			throw new ContextInitializationException("Error accessing " + descriptor + " while building " + name, ioe);
		} catch (InvalidXMLException ixe) {
			throw new ContextInitializationException("Invalid XML in " + descriptor + " while building " + name, ixe);
		} catch (ResourceInitializationException rie) {
			throw new ContextInitializationException("Failed to produce AE " + name, rie);
		}
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
			log.debug("Instantiating new SummarizationContext.");
			instance = new SummarizationContext();
		}
		return instance;
	}
	
	public AnalysisEngine getOpenNLPAE() {
		return opennlp;
	}

	public AnalysisEngine getTfidfAE() {
		return tfidfAE;
	}
}
