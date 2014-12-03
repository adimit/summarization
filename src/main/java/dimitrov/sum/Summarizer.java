package dimitrov.sum;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Summarizer {
	private static final Logger log = LoggerFactory.getLogger(Summarizer.class);
	public static void main(String[] args) {
		// get file set

		// make documents
		final List<Document> docs = new ArrayList<Document>();
		try {
			for (int i = 0; i<args.length; i++) {
				log.debug("Adding {}", args[i]);
				docs.add(new Document(new File(args[i])));
			}
		} catch (DocumentInitializationException die) {
			throw new RuntimeException(die);
		}
		
		// analyse documents
		for (Document doc:docs) {
			try {
				log.debug("Analyzing {}", doc.getName());
				doc.analyze();
				log.debug("Summarizing {}", doc.getName());
				// summarise documents
				doc.summarize();
				log.debug("Finished analysis of {}", doc.getName());
			} catch (AnalysisEngineProcessException aee) {
				throw new RuntimeException(aee);
			}
		}
		// output
	}

}
