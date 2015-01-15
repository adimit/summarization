package dimitrov.sum;

import org.apache.activemq.broker.BrokerService;
import org.apache.uima.UIMAFramework;
import org.apache.uima.aae.client.UimaASProcessStatus;
import org.apache.uima.aae.client.UimaAsBaseCallbackListener;
import org.apache.uima.aae.client.UimaAsynchronousEngine;
import org.apache.uima.adapter.jms.client.BaseUIMAAsynchronousEngine_impl;
import org.apache.uima.cas.*;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.util.ProcessTraceEvent;
import org.apache.uima.util.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by aleks on 21/12/14.
 */
public class UimaDeployer {

    private static final Logger log = LoggerFactory.getLogger(UimaDeployer.class);

    public static final String SETTINGS_FILE = "Settings.properties";

    // Name constants for settings.
    public static final String PROP_SERIALIZATION_STRAT = "serializationStrategy";
    public static final String PROP_CAS_POOL_SIZE = "casPoolSize";
    public static final String PROP_AS_TIMEOUT = "asyncTimeout";
    public static final String PROP_AS_CPC_TIMEOUT = "asyncCpcTimeout";
    public static final String PROP_AS_META_TIMEOUT = "asyncGetMetaTimeout";
    public static final String PROP_BROKER_URL = "brokerURL";
    public static final String PROP_ENDPOINT_NAME = "endpointName";
    public static final String PROP_DEPLOYMENT_DESCRIPTOR = "deploymentDescriptor";
    public static final String PROP_DOCUMENT_READER_DESCRIPTOR = "documentReaderDescriptor";
    public static final String PROP_USE_EMBEDDED_BROKER = "useEmbeddedBroker";
    public static final String PROP_OUTPUT_DIRECTORY = "outputDirectory";
    public static final String PROP_INPUT_DIRECTORY = "inputDirectory";

    // Defaults for settings
    private static final int DEFAULT_CAS_POOL_SIZE = 1;
	private static final int DEFAULT_AS_TIMEOUT = 10;
	private static final int DEFAULT_AS_CPC_TIMEOUT = 10;
	private static final int DEFAULT_AS_META_TIMEOUT = 10;
    private static final int FS_HEAP_SIZE = 2000000;

    /**
     * Start time of the processing - used to compute elapsed time.
     */
    private static long mStartTime = System.nanoTime() / 1000000;


    // For logging CAS activity
    private ConcurrentHashMap<String, Long> casMap = new ConcurrentHashMap<>();

    private String springContainerId = null;
    private UimaAsynchronousEngine uimaAsynchronousEngine;

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

    private UimaDeployer(final DeployerSettings settings) throws Exception {
        uimaAsynchronousEngine =
                new BaseUIMAAsynchronousEngine_impl();

        final Map<String,Object> appCtx = new HashMap<>();

        appCtx.put(UimaAsynchronousEngine.DD2SpringXsltFilePath, System.getenv("UIMA_HOME")
                + "/bin/dd2spring.xsl");
        appCtx.put(UimaAsynchronousEngine.SaxonClasspath, "file:" + System.getenv("UIMA_HOME")
                + "/saxon/saxon8.jar");

        log.info("Initializing Collection Reader");
        final CollectionReaderDescription collectionReaderDescription = UIMAFramework.getXMLParser()
                .parseCollectionReaderDescription(new XMLInputSource(settings.documentReaderDescriptor));
        final CollectionReader collectionReader = UIMAFramework
                .produceCollectionReader(collectionReaderDescription);
        uimaAsynchronousEngine.setCollectionReader(collectionReader);
        uimaAsynchronousEngine.addStatusCallbackListener(new StatusCallbackListenerImpl(settings.outputDir));

        appCtx.put(UimaAsynchronousEngine.Timeout, settings.uimaAsTimeout);
        appCtx.put(UimaAsynchronousEngine.CpcTimeout, settings.uimaAsCpcTimeout);
        appCtx.put(UimaAsynchronousEngine.GetMetaTimeout, settings.uimaAsMetaTimeout);
        appCtx.put(UimaAsynchronousEngine.CasPoolSize, settings.uimaCasPoolSize);

        log.info("Deploying AE.");
        final long deployStart = System.currentTimeMillis();
        springContainerId = uimaAsynchronousEngine.deploy(settings.deploymentDescriptor, appCtx);
        final long deployEnd = System.currentTimeMillis();
        log.info("Deployment took {} ms.", deployEnd - deployStart);

        appCtx.put(UimaAsynchronousEngine.SERIALIZATION_STRATEGY, settings.serializationStrategy);

        appCtx.put(UimaAsynchronousEngine.ServerUri, settings.brokerUrl);
        appCtx.put(UimaAsynchronousEngine.ENDPOINT, settings.endpointName);

        appCtx.put(UIMAFramework.CAS_INITIAL_HEAP_SIZE, Integer.valueOf(FS_HEAP_SIZE / 4).toString());

        log.info("Initializing UIMA As.");
        uimaAsynchronousEngine.initialize(appCtx);
    }

    private void run() {
        try {
            log.info("Processing…");
            uimaAsynchronousEngine.process();
            log.info("Undeploying…");
            uimaAsynchronousEngine.undeploy(springContainerId);
            log.info("Stopping…");
            uimaAsynchronousEngine.stop();
        } catch (Exception e) {
            croak(e, "Failed asynchronous processing!");
        }
    }

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        log.info("Initializing.");

        final Properties properties = new Properties();
        try {
            final ClassLoader loader = ClassLoader.getSystemClassLoader();
            final InputStream is = loader.getResourceAsStream(SETTINGS_FILE);
            if (is == null) {
                croak("Couldn't open stream to " + SETTINGS_FILE);
            }
            properties.load(is);
        } catch (IOException e) {
            croak(e, "CRITICAL: Couldn't load properties at " + SETTINGS_FILE);
        }

        final DeployerSettings settings = new DeployerSettings(properties);

        BrokerService brokerService = null;
        if (settings.useEmbeddedBroker) {
            log.info("Using embedded broker.");
            brokerService = new BrokerService();
            brokerService.setBrokerName("UIMA");
            brokerService.addConnector("tcp://minsk:61616");
            brokerService.addConnector("vm://UIMA");
            brokerService.setUseJmx(false);

            brokerService.start();
        } else {
            log.info("Using external broker.");
        }

        final UimaDeployer runner = new UimaDeployer(settings);
        runner.run();

        if (brokerService != null) {
            log.info("Stopping embedded broker.");
            brokerService.stop();
        }

        log.info("Completed.");
    }

    // croak v. /krəʊk/. 1. to utter a low hoarse sound. 2. (informal) to die.
    private static void croak(Throwable cause, String message) {
        cause.printStackTrace();
        log.error(message);
        System.exit(1);
    }

    private static void croak(List<? extends Throwable> causes, String message) {
        causes.forEach(Throwable::printStackTrace);
        log.error(message);
        System.exit(1);
    }

    private static void croak(String message) {
        log.error(message);
        System.exit(1);
    }

    private static class DeployerSettings {
        final String deploymentDescriptor;
        final File outputDir;
        final String serializationStrategy;
        final boolean useEmbeddedBroker;
        final Integer uimaAsTimeout;
        final Integer uimaAsCpcTimeout;
        final Integer uimaAsMetaTimeout;
        final Integer uimaCasPoolSize;
        final String brokerUrl;
        final String endpointName;
        final String documentReaderDescriptor;

        DeployerSettings(final Properties settings) {
            // Optional settings
            this.uimaAsTimeout = 1000 * getNumericProperty(settings, PROP_AS_TIMEOUT, DEFAULT_AS_TIMEOUT);
            this.uimaAsCpcTimeout = 1000 * getNumericProperty(settings, PROP_AS_CPC_TIMEOUT, DEFAULT_AS_CPC_TIMEOUT);
            this.uimaAsMetaTimeout = 1000 * getNumericProperty(settings, PROP_AS_META_TIMEOUT, DEFAULT_AS_META_TIMEOUT);
            this.uimaCasPoolSize = getNumericProperty(settings, PROP_CAS_POOL_SIZE, DEFAULT_CAS_POOL_SIZE);

            // Mandatory settings
            this.brokerUrl = set(settings, PROP_BROKER_URL);
            this.endpointName = set(settings, PROP_ENDPOINT_NAME);
            this.deploymentDescriptor = set(settings, PROP_DEPLOYMENT_DESCRIPTOR);
            this.serializationStrategy = settings.getProperty(PROP_SERIALIZATION_STRAT, "xmi");
            this.documentReaderDescriptor = set(settings, PROP_DOCUMENT_READER_DESCRIPTOR);
            this.useEmbeddedBroker =
                    set(settings, PROP_USE_EMBEDDED_BROKER).toLowerCase().trim().equals("true");
            final String outputDirName = set(settings, PROP_OUTPUT_DIRECTORY);
            this.outputDir = new File(outputDirName);
            if (!this.outputDir.isDirectory() || !this.outputDir.canWrite()) {
                croak("Output path " + outputDirName + " is not a directory or is not writable.");
            }
        }

        private String set(final Properties settings, String key) {
            final String result = settings.getProperty(key);
            if (result == null)
                croak("Missing mandatory setting: " + key);
            return result;
        }
    }

    /**
     * Callback Listener. Receives event notifications from CPE.
     */
    private class StatusCallbackListenerImpl extends UimaAsBaseCallbackListener {
        int entityCount = 0;
        long size = 0;

        private final File outputDir;

        public StatusCallbackListenerImpl(final File outputDir) {
            super();
            this.outputDir = outputDir;
        }

        /**
         * Called when the initialization is completed.
         *
         * @see org.apache.uima.collection.StatusCallbackListener#initializationComplete()
         */
        @Override
        public void initializationComplete(EntityProcessStatus aStatus) {
            maybeStopAndCroak(aStatus, "Error on getMeta call to remote service.");
            log.info("UIMA AS Service Initialization Complete");
        }

        /* On bad status, stop the engine, log an error, print stack traces, and die ungracefully. */
        private void maybeStopAndCroak(final EntityProcessStatus aStatus, final String message) {
            if (aStatus != null && aStatus.isException()) {
                stop();
                croak(aStatus.getExceptions(), message);
            }
        }

        private void stop() {
            try {
                uimaAsynchronousEngine.stop();
            } catch( Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Called when the collection processing is completed.
         *
         * @see org.apache.uima.collection.StatusCallbackListener#collectionProcessComplete()
         */
        @Override
        public void collectionProcessComplete(EntityProcessStatus aStatus) {
            maybeStopAndCroak(aStatus, "Error on collection process complete call to remote service:");

            log.info("Completed {} document(s.)", entityCount);
            if (size > 0) {
                log.info("Document(s) had {} characters.", size);
            }
            long elapsedTime = System.nanoTime() / 1000000 - mStartTime;
            log.info("Time elapsed: {}ms ", elapsedTime);

            String perfReport = uimaAsynchronousEngine.getPerformanceReport();
            if (perfReport != null) {
                log.info("\n\n ------------------ PERFORMANCE REPORT ------------------");
                log.info(uimaAsynchronousEngine.getPerformanceReport());
            } else {
                log.warn("No performance report generated.");
            }
            if (springContainerId == null) { stop(); }
        }

        /**
         * Called when the processing of a Document is completed. <br>
         * The process status can be looked at and corresponding actions taken.
         *
         * @param aCas
         *          CAS corresponding to the completed processing
         * @param aStatus
         *          EntityProcessStatus that holds the status of all the events for aEntity
         */
        public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus) {
            maybeStopAndCroak(aStatus, "Error on process CAS call to remote service:");
            if (aStatus != null) {
                if (log.isDebugEnabled()) {
                    String ip = "no IP";
                    List<ProcessTraceEvent> eList = aStatus.getProcessTrace().getEventsByComponentName("UimaEE", false);
                    for (ProcessTraceEvent event : eList) {
                        if (event.getDescription().equals("Service IP")) {
                            ip = event.getResultMessage();
                        }
                    }
                    String casId = ((UimaASProcessStatus) aStatus).getCasReferenceId();
                    if (casId != null) {
                        long current = System.nanoTime() / 1000000 - mStartTime;
                        if (casMap.containsKey(casId)) {
                            Object value = casMap.get(casId);
                            if (value != null) {
                                long start = (Long) value;
                                log.debug("IP: {}\tStart: {}\tElapsed: {}", ip, start, (current - start));
                            }
                        }
                    }

                } else {
                    if (0 == (entityCount + 1) % 50) {
                        log.info("{} processed.", (entityCount + 1));
                    }
                }
            }

            // if output dir specified, dump CAS to XMI
            // try to retrieve the filename of the input file from the CAS
            File outFile = null;
            Type srcDocInfoType = aCas.getTypeSystem().getType(
                    "org.apache.uima.examples.SourceDocumentInformation");
            if (srcDocInfoType != null) {
                FSIterator<FeatureStructure> it = aCas.getIndexRepository().getAllIndexedFS(srcDocInfoType);
                if (it.hasNext()) {
                    FeatureStructure srcDocInfoFs = it.get();
                    Feature uriFeat = srcDocInfoType.getFeatureByBaseName("uri");
                    Feature offsetInSourceFeat = srcDocInfoType.getFeatureByBaseName("offsetInSource");
                    String uri = srcDocInfoFs.getStringValue(uriFeat);
                    int offsetInSource = srcDocInfoFs.getIntValue(offsetInSourceFeat);
                    File inFile;
                    try {
                        inFile = new File(new URL(uri).getPath());
                        String outFileName = inFile.getName();
                        if (offsetInSource > 0) {
                            outFileName += ("_" + offsetInSource);
                        }
                        outFileName += ".xmi";
                        outFile = new File(outputDir, outFileName);
                    } catch (MalformedURLException e1) {
                        // invalid URI, use default processing below
                    }
                }
            }
            if (outFile == null) {
                outFile = new File(outputDir, "doc" + entityCount);
            }
            try {
                try (FileOutputStream outStream = new FileOutputStream(outFile)) {
                    XmiCasSerializer.serialize(aCas, outStream);
                }
            } catch (Exception e) {
                log.error("Could not save CAS to XMI file");
                e.printStackTrace();
            }

            // update stats
            entityCount++;
            String docText = aCas.getDocumentText();
            if (docText != null) {
                size += docText.length();
            }
        }

        public void onBeforeMessageSend(UimaASProcessStatus status) {
            long current = System.nanoTime() / 1000000 - mStartTime;
            casMap.put(status.getCasReferenceId(), current);
        }

        /**
         * This method is called when a CAS is picked up by remote UIMA AS
         * from a queue right before processing. This callback identifies
         * on which machine the CAS is being processed and by which UIMA AS
         * service (PID).
         */
        public void onBeforeProcessCAS(UimaASProcessStatus status, String nodeIP, String pid) {
            log.debug("About to process cas. Status: {}, node: {}, pid: {}",
                    status.getStatusMessage(), nodeIP, pid);
        }
    }
}
