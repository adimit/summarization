package dimitrov.sum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

/**
 * Created by aleks on 20/01/15.
 */
public class DeployerSettings {

    public static final String SETTINGS_FILE = "Settings.properties";

    // Name constants for settings.
    public static final String PROP_SERIALIZATION_STRAT = "serializationStrategy";
    public static final String PROP_CAS_POOL_SIZE = "casPoolSize";
    public static final String PROP_AS_TIMEOUT = "asyncTimeout";
    public static final String PROP_AS_CPC_TIMEOUT = "asyncCpcTimeout";
    public static final String PROP_AS_META_TIMEOUT = "asyncGetMetaTimeout";
    public static final String PROP_BROKER_URL = "brokerURL";
    public static final String PROP_ENDPOINT_NAME = "endpointName";
    public static final String PROP_USE_EMBEDDED_BROKER = "useEmbeddedBroker";
    public static final String PROP_OUTPUT_DIRECTORY = "outputDirectory";
    public static final String PROP_INPUT_DIRECTORY = "inputDirectory";
    public static final String PROP_PHASE_1_AGGREGATE = "phase1AggregateDescriptor";
    public static final String PROP_FS_HEAP_SIZE = "fsHeapSize";

    // Defaults for settings
    private static final int DEFAULT_CAS_POOL_SIZE = 1;
    private static final int DEFAULT_AS_TIMEOUT = 10;
    private static final int DEFAULT_AS_CPC_TIMEOUT = 10;
    private static final int DEFAULT_AS_META_TIMEOUT = 10;
    private static final int DEFAULT_FS_HEAP_SIZE = 500000;

    protected static final Logger log = LoggerFactory.getLogger(DeployerSettings.class);

    public final File outputDir;
    public final String inputDir;
    public final String serializationStrategy;
    public final boolean useEmbeddedBroker;
    public final Integer uimaAsTimeout;
    public final Integer uimaAsCpcTimeout;
    public final Integer uimaAsMetaTimeout;
    public final Integer uimaCasPoolSize;
    public final String brokerUrl;
    public final String endpointName;
    public final String phase1Aggregate;
    public final int fsHeapSize;

    DeployerSettings(final Properties settings) {
        // Optional settings
        this.uimaAsTimeout = 1000 * getNumericProperty(settings, PROP_AS_TIMEOUT, DEFAULT_AS_TIMEOUT);
        this.uimaAsCpcTimeout = 1000 * getNumericProperty(settings, PROP_AS_CPC_TIMEOUT, DEFAULT_AS_CPC_TIMEOUT);
        this.uimaAsMetaTimeout = 1000 * getNumericProperty(settings, PROP_AS_META_TIMEOUT, DEFAULT_AS_META_TIMEOUT);
        this.uimaCasPoolSize = getNumericProperty(settings, PROP_CAS_POOL_SIZE, DEFAULT_CAS_POOL_SIZE);
        this.fsHeapSize = getNumericProperty(settings, PROP_FS_HEAP_SIZE, DEFAULT_FS_HEAP_SIZE);

        // Mandatory settings
        this.phase1Aggregate = set(settings, PROP_PHASE_1_AGGREGATE);
        this.brokerUrl = set(settings, PROP_BROKER_URL);
        this.endpointName = set(settings, PROP_ENDPOINT_NAME);
        this.serializationStrategy = settings.getProperty(PROP_SERIALIZATION_STRAT, "xmi");
        this.inputDir = this.set(settings, PROP_INPUT_DIRECTORY);
        this.useEmbeddedBroker =
                set(settings, PROP_USE_EMBEDDED_BROKER).toLowerCase().trim().equals("true");
        final String outputDirName = set(settings, PROP_OUTPUT_DIRECTORY);
        this.outputDir = new File(outputDirName);
        if (!this.outputDir.isDirectory() || !this.outputDir.canWrite()) {
            Summarizer.croak("Output path " + outputDirName + " is not a directory or is not writable.");
        }
    }

    // A wrapper to extract a numeric property from a Properties object, with a default
    // failover.
    private static int getNumericProperty(final Properties p, String pName, int def) {
        final String value = p.getProperty(pName);
        try {
            return(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            log.warn("Couldn't parse {} in setting {}, substituting default {}.", value, pName, def);
            return(def);
        }
    }

    private String set(final Properties settings, String key) {
        final String result = settings.getProperty(key);
        if (result == null)
            Summarizer.croak("Missing mandatory setting: " + key);
        return result;
    }
}
