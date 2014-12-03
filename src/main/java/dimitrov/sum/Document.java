package dimitrov.sum;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

public class Document {
	private final JCas cas;
	private final SummarizationContext cx;
	private final AnalysisEngine opennlpae;
	private final String name;

	public Document(File f) throws DocumentInitializationException {
		try {
			this.name = f.getName();
			cx = SummarizationContext.getInstance();
			opennlpae = cx.getOpenNLPAE();
			cas = opennlpae.newJCas();
			/* FIXME: we just take the default charset,
			 * but this should be configurable. */
			cas.setDocumentText(FileUtils.readFileToString(f, Charset.defaultCharset()));
		} catch (IOException ioe) {
			throw new DocumentInitializationException("Error accessing file " + f, ioe);
		} catch (ContextInitializationException cie) {
			throw new DocumentInitializationException("Error accessing context.", cie);
		} catch (ResourceInitializationException rie) {
			throw new DocumentInitializationException("Couldn't create JCas.", rie);
		}
	}

	public void analyze() throws AnalysisEngineProcessException {
		opennlpae.process(cas);
	}

	public String summarize() {
		return null;
	}

	public String getName() {
		return this.name;
	}
}
