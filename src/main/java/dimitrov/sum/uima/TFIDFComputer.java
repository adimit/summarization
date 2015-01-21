package dimitrov.sum.uima;

/**
 * Created by aleks on 21/01/15.
 */
public abstract class TFIDFComputer {
    protected final double totalDocumentCount;

    public TFIDFComputer(long totalDocumentCount) {
        this.totalDocumentCount = (double) totalDocumentCount;
    }

    public abstract double computeTFIDF(int tf, long df);
}
