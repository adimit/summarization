package dimitrov.sum;

import dimitrov.sum.protocols.classpath.ClassPathHandler;
import dimitrov.sum.protocols.classpath.ConfigurableStreamHandlerFactory;
import dimitrov.sum.uima.LocalSourceInfo;
import org.apache.activemq.broker.BrokerService;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.metadata.FixedFlow;
import org.apache.uima.analysis_engine.metadata.impl.FixedFlow_impl;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.FileResourceSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.impl.ExternalResourceDescription_impl;
import org.apache.uima.resource.impl.FileResourceSpecifier_impl;
import org.apache.uima.resource.metadata.ExternalResourceBinding;
import org.apache.uima.resource.metadata.ResourceManagerConfiguration;
import org.apache.uima.resource.metadata.impl.ExternalResourceBinding_impl;
import org.apache.uima.resource.metadata.impl.ResourceManagerConfiguration_impl;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.*;

/**
 * Created by aleks on 20/01/15.
 */
public class Summarizer {

    public static final String termFrequencySerializationFile = "tf.serialized";

    private static final Logger log = LoggerFactory.getLogger(UimaDeployer.class);

    public static final String SETTINGS_FILE = "Settings.properties";

    // croak v. /krəʊk/. 1. to utter a low hoarse sound. 2. (informal) to die.
    public static void croak(Throwable cause, String message) {
        cause.printStackTrace();
        log.error(message);
        System.exit(1);
    }

    public static void croak(List<? extends Throwable> causes, String message) {
        causes.forEach(Throwable::printStackTrace);
        log.error(message);
        System.exit(1);
    }

    public static void croak(String message) {
        log.error(message);
        System.exit(1);
    }

    private static File writeDescriptor(final AnalysisEngineDescription desc, String prefix)
            throws ResourceInitializationException {

        final File xml;
        try {
            xml = File.createTempFile(prefix, ".xml");
        } catch (IOException e) {
            log.error("Could not create temporary File: {}", prefix, e);
            throw new ResourceInitializationException(e);
        }

        try (final FileOutputStream xmlOut = new FileOutputStream(xml)) {
            desc.toXML(xmlOut);
        } catch (FileNotFoundException e) {
            log.error("Could not find temporary file: {}.", xml.getAbsoluteFile(), e);
            throw new ResourceInitializationException(e);
        } catch (IOException e) {
            log.error("Could not write to temporary file: {}.", xml.getAbsoluteFile(), e);
            throw new ResourceInitializationException(e);
        } catch (SAXException e) {
            log.error("SAX error while writing to file {}.", xml.getAbsoluteFile(), e);
            throw new ResourceInitializationException(e);
        }
        return xml;
    }

    private static void configureOpenNLPResource(String implementationName, String resourceName,
                                                 String fileURL, String externalResourceKey,
                                                 ResourceManagerConfiguration rmConfig) {

        final ExternalResourceDescription sentenceModel = new ExternalResourceDescription_impl();
        sentenceModel.setImplementationName(implementationName);
        sentenceModel.setName(resourceName);
        final FileResourceSpecifier sentModelSpec = new FileResourceSpecifier_impl();
        sentModelSpec.setFileUrl(fileURL);
        sentenceModel.setResourceSpecifier(sentModelSpec);
        rmConfig.addExternalResource(sentenceModel);

        final ExternalResourceBinding sentBinding = new ExternalResourceBinding_impl();
        sentBinding.setKey(externalResourceKey + "/opennlp.uima.ModelName");
        sentBinding.setResourceName(resourceName);
        rmConfig.addExternalResourceBinding(sentBinding);

    }

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        log.info("Initializing.");
        final Arguments arguments = new Arguments(args);

        final URLStreamHandler internalHandler = new ClassPathHandler();
        final URLStreamHandlerFactory internalFactory =
                new ConfigurableStreamHandlerFactory("internal", internalHandler);
        URL.setURLStreamHandlerFactory(internalFactory);

        // An undocumented little "feature" of UIMA-AS: if you undeploy it with
        // the property dontKill missing, it will just call System.exit(0).
        System.setProperty("dontKill", "true");

        final List<String> phase1Components = new LinkedList<>();
        phase1Components.add("internal:opennlp/SentenceDetector.xml");
        phase1Components.add("internal:opennlp/Tokenizer.xml");
        phase1Components.add("internal:sum/TermFrequency.xml");

        final ResourceManagerConfiguration rmConfig = new ResourceManagerConfiguration_impl();
        configureOpenNLPResource(
                "opennlp.uima.sentdetect.SentenceModelResourceImpl",
                "SentenceModel",
                "internal:models/en-sent.bin",
                "Sentence Detector",
                rmConfig
        );
        configureOpenNLPResource(
                "opennlp.uima.tokenize.TokenizerModelResourceImpl",
                "TokenModel",
                "internal:models/en-token.bin",
                "Tokenizer",
                rmConfig
        );

        final AnalysisEngineDescription phase1AEDesc = makeAggregate("Phase1", phase1Components, rmConfig);
        final File phase1Xml = writeDescriptor(phase1AEDesc, "phase1");

        final List<String> phase2Components = new LinkedList<>();
        phase2Components.add("internal:sum/TFIDFAE.xml");

        final AnalysisEngineDescription phase2AEDesc = makeAggregate("Phase2", phase2Components);
        final File phase2Xml = writeDescriptor(phase2AEDesc, "phase2");

        final Properties properties = new Properties();
        try (final InputStream is = ClassLoader.getSystemResourceAsStream(SETTINGS_FILE)) {
            if (is == null)
                croak("Couldn't open stream to " + SETTINGS_FILE);
            properties.load(is);
        } catch (IOException e) {
            croak(e, "CRITICAL: Couldn't load properties at " + SETTINGS_FILE);
        }

        final DeployerSettings phase1Settings = new DeployerSettings(properties, "phase1");

        BrokerService brokerService = null;
        if (phase1Settings.useEmbeddedBroker) {
            log.info("Using embedded broker.");
            brokerService = new BrokerService();
            brokerService.setBrokerName("UIMA");
            brokerService.addConnector(phase1Settings.brokerUrl);
            brokerService.setUseJmx(false);

            brokerService.start();
        } else {
            log.info("Using external broker.");
        }

        if (arguments.startPhase1) {
            final UimaDeployer phase1 = new UimaDeployer(phase1Settings, phase1Xml);
            phase1.run();
        }

        LocalSourceInfo.phaseComplete();
        final DeployerSettings phase2Settings = new DeployerSettings(properties, "phase2");

        final UimaDeployer phase2 = new UimaDeployer(phase2Settings, phase2Xml);
        phase2.run();

        if (brokerService != null) {
            log.info("Stopping embedded broker.");
            brokerService.stop();
        }

        log.info("Completed.");
    }

    private static AnalysisEngineDescription readXMLAEDesc(final String location) throws ResourceInitializationException {
        final URL url;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            log.error("Malformed URL {}!", location);
            throw new ResourceInitializationException(e);
        }
        return readXMLAEDesc(url);
    }

    private static AnalysisEngineDescription readXMLAEDesc(final URL location) throws ResourceInitializationException {
        final XMLInputSource xmlin;
        try {
            xmlin = new XMLInputSource(location);
        } catch (IOException e) {
            log.error("IO error while reading XML Input from {}.", location.toString(), e);
            throw new ResourceInitializationException(e);
        }
        final AnalysisEngineDescription ae;
        try {
            ae = UIMAFramework.getXMLParser().parseAnalysisEngineDescription(xmlin);
        } catch (InvalidXMLException e) {
            log.error("Invalid XML provided at {}.", location.toString(), e);
            throw new ResourceInitializationException(e);
        }
        return ae;
    }

    private static AnalysisEngineDescription makeAggregate(String name, List<String> locations)
            throws ResourceInitializationException {

        final ResourceManagerConfiguration emptyConfiguration = new  ResourceManagerConfiguration_impl();
        return makeAggregate(name, locations, emptyConfiguration);
    }

    private static AnalysisEngineDescription makeAggregate
            (String name, List<String> locations,
             ResourceManagerConfiguration resourceManagerConfiguration)
            throws ResourceInitializationException {

        final AggregateBuilder builder = new AggregateBuilder();
        for (String location: locations) {
            final AnalysisEngineDescription aeDesc = readXMLAEDesc(location);
            builder.add(aeDesc);
        }

        final AnalysisEngineDescription aggregate = builder.createAggregateDescription();
        aggregate.getAnalysisEngineMetaData().setName(name);

        // Since the delegateAnalysisEngines are saved in a LinkedHashMap, they keySet *should*
        // return their keys in the order of addition, since it's a LinkedHashSet. Should. If the
        // fixedFlow ends up having the flow in the wrong order, the culprit is likely here, especially
        // if the underlying UIMA implementation has stopped using a LinkedHashMap.
        final Set<String> keys = aggregate.getDelegateAnalysisEngineSpecifiersWithImports().keySet();
        final String[] keyStrings = new String[keys.size()];
        keys.toArray(keyStrings);
        final FixedFlow fixed = new FixedFlow_impl();
        fixed.setFixedFlow(keyStrings);
        aggregate.getAnalysisEngineMetaData().setFlowConstraints(fixed);

        aggregate.setResourceManagerConfiguration(resourceManagerConfiguration);

        return aggregate;
    }

    private static class Arguments {
        final boolean startPhase1;
        Arguments(String[] args) {
            if (args.length > 0)
                this.startPhase1 = !Objects.equals(args[0], "--skip-phase-1");
            else
                startPhase1 = true;
        }
    }
}
