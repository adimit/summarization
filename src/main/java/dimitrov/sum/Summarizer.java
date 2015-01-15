package dimitrov.sum;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import dimitrov.sum.uima.DocumentReader;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.uima.UIMAFramework;
import org.apache.uima.aae.client.UimaAsBaseCallbackListener;
import org.apache.uima.aae.client.UimaAsynchronousEngine;
import org.apache.uima.adapter.jms.client.BaseUIMAAsynchronousEngine_impl;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Summarizer {
	private static final Logger log = LoggerFactory.getLogger(Summarizer.class);

	private static final String DEFAULT_BROKER_CONFIG_URI = "xbean:activemq.xml";
	private static final String PROP_AMQ_CONFIG_URI = "brokerConfig";

	private static final String SETTINGS_FILE = "Settings.properties";
	private static final String DEFAULT_DR_RESOURCE = "/sum/DocumentReader.xml";
	private static final int DEFAULT_CAS_POOL_SIZE = 1;

	private static final String PROP_CAS_POOL_SIZE = "casPoolSize";
	private static final String PROP_DOC_READER = "collectionReader";

	private static final int DEFAULT_AS_TIMEOUT = 10;
	private static final String PROP_AS_TIMEOUT = "asyncTimeout";
	private static final int DEFAULT_AS_CPC_TIMEOUT = 10;
	private static final String PROP_AS_CPC_TIMEOUT = "asyncCpcTimeout";
	private static final int DEFAULT_AS_META_TIMEOUT = 10;
	private static final String PROP_AS_META_TIMEOUT = "asyncGetMetaTimeout";
	private static final String VM_MQ_URI = "tcp://minsk:61616";
	private static final String MQ_AS_ENDPOINT = "OpenNLP";

	private static final String DEFAULT_DOC_READER_INPUT = "test";
	private static final String PROP_INPUTDIR = DocumentReader.PARAM_INPUTDIR;

	private static CollectionReader initializeCollectionReader(final ClassLoader loader, final Properties settings)
			throws IOException, InvalidXMLException, ResourceInitializationException {
		final String location = settings.getProperty(PROP_DOC_READER, DEFAULT_DR_RESOURCE);
		log.info("Initializing collection reader from '{}'.", location);
		final URL url = loader.getResource(location);
		if (url == null) {
			throw new ResourceInitializationException(ResourceInitializationException.UNKNOWN_RESOURCE_NAME,
					new Object[] {location});
		}
		final XMLInputSource in =
				new XMLInputSource(url);
		final CollectionReaderDescription desc =
				UIMAFramework.getXMLParser().parseCollectionReaderDescription(in);
		final Map<String, Object> crParams = new HashMap<>();
		String inputDir = settings.getProperty(PROP_INPUTDIR);
		if (inputDir == null) {
			log.warn("Couldn't find input directory setting. Using default input dir '{}'", DEFAULT_DOC_READER_INPUT);
			inputDir = DEFAULT_DOC_READER_INPUT;
		}
		//FIXME: this configuration doesn't seem to work right now.
		crParams.put(DocumentReader.PARAM_INPUTDIR, inputDir);
		return UIMAFramework.produceCollectionReader(desc, crParams);
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

	// croak v. /krəʊk/. 1. to utter a low hoarse sound. 2. (informal) To die.
	private static void croak(Throwable cause, String message) {
		cause.printStackTrace();
		log.error(message);
		System.exit(1);
	}

	private static void croak(String message) {
		log.error(message);
		System.exit(1);
	}

	/* We initialize the broker manually. Right now, this is not really necessary at all. We could instead just
	 * give UIMA AS the url
	 *
	 * vm://localhost?brokerConfig=xbean:activemq.xml
	 *
	 * This would have the same effect as the current setup. The reason we do it this way is to make it easier
	 * to switch to remote servers eventually.
	 */
	// this needs to be throwing Exception because ActiveMQ's API
	// does not understand what a checked exception is good for.
	private static BrokerService initializeBroker(final ClassLoader loader, final Properties settings)
			throws Exception {
		final String brokerConfigURI = settings.getProperty(PROP_AMQ_CONFIG_URI, DEFAULT_BROKER_CONFIG_URI);
		log.info("Initializing embedded ActiveMQ broker from '{}'.", brokerConfigURI);
		final BrokerService broker = BrokerFactory.createBroker(new URI(brokerConfigURI));
		broker.start();
		log.info("Started broker.");
		return broker;
	}

	private static UimaAsynchronousEngine initializeUIMA_AS(final ClassLoader loader, final Properties settings)
			throws InvalidXMLException, IOException, ResourceInitializationException {
		log.info("Initializing UIMA AS");
		final UimaAsynchronousEngine uimaAsEngine =
				new BaseUIMAAsynchronousEngine_impl();

		final CollectionReader docReader = initializeCollectionReader(loader, settings);

		uimaAsEngine.setCollectionReader(docReader);

		final HashMap<String,Object> appCtx = new HashMap<>();
		// Timeout numbers need to be converted from seconds to milliseconds
		appCtx.put(UimaAsynchronousEngine.Timeout,
				1000 * getNumericProperty(settings, PROP_AS_TIMEOUT, DEFAULT_AS_TIMEOUT));
		appCtx.put(UimaAsynchronousEngine.CpcTimeout,
				1000 * getNumericProperty(settings, PROP_AS_CPC_TIMEOUT, DEFAULT_AS_CPC_TIMEOUT));
		appCtx.put(UimaAsynchronousEngine.GetMetaTimeout,
				1000 * getNumericProperty(settings, PROP_AS_META_TIMEOUT, DEFAULT_AS_META_TIMEOUT));

		// Connect to broker & endpoint
		appCtx.put(UimaAsynchronousEngine.ServerUri, VM_MQ_URI);
		appCtx.put(UimaAsynchronousEngine.ENDPOINT, MQ_AS_ENDPOINT);

		appCtx.put(UimaAsynchronousEngine.DD2SpringXsltFilePath, "/home/aleks/src/summarization/target/classes/dd2spring.xsl");
		final String saxonCP = settings.getProperty("saxonClassPath");
		if (saxonCP == null) {
			croak("You need to define 'saxonClassPath' in the Settings.properties.\nI know, UIMA sucks, doesn't it?");
		}
		appCtx.put(UimaAsynchronousEngine.SaxonClasspath, saxonCP);

		// then deploy ASes.
		try {
			log.info("Deploying OpenNLP analyzer.");
			uimaAsEngine.deploy("/home/aleks/src/summarization/target/classes/sum/deploy.xml", appCtx);
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}

		uimaAsEngine.addStatusCallbackListener(new UimaAsBaseCallbackListener() {
			/**
			 * Called when the processing of each entity has completed.
			 *
			 * @param aCas    the CAS containing the processed entity and the analysis results
			 * @param aStatus the status of the processing. This object contains a record of any Exception that
			 */
			@Override
			public void entityProcessComplete(CAS aCas, EntityProcessStatus aStatus) {
				// TODO: look at StatusCallbackListenerImpl.java in UIMA sources to implement this.
				log.info("Finished annotation of document: {}", aStatus.getStatusMessage());
			}
		});

		// and initialize
		uimaAsEngine.initialize(appCtx);

		log.info("Finished initializing UIMA AS");

		return uimaAsEngine;
	}

	public static void main(String[] args) {
		// initialize

		// Install the JUL-to-SLF4J bridge. This will handle the UIMA logs.
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		log.info("Starting Summarizer.");

		final ClassLoader loader = ClassLoader.getSystemClassLoader();
		final Properties settings = new Properties();
		try {
			InputStream is = loader.getResourceAsStream(SETTINGS_FILE);
			if (is == null) {
				croak("Couldn't open stream to " + SETTINGS_FILE);
			}
			settings.load(is);
		} catch (IOException e) {
			croak(e, "CRITICAL: Couldn't load properties at " + SETTINGS_FILE);
		}

		// initialize ActiveMQ broker
		try {
			//final BrokerService brokerService = initializeBroker(loader, settings);
		} catch (Exception e) {
			croak(e, "Failed to initialize ActiveMQ broker. " + e.getCause());
		}

		try {
			final UimaAsynchronousEngine uimaAsynchronousEngine = initializeUIMA_AS(loader, settings);
		} catch (InvalidXMLException | IOException | ResourceInitializationException e) {
			croak(e, "Failed to initialize UIMA AS " + e.getCause());
		}

		log.info("Initializing database.");
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			log.error("Could not initialize DB: SQLite JDBC not found.");
			e.printStackTrace();
			System.exit(1);
		}
		Connection connection = null;
		try {
			connection = DriverManager.getConnection("jdbc:sqlite:");

			// get file set

			// make documents
			final List<Document> docs = new ArrayList<>();
			try {
				for (String arg:args) {
					log.info("Adding {}", arg);
					docs.add(new Document(new File(arg)));
				}
			} catch (DocumentInitializationException die) {
				throw new RuntimeException(die);
			}

			// analyse documents
			for (Document doc:docs) {
				try {
					log.info("Analyzing {}", doc.getName());
					doc.analyze();
					log.info("Summarizing {}", doc.getName());
					// summarise documents
					doc.summarize();
					log.info("Finished analysis of {}", doc.getName());
				} catch (AnalysisEngineProcessException aee) {
					log.error("Analysis engine failure: {}", aee.getMessage());
					throw new RuntimeException(aee);
				}
			}

			// output
		} catch (SQLException e) {
			log.error("Error accessing database: {}", e.getMessage());
			e.printStackTrace();
			System.exit(1);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					log.error("Failed to close connection! {}", e.getMessage());
					e.printStackTrace();
				}
			}
		}
	}
}
