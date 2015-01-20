package dimitrov.sum;

import dimitrov.sum.uima.reader.DocumentReader;
import dimitrov.sum.uima.LocalSourceInfo;
import org.apache.commons.io.FileUtils;
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
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resourceSpecifier.factory.*;
import org.apache.uima.resourceSpecifier.factory.impl.ServiceContextImpl;
import org.apache.uima.util.ProcessTraceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by aleks on 21/12/14.
 */
public class UimaDeployer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UimaDeployer.class);

    /**
     * Start time of the processing - used to compute elapsed time.
     */
    private static long mStartTime = System.currentTimeMillis();

    private final File aeDescriptor;

    // For logging CAS activity
    private ConcurrentHashMap<String, Long> casMap = new ConcurrentHashMap<>();

    private String springContainerId = null;
    private UimaAsynchronousEngine uimaAsynchronousEngine;

    private void writeDeploymentDescriptor(final DeployerSettings settings, final File tempFile,
                                           final String name, final String description)
            throws ResourceInitializationException {
        final ServiceContext context = new ServiceContextImpl(name, description,
                settings.aggregateAE, settings.endpointName, settings.brokerUrl);
        context.setCasPoolSize(settings.uimaCasPoolSize);
        context.setScaleup(settings.uimaCasPoolSize);

        final UimaASAggregateDeploymentDescriptor dd =
                DeploymentDescriptorFactory.createAggregateDeploymentDescriptor(context);
        dd.setAsync(true);

        try {
            final String ddContent = dd.toXML();
            log.debug("Deployment descriptor:\n{}", ddContent);
            FileUtils.writeStringToFile(tempFile, ddContent, Charset.defaultCharset(), false);
            log.info("Wrote deployment descriptor to {}.", tempFile.getAbsoluteFile());
        } catch (IOException e) {
            log.error("Failed to write Phase 1 deployment descriptor! Deliting temporary file {}.",
                    tempFile.getAbsoluteFile(), e);
            final boolean deleted = tempFile.delete();
            if (!deleted)
                log.warn("Failed to delete {}.", tempFile.getAbsoluteFile());
            throw new ResourceInitializationException(e);
        }

    }

    public UimaDeployer(final DeployerSettings settings) throws Exception {
        aeDescriptor = new File(settings.phase + ".xml");
        writeDeploymentDescriptor(settings, aeDescriptor, settings.phase, settings.phase + " deployment.");

        uimaAsynchronousEngine = new BaseUIMAAsynchronousEngine_impl();

        final Map<String,Object> appCtx = new HashMap<>();

        appCtx.put(UimaAsynchronousEngine.DD2SpringXsltFilePath, System.getenv("UIMA_HOME")
                + "/bin/dd2spring.xsl");
        appCtx.put(UimaAsynchronousEngine.SaxonClasspath, "file:" + System.getenv("UIMA_HOME")
                + "/saxon/saxon8.jar");

        log.info("Initializing Collection Reader");
        final CollectionReaderDescription cd = CollectionReaderFactory.createReaderDescription(
                DocumentReader.class,
                DocumentReader.PARAM_INPUTDIR, settings.inputDir,
                DocumentReader.PARAM_READ_PLAIN_TEXT, settings.readPlainText);

        final CollectionReader collectionReader = UIMAFramework.produceCollectionReader(cd);
        uimaAsynchronousEngine.setCollectionReader(collectionReader);
        uimaAsynchronousEngine.addStatusCallbackListener(new StatusCallbackListenerImpl(settings.outputDir));

        appCtx.put(UimaAsynchronousEngine.Timeout, settings.uimaAsTimeout);
        appCtx.put(UimaAsynchronousEngine.CpcTimeout, settings.uimaAsCpcTimeout);
        appCtx.put(UimaAsynchronousEngine.GetMetaTimeout, settings.uimaAsMetaTimeout);
        appCtx.put(UimaAsynchronousEngine.CasPoolSize, settings.uimaCasPoolSize);

        log.info("Deploying {} AE.", settings.phase);
        final long deployStart = System.currentTimeMillis();
        springContainerId = uimaAsynchronousEngine.deploy(aeDescriptor.getAbsolutePath(), appCtx);
        final long deployEnd = System.currentTimeMillis();
        log.info("Deployment took {}.", renderMillis(deployEnd - deployStart));

        appCtx.put(UimaAsynchronousEngine.SERIALIZATION_STRATEGY, settings.serializationStrategy);

        appCtx.put(UimaAsynchronousEngine.ServerUri, settings.brokerUrl);
        appCtx.put(UimaAsynchronousEngine.ENDPOINT, settings.endpointName);

        appCtx.put(UIMAFramework.CAS_INITIAL_HEAP_SIZE, Integer.valueOf(settings.fsHeapSize).toString());

        log.info("Initializing UIMA As.");
        uimaAsynchronousEngine.initialize(appCtx);
    }

    public void run() {
        try {
            log.info("Processing…");
            uimaAsynchronousEngine.process();
            log.info("Undeploying…");
            uimaAsynchronousEngine.undeploy(springContainerId);
            log.info("Stopping…");
            uimaAsynchronousEngine.stop();
            if (!aeDescriptor.delete())
                log.warn("Couldn't delete phase 1 descriptor at {}!", aeDescriptor.getAbsoluteFile());
            log.info("Halted.");
        } catch (Exception e) {
            Summarizer.croak(e, "Failed asynchronous processing!");
        }
    }

    /**
     * Render milliseconds in a human-readable format. If the given amount of milliseconds
     * amounts to less than a minute, display seconds, with millisecond fraction. If not,
     * omit millisecond fraction, and display the bigger units.
     *
     * @param ms Milliseconds
     * @return A formatted string for easy consumption by humans. Nom nom.
     */
    public static String renderMillis(final Long ms) {
        if (ms > 1000) {
            final long hrs = TimeUnit.MILLISECONDS.toHours(ms) % 24;
            final long min = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
            final long sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
            if (hrs > 0) {
                return String.format("%dh, %02dm, %02ds", hrs, min, sec);
            } else if (min > 0) {
                return String.format("%dm, %02ds", min, sec);
            } else {
                return String.format("%d.%03ds", sec, ms - sec*1000);
            }
        } else {
            return ms.toString() + "ms";
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
                Summarizer.croak(aStatus.getExceptions(), message);
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
            long elapsedTime = System.currentTimeMillis() - mStartTime;
            log.info("Time elapsed: {}.", renderMillis(elapsedTime));
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
                log.debug("entityProcessComplete(): {}", aStatus.getStatusMessage());
                if (log.isDebugEnabled()) {
                    List<ProcessTraceEvent> eList = aStatus.getProcessTrace().getEventsByComponentName("UimaEE", false);
                    final String ip = eList.stream()
                            .filter(event -> event.getDescription().equals("Service IP"))
                            .map(ProcessTraceEvent::getResultMessage).findAny().orElse("no IP");
                    String casId = ((UimaASProcessStatus) aStatus).getCasReferenceId();
                    if (casId != null) {
                        final long current = System.currentTimeMillis() - mStartTime;
                        final Long start = casMap.get(casId);
                        if (start != null) {
                            log.debug("IP: {}\tStart: {}\tElapsed: {}",
                                    ip, renderMillis(start), renderMillis(current - start));
                        }
                    }

                } else {
                    if ((entityCount + 1) % 50 == 0) {
                        log.info("{} processed.", (entityCount + 1));
                    }
                }
            }

            final LocalSourceInfo sourceInfo = new LocalSourceInfo(aCas);
            final File outFile = new File(outputDir, sourceInfo.generateXmiFileName());
            log.debug("Finished annotation of {}. Outputting to {}", sourceInfo.getUri(), outFile.getName());
            try (FileOutputStream outStream = new FileOutputStream(outFile)) {
                    XmiCasSerializer.serialize(aCas, outStream);
            } catch (Exception e) {
                log.error("Could not save CAS to XMI file");
                e.printStackTrace();
            }

            // update stats
            entityCount++;
            final String docText = aCas.getDocumentText();
            if (docText != null) {
                size += docText.length();
            }
        }

        public void onBeforeMessageSend(UimaASProcessStatus status) {
            long current = System.currentTimeMillis() - mStartTime;
            casMap.put(status.getCasReferenceId(), current);
        }

        /**
         * This method is called when a CAS is picked up by remote UIMA AS
         * from a queue right before processing. This callback identifies
         * on which machine the CAS is being processed and by which UIMA AS
         * service (PID).
         */
        public void onBeforeProcessCAS(UimaASProcessStatus status, String nodeIP, String pid) {
            log.debug("onBeforeProcessCAS() Status: {}, node: {}, pid: {}",
                    status.getStatusMessage(), nodeIP, pid);
        }
    }
}
