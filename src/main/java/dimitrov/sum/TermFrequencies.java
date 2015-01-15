package dimitrov.sum;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by aleks on 05/12/14.
 */
public class TermFrequencies<T> {
    private final Map<T,Integer> tf;

    // TODO: write tests for this class.
    public TermFrequencies() {
        tf = new HashMap<>();
    }

    /**
     * Observe a singular occurrence of a term <code>t</code>.
     *
     * @param t
     */
    public void observe(T t) {
        tf.compute(t, (k,v) -> (v==null) ? 1 : v + 1);
    }

    public void observeAll(TermFrequencies<T> sometf) {
        // We can't just use .putAll(), that'd ruin the present bindings. Unfortunately, there's no .computeAll()
        for (Entry<T,Integer> oldentry:sometf.tf.entrySet()) {
            tf.compute(oldentry.getKey(), (k,v) -> (v==null) ?
                    oldentry.getValue() : oldentry.getValue() + v);
        }
    }

    /**
     * Query this term frequency record for a specific term's frequency. If it hasn't been observed, return 0.
     * Does not return <code>null</code>.
     *
     * @return: The term's frequency; 0 if it has not been observed.
     */
    public Integer freq(String term) {
        final Integer frequency = tf.get(term);
        return (frequency == null) ? 0 : frequency;
    }
}
