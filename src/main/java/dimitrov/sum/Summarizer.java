package dimitrov.sum;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Summarizer {
	private static final Logger log = LoggerFactory.getLogger(Summarizer.class);

	public static void main(String[] args) {
		// initialize

		// Install the JUL-to-SLF4J bridge. This will handle the UIMA logs.
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		// get file set

		// make documents
		final List<Document> docs = new ArrayList<>();
		try {
			for (String arg:args) {
				log.info("Adding {}", arg);
				docs.add(new Document(new File(arg)));
			}
		} catch (DocumentInitializationException die) {
			throw new RuntimeException(die);
		}
		
		// analyse documents
		for (Document doc:docs) {
			try {
				log.info("Analyzing {}", doc.getName());
				doc.analyze();
				log.info("Summarizing {}", doc.getName());
				// summarise documents
				doc.summarize();
				log.info("Finished analysis of {}", doc.getName());
			} catch (AnalysisEngineProcessException aee) {
				throw new RuntimeException(aee);
			}
		}
		// output
	}
}
