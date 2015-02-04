package dimitrov.sum.uima.ae;

import dimitrov.sum.uima.types.SenseSynset;
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
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        final Map<IndexWord, FSArray> senseFlyweights = new HashMap<>();
        final FSArray emptySenses = new FSArray(aJCas, 0);

        JCasUtil.iterator(aJCas, Token.class).forEachRemaining(token -> {
            try {
                final IndexWordSet indexWords = dictionary.lookupAllIndexWords(token.getCoveredText());
                final IndexWord indexWord = disambiguate(indexWords, token.getPos());

                if (indexWord == null) {
                    token.setSynsets(emptySenses);
                } else {
                    final FSArray cachedFS = senseFlyweights.get(indexWord);
                    if (cachedFS == null) {
                        final List<Synset> senses = indexWord.getSenses();
                        final FSArray sensesFS = new FSArray(aJCas, senses.size());
                        int i = 0;
                        for (Synset synset:senses) {
                            final List<Word> wordList = synset.getWords();
                            final String[] words = wordList.stream().map(Word::getLemma).toArray(String[]::new);
                            final StringArray wordsFS  = new StringArray(aJCas, wordList.size());
                            wordsFS.copyFromArray(words, 0,0, words.length);
                            final SenseSynset senseSynsetFS = new SenseSynset(aJCas);
                            senseSynsetFS.setSynset(wordsFS);
                            senseSynsetFS.addToIndexes();
                            sensesFS.set(i, senseSynsetFS);
                            i++;
                        }
                        log.debug("Generated new synset for {}.", token.getCoveredText());
                        token.setSynsets(sensesFS);
                        senseFlyweights.put(indexWord, sensesFS);
                    } else {
                        log.debug("Using cached synsets for {}.", token.getCoveredText());
                        token.setSynsets(cachedFS);
                    }
                }
            } catch (JWNLException e) {
                log.error("Failed to generate synset for '{}'.", token.getCoveredText(), e);
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

    private IndexWord disambiguate(IndexWordSet indexWords, String openNLPPos) {
        final POS jwnlPOS = translatePOS(openNLPPos);
        if (jwnlPOS == null || indexWords.size() == 0)
            return null;
        else
            return indexWords.getIndexWord(jwnlPOS);
    }
}
