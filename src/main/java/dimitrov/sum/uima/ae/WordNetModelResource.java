package dimitrov.sum.uima.ae;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.dictionary.Dictionary;
import org.apache.uima.fit.component.ExternalResourceAware;
import org.apache.uima.fit.component.initialize.ConfigurationParameterInitializer;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Created by aleks on 25/01/15.
 */
public class WordNetModelResource implements SharedResourceObject {
    protected final Logger log = LoggerFactory.getLogger(WordNetModelResource.class);
    private Dictionary dictionary;

    public Dictionary getDictionary() { return dictionary; }

    /**
     * Called by the {@link org.apache.uima.resource.ResourceManager} after this object has been
     * instantiated. The implementation of this method should read the data from the specified
     * <code>DataResource</code> and use that data to initialize this object.
     *
     * @param data a <code>DataResource</code> that provides access to the data for this resource
     *              object.
     * @throws org.apache.uima.resource.ResourceInitializationException if a failure occurs during loading.
     */
    @Override
    public void load(DataResource data) throws ResourceInitializationException {
        try {
            log.debug("Loading WN dictionary from {}.", data.getUrl().toString());
            this.dictionary = Dictionary.getInstance(data.getInputStream());
        } catch (JWNLException e) {
            log.error("JWNL error while trying to read {}.", data.getUrl(), e);
            throw new ResourceInitializationException(e);
        } catch (IOException e) {
            log.error("IO error while trying to read {}.", data.getUrl(), e);
            throw new ResourceInitializationException(e);
        }
    }
}
