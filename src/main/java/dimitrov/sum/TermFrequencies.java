package dimitrov.sum;

import java.util.*;
import java.util.Map.Entry;

/**
 * Created by aleks on 05/12/14.
 */
public class TermFrequencies<T,E> {
    private final Map<T,List<E>> tf;

    // TODO: write tests for this class.
    public TermFrequencies() { tf = new HashMap<>(); }

    /**
     * Observe a singular occurrence of a term <code>t</code>.
     *
     * @param t The observed entity.
     * @param e A particular instance of observation.
     */
    public void observe(final T t, final E e) {
        tf.compute(t, (k,v) -> v == null ? lambdAdd(new LinkedList<>(), e) : lambdAdd(v, e));
    }

    /* Convenience functions for adding to a List inside a Lambda; in the Collections API, Lambdas have
     * to return the value type, not just mutate it. This is probably inefficient. */
    private List<E> lambdAdd(final List<E> c, final E e) { c.add(e); return c; }
    private List<E> lambdAddAll(final List<E> c1, final List<E> c2) { c1.addAll(c2); return c1; }

    public void observeAll(TermFrequencies<T,E> sometf) {
        // We can't just use .putAll(), that'd ruin the present bindings. Unfortunately, there's no .computeAll()
        sometf.entrySet().stream().map(newEntry -> tf.merge(newEntry.getKey(), newEntry.getValue(), this::lambdAddAll));
    }

    public Collection<Entry<T,List<E>>> entrySet() { return tf.entrySet(); }
}
