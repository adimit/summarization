package dimitrov.sum.protocols.classpath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Created by aleks on 24/01/15.
 */
public class ClassPathHandler extends URLStreamHandler {

    protected final Logger log = LoggerFactory.getLogger(URLStreamHandler.class);

    private final ClassLoader loader;

    public ClassPathHandler() {
        this.loader = ClassLoader.getSystemClassLoader();
    }

    public ClassPathHandler(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Opens a connection to the object referenced by the
     * {@code URL} argument.
     * This method should be overridden by a subclass.
     * <p/>
     * <p>If for the handler's protocol (such as HTTP or JAR), there
     * exists a public, specialized URLConnection subclass belonging
     * to one of the following packages or one of their subpackages:
     * java.lang, java.io, java.util, java.net, the connection
     * returned will be of that subclass. For example, for HTTP an
     * HttpURLConnection will be returned, and for JAR a
     * JarURLConnection will be returned.
     *
     * @param u the URL that this connects to.
     * @return a {@code URLConnection} object for the {@code URL}.
     * @throws java.io.IOException if an I/O error occurs while opening the
     *                             connection.
     */
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        log.debug("Opening URL Connection to: {}.", u.toString());
        final URL resourceUrl = loader.getResource(u.getPath());
        if (resourceUrl == null)
            throw new IOException("Read error at location " + u.toString());
        else
            return resourceUrl.openConnection();
    }
}
