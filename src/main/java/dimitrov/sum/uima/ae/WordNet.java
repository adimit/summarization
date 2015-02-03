package dimitrov.sum.uima.ae;

import dimitrov.sum.uima.types.Token;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.*;
import net.sf.extjwnl.dictionary.Dictionary;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

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
        JCasUtil.iterator(aJCas, Token.class).forEachRemaining(token -> {
            try {
                final IndexWordSet indexWords = dictionary.lookupAllIndexWords (token.getCoveredText());
                final List<Synset> senses = disambiguate(indexWords, token.getPos());
                final String[] words;
                if (senses == null) {
                    // If WordNet draws a blank, just use the word as-is
                    log.debug("WordNet doesn't know about '{}'. No synset generated.", token.getCoveredText());
                    words = new String[]{ token.getCoveredText() };
                } else {
                    // We don't do word sense disambiguation. Just concatenate the synsets. Yup, that's bad.
                    if (log.isDebugEnabled() && senses.size() > 1) {
                        log.warn("Token '{}' has more than one sense. WSD NYI!", token.getCoveredText());
                    }

                    List<String> bigSynset = new LinkedList<String>();
                    senses.forEach(synset -> synset.getWords().forEach(word -> bigSynset.add(word.getLemma())));
                    words = new String[bigSynset.size()];
                    bigSynset.toArray(words);
                    log.debug("Synset for '{}': {}.", token.getCoveredText(), words);
                }
                final StringArray stringArray = new StringArray(aJCas, words.length);
                stringArray.copyFromArray(words, 0, 0, words.length);
                token.setSynset(stringArray);
            } catch (JWNLException e) {
                log.warn("Failed to generate synset for '{}'.", token.getCoveredText(), e);
            }
        });
    }

    // OpenNLP tagset is the Penn Treebank tagset.
    private POS translatePOS(String openNLPPos) {
        if (openNLPPos.startsWith("J"))
            return POS.ADJECTIVE;
        else if (openNLPPos.startsWith("V"))
            return POS.VERB;
        else if (openNLPPos.startsWith("N"))
            return POS.NOUN;
        else if (openNLPPos.startsWith("RB"))
            return POS.ADVERB;
        else
            return null;
    }

    private List<Synset> disambiguate(IndexWordSet indexWords, String openNLPPos) {
        if (indexWords.size() == 1) {
            return indexWords.getIndexWordArray()[0].getSenses();
        } else if (indexWords.size() == 0) {
            return null;
        } else {
            final POS jwnlPOS = translatePOS(openNLPPos);
            if (jwnlPOS == null) {
                return null;
            }

            final IndexWord iw = indexWords.getIndexWord(jwnlPOS);
            if (iw == null) {
                return null;
            } else {
                return iw.getSenses();
            }
        }
    }
}
