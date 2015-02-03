package dimitrov.sum.uima;

import dimitrov.sum.uima.types.SourceDocumentInformation;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;

/**
 * Created by aleks on 05/12/14.
 */
public final class SummarizerUtil {
    public static final String TERM_TYPE_PARAMETER = "dimitrov.sum.TermType";
    public static final String TERM_FREQUENCY_FEATURE_PARAMETER = "dimitrov.sum.TermFrequencyFeature";
    public static final String TERM_SURFACE_FEATURE_PARAMETER = "dimitrov.sum.TermSurfaceFeature";
    public static final String TERM_OBSERVATIONS_FEATURE_PARAMETER = "dimitrov.sum.TermObservationsFeature";
    public static final String TFIDF_FEATURE_PARAMETER = "dimitrov.sum.TFIDFFeature";

    private SummarizerUtil() { }

    public static SourceDocumentInformation getJCasSourceDocumentInformation(final JCas cas) {
        final Collection<SourceDocumentInformation> sourceDocInfos =
                JCasUtil.select(cas, SourceDocumentInformation.class);
        if (sourceDocInfos.size() != 1) {
            throw new RuntimeException("No, or more than one SourceDocumentInformation found in CAS.");
        }
        return sourceDocInfos.iterator().next();
    }
}
