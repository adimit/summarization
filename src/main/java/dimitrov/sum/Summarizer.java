package dimitrov.sum;

import dimitrov.sum.uima.LocalSourceInfo;
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

        // An undocumented little "feature" of UIMA-AS: if you undeploy it with
        // the property dontKill missing, it will just call System.exit(0).
        System.setProperty("dontKill", "true");

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

        final UimaDeployer phase1 = new UimaDeployer(phase1Settings);

        // Fork off phase 1 processing.
        final Thread phase1Worker = new Thread(phase1);
        phase1Worker.start();

        // Meanwhile, initialize phase 2
        final DeployerSettings phase2Settings = new DeployerSettings(properties, "phase2");
        final UimaDeployer phase2 = new UimaDeployer(phase2Settings);
        LocalSourceInfo.phaseComplete();

        // Wait for phase 1
        phase1Worker.join();
        phase2.run();

        if (brokerService != null) {
            log.info("Stopping embedded broker.");
            brokerService.stop();
        }

        log.info("Completed.");
    }

}
