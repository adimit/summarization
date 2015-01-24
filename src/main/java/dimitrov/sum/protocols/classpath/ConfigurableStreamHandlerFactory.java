package dimitrov.sum.protocols.classpath;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by aleks on 24/01/15.
 */

public class ConfigurableStreamHandlerFactory implements URLStreamHandlerFactory {
    private final Map<String, URLStreamHandler> protocolHandlers;

    public ConfigurableStreamHandlerFactory(String protocol, URLStreamHandler urlHandler) {
        protocolHandlers = new HashMap<>();
        addHandler(protocol, urlHandler);
    }

    public void addHandler(String protocol, URLStreamHandler urlHandler) {
        protocolHandlers.put(protocol, urlHandler);
    }

    /**
     * Creates a new {@code URLStreamHandler} instance with the specified
     * protocol.
     *
     * @param protocol the protocol ("{@code ftp}",
     *                 "{@code http}", "{@code nntp}", etc.).
     * @return a {@code URLStreamHandler} for the specific protocol.
     * @see java.net.URLStreamHandler
     */
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return protocolHandlers.get(protocol);
    }
}

