package dimitrov.sum;

import dimitrov.sum.protocols.classpath.ClassPathHandler;
import dimitrov.sum.protocols.classpath.ConfigurableStreamHandlerFactory;
import dimitrov.sum.uima.LocalSourceInfo;
import dimitrov.sum.uima.Names;
import dimitrov.sum.uima.ae.TFIDFAE;
import dimitrov.sum.uima.ae.WordNet;
import dimitrov.sum.uima.ae.WordNetModelResource;
import opennlp.uima.util.UimaUtil;
import org.apache.activemq.broker.BrokerService;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.metadata.FixedFlow;
import org.apache.uima.analysis_engine.metadata.impl.FixedFlow_impl;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.FileResourceSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.impl.ExternalResourceDescription_impl;
import org.apache.uima.resource.impl.FileResourceSpecifier_impl;
import org.apache.uima.resource.metadata.ExternalResourceBinding;
import org.apache.uima.resource.metadata.ResourceManagerConfiguration;
import org.apache.uima.resource.metadata.TypeSystemDescription;
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

    private static final Logger log = LoggerFactory.getLogger(Summarizer.class);

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

        final ExternalResourceDescription model = new ExternalResourceDescription_impl();
        model.setImplementationName(implementationName);
        model.setName(resourceName);
        final FileResourceSpecifier modelSpec = new FileResourceSpecifier_impl();
        modelSpec.setFileUrl(fileURL);
        model.setResourceSpecifier(modelSpec);
        rmConfig.addExternalResource(model);

        final ExternalResourceBinding modelBinding = new ExternalResourceBinding_impl();
        modelBinding.setKey(externalResourceKey + "/opennlp.uima.ModelName");
        modelBinding.setResourceName(resourceName);
        rmConfig.addExternalResourceBinding(modelBinding);
    }

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        log.info("Initializing.");
        final long startTime = System.currentTimeMillis();
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
        phase1Components.add("internal:opennlp/PosTagger.xml");
        phase1Components.add("internal:opennlp/Chunker.xml");
        phase1Components.add("internal:opennlp/Parser.xml");
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
        configureOpenNLPResource(
                "opennlp.uima.postag.POSModelResourceImpl",
                "PosModel",
                "internal:models/en-pos-maxent.bin",
                "POS Tagger",
                rmConfig
        );
        configureOpenNLPResource(
                "opennlp.uima.chunker.ChunkerModelResourceImpl",
                "ChunkerModel",
                "internal:models/en-chunker.bin",
                "Chunker",
                rmConfig
        );
        configureOpenNLPResource(
                "opennlp.uima.parser.ParserModelResourceImpl",
                "ParserModel",
                "internal:models/en-parser-chunking.bin",
                "Parser",
                rmConfig
        );

        final Properties properties = new Properties();
        try (final InputStream is = ClassLoader.getSystemResourceAsStream(SETTINGS_FILE)) {
            if (is == null)
                croak("Couldn't open stream to " + SETTINGS_FILE);
            properties.load(is);
        } catch (IOException e) {
            croak(e, "CRITICAL: Couldn't load properties at " + SETTINGS_FILE);
        }

        final AnalysisEngineDescription phase1AEDesc = makeAggregate("Phase1", phase1Components, rmConfig);
        final File phase1Xml = writeDescriptor(phase1AEDesc, "phase1");

        final URL dictionaryURL = new URL("internal:extjwnl_resource_properties.xml");
        final ExternalResourceDescription wnModel = ExternalResourceFactory.createExternalResourceDescription
                (WordNetModelResource.class, dictionaryURL);

        final DeployerSettings phase1Settings = new DeployerSettings(properties, "phase1");

        final List<AnalysisEngineDescription> phase2AEs = new LinkedList<>();
        phase2AEs.add(AnalysisEngineFactory.createEngineDescription(TFIDFAE.class, phase1Settings.getTypeSystemDesc(),
                UimaUtil.TOKEN_TYPE_PARAMETER, "dimitrov.sum.uima.types.Token",
                Names.TERM_TYPE_PARAMETER, "dimitrov.sum.uima.types.Term",
                Names.TFIDF_FEATURE_PARAMETER, "tfidf",
                Names.TERM_SURFACE_FEATURE_PARAMETER, "surface",
                Names.TERM_FREQUENCY_FEATURE_PARAMETER, "casFrequency",
                Names.TERM_OBSERVATIONS_FEATURE_PARAMETER, "observations"));
        phase2AEs.add(AnalysisEngineFactory.createEngineDescription(WordNet.class, phase1Settings.getTypeSystemDesc(),
                WordNet.WORD_NET_RESOURCE_KEY, wnModel));

        final AnalysisEngineDescription phase2AEDesc = makeAggregatePrime("Phase2", phase2AEs);
        final File phase2Xml = writeDescriptor(phase2AEDesc, "phase2");


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

        final long finalTime = System.currentTimeMillis();
        log.info("Overall processing time was {}", UimaDeployer.renderMillis(finalTime - startTime));
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
            log.error("IO error while reading XML input from {}.", location.toString(), e);
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

    private static AnalysisEngineDescription makeAggregatePrime
            (String name, final List<AnalysisEngineDescription> aes,
             final ResourceManagerConfiguration resourceManagerConfiguration)
            throws ResourceInitializationException {

        final AggregateBuilder builder = new AggregateBuilder();
        aes.forEach(ae -> builder.add(ae));

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

        if (resourceManagerConfiguration != null)
            aggregate.setResourceManagerConfiguration(resourceManagerConfiguration);

        return aggregate;
    }

    private static AnalysisEngineDescription makeAggregatePrime
            (String name, final List<AnalysisEngineDescription> aes)
            throws ResourceInitializationException {

        return makeAggregatePrime(name, aes, null);
    }

    private static AnalysisEngineDescription makeAggregate(String name, final List<String> locations)
            throws ResourceInitializationException {

        final ResourceManagerConfiguration emptyConfiguration = new  ResourceManagerConfiguration_impl();
        return makeAggregate(name, locations, emptyConfiguration);
    }

    private static AnalysisEngineDescription makeAggregate
            (String name, final List<String> locations,
             final ResourceManagerConfiguration resourceManagerConfiguration)
            throws ResourceInitializationException {

        final List<AnalysisEngineDescription> aes = new LinkedList<>();
        for (String location: locations) { aes.add(readXMLAEDesc(location)); }

        return makeAggregatePrime(name, aes, resourceManagerConfiguration);
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
