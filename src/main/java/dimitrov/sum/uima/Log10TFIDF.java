package dimitrov.sum.uima;

/**
 * Created by aleks on 21/01/15.
 */
public class Log10TFIDF extends TFIDFComputer{

    public Log10TFIDF(long totalDocumentCount) {
        super(totalDocumentCount);
    }

    @Override
    public double computeTFIDF(int tf, long df) {
        return (double) tf * Math.log10((double) df/this.totalDocumentCount);
    }
}
