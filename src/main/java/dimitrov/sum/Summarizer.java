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
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		// analyse documents
		for (Document doc:docs) {
			try {
				doc.analyze();
			} catch (AnalysisEngineProcessException aee) {
				throw new RuntimeException(aee);
			}
		}
		// summarise documents
		// output
	}

}
