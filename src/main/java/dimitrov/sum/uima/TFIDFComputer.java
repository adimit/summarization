package dimitrov.sum.uima;

/**
 * Created by aleks on 21/01/15.
 */
public abstract class TFIDFComputer {
    protected final double totalDocumentCount;

    public TFIDFComputer(long totalDocumentCount) {
        this.totalDocumentCount = (double) totalDocumentCount;
    }

    protected double idf(long df) { return totalDocumentCount / (double) df; }

    public abstract double computeTFIDF(int tf, long df);
}
