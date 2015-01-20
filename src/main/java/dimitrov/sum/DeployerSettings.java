package dimitrov.sum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

/**
 * Created by aleks on 20/01/15.
 */
public class DeployerSettings {

    // Name constants for settings.
    public static final String PROP_SERIALIZATION_STRAT = "serializationStrategy";
    public static final String PROP_CAS_POOL_SIZE = "casPoolSize";
    public static final String PROP_AS_TIMEOUT = "asyncTimeout";
    public static final String PROP_AS_CPC_TIMEOUT = "asyncCpcTimeout";
    public static final String PROP_AS_META_TIMEOUT = "asyncGetMetaTimeout";
    public static final String PROP_BROKER_URL = "brokerURL";
    public static final String PROP_USE_EMBEDDED_BROKER = "useEmbeddedBroker";
    public static final String PROP_OUTPUT_DIRECTORY = "outputDirectory";
    public static final String PROP_INPUT_DIRECTORY = "inputDirectory";
    public static final String PROP_AGGREGATE = "aggregateDescriptor";
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
    public final String aggregateAE;
    public final int fsHeapSize;
    public final boolean readPlainText;

    public final String phase;

    private final Properties settings;

    DeployerSettings(final Properties settings, final String phase) {
        this.phase = phase;
        this.settings = settings;

        // Optional settings
        this.uimaAsTimeout = 1000 * getNumericProperty(PROP_AS_TIMEOUT, DEFAULT_AS_TIMEOUT);
        this.uimaAsCpcTimeout = 1000 * getNumericProperty(PROP_AS_CPC_TIMEOUT, DEFAULT_AS_CPC_TIMEOUT);
        this.uimaAsMetaTimeout = 1000 * getNumericProperty(PROP_AS_META_TIMEOUT, DEFAULT_AS_META_TIMEOUT);
        this.uimaCasPoolSize = getNumericProperty(PROP_CAS_POOL_SIZE, DEFAULT_CAS_POOL_SIZE);
        this.fsHeapSize = getNumericProperty(PROP_FS_HEAP_SIZE, DEFAULT_FS_HEAP_SIZE);

        this.readPlainText = getProperty("readPlainText", "true").toLowerCase().trim().equals("true");

        // We call the endpoint by the same name as the phase
        this.endpointName = phase;

        // Mandatory settings
        this.aggregateAE = set(PROP_AGGREGATE);
        this.brokerUrl = set(PROP_BROKER_URL);
        this.serializationStrategy = getProperty(PROP_SERIALIZATION_STRAT, "xmi");
        this.inputDir = this.set(PROP_INPUT_DIRECTORY);
        this.useEmbeddedBroker = set(PROP_USE_EMBEDDED_BROKER).toLowerCase().trim().equals("true");
        final String outputDirName = set(PROP_OUTPUT_DIRECTORY);
        this.outputDir = new File(outputDirName);
        if (!this.outputDir.isDirectory() || !this.outputDir.canWrite()) {
            Summarizer.croak("Output path " + outputDirName + " is not a directory or is not writable.");
        }
    }

    private String getProperty(final String pName) {
        final String phaseProperty = this.settings.getProperty(phase + "." + pName);
        if (phaseProperty == null)
            return this.settings.getProperty(pName);
        else
            return phaseProperty;
    }

    private String getProperty(final String pName, final String def) {
        final String result = getProperty(pName);
        if (result == null) { return def; } else { return result; }
    }

    // A wrapper to extract a numeric property from a Properties object, with a default
    // failover.
    private int getNumericProperty(String pName, int def) {
        final String value = getProperty(pName);
        try {
            return(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            log.warn("Couldn't parse {} in setting {}, substituting default {}.", value, pName, def);
            return(def);
        }
    }

    private String set(String key) {
        final String result = getProperty(key);
        if (result == null)
            Summarizer.croak("Missing mandatory setting: " + key);
        return result;
    }
}
