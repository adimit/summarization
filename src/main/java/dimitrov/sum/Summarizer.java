package dimitrov.sum;

import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Created by aleks on 20/01/15.
 */
public class Summarizer {

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
            brokerService.addConnector(settings.brokerUrl);
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

}
