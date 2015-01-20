package dimitrov.sum.uima;

import dimitrov.sum.UimaDeployer;
import dimitrov.sum.uima.ae.CasPopulater;
import dimitrov.sum.uima.ae.PlainTextCASPopulater;
import dimitrov.sum.uima.ae.XmiCASPopulater;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.uima.UIMA_IllegalArgumentException;
import org.apache.uima.UimaContext;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by aleks on 13/12/14.
 *
 * See: {@link org.apache.uima.examples.cpe.FileSystemCollectionReader}
 */
public class DocumentReader extends JCasCollectionReader_ImplBase {

    /**
     * Directory from which to read files.
     */
    public static final String PARAM_INPUTDIR = "inputDirectory";
    @ConfigurationParameter
    private File inputDirectory;

    /**
     * Whether to read XMI files, instead of plain text. Reading XMI files iff value of this setting is "true".
     */
    public static final String PARAM_READ_XMIS = "isXmiReader";
    @ConfigurationParameter
    private boolean isXmiReader;

    private Iterator<File> fileIterator; // documents to process.
    private int totalFiles; // documents to process total count.
    private int progress; // documents already processed.

    private CasPopulater casPopulater;

    private static final Logger log = LoggerFactory.getLogger(DocumentReader.class);

    @Override
    public void initialize(final UimaContext context) throws ResourceInitializationException {
        log.info("Initializing Document Reader.");
        if (inputDirectory == null) {
            log.error("Couldn't find input directory parameter setting!");
            throw new ResourceInitializationException(ResourceInitializationException.CONFIG_SETTING_ABSENT,
                    new Object[] {PARAM_INPUTDIR});
        }

        if (isXmiReader) {
            // We don't like type errors, so it's always non-lenient;
            casPopulater = new XmiCASPopulater(false);
        } else {
            // FIXME: We just use the default encoding, which shouldn't be the case.
            casPopulater = new PlainTextCASPopulater(Charset.defaultCharset());
        }

        if (!inputDirectory.exists() || !inputDirectory.isDirectory()) {
            throw new ResourceInitializationException(ResourceConfigurationException.DIRECTORY_NOT_FOUND,
                    new Object[] { PARAM_INPUTDIR, this.getMetaData().getName(), inputDirectory.getPath() });
        }

        final IOFileFilter always = FileFilterUtils.trueFileFilter();
        final Collection<File> files = FileUtils.listFiles(inputDirectory, always, always);

        // Unfortunately, iterators are not suited to keeping
        // track of progress, so we need to do it manually.
        fileIterator = files.iterator();
        totalFiles = files.size();
        progress = 0;

        log.debug("Found {} files to process.", totalFiles);
    }

    @Override
    public boolean isConsuming() { return true; }

    /**
     * Gets the next element of the collection. The element will be stored in the provided CAS object.
     * If this is a consuming <code>CollectionReader</code> (see {@link #isConsuming()}), this
     * element will also be removed from the collection.
     *
     * @param cas the CAS to populate with the next element of the collection
     * @throws org.apache.uima.UIMA_IllegalStateException     if there are no more elements left in the collection
     * @throws java.io.IOException                            if an I/O failure occurs
     * @throws org.apache.uima.collection.CollectionException if there is some other problem with reading from the Collection
     */
    @Override
    public synchronized void getNext(JCas cas) throws IOException, CollectionException {
        final File f = this.getNextFile();
        log.info("Next document: {}", f.getName());
        if (cas == null) {
            log.error("Got a null cas.");
            throw new UIMA_IllegalArgumentException(UIMA_IllegalArgumentException.ILLEGAL_ARGUMENT,
                    new Object[] {"null", "aCAS", "getNext"});
        }

        casPopulater.populateCAS(cas, f);
    }

    /**
     * Get the next file in the iterator, preserving progress count.
     * @return the next file
     * @throws IOException
     * @throws CollectionException
     */
    private File getNextFile() throws IOException, CollectionException {
        final File f = fileIterator.next();
        progress++;
        return f;
    }

    /**
     * Gets whether there are any elements remaining to be read from this
     * <code>CollectionReader</code>.
     *
     * @return true if and only if there are more elements available from this
     * <code>CollectionReader</code>.
     * @throws java.io.IOException                            if an I/O failure occurs
     */
    @Override
    public synchronized boolean hasNext() throws IOException {
        log.debug("DocumentReader.hasNext() is called. We have {} documents left.", totalFiles - progress);
        if (fileIterator != null) {
            return fileIterator.hasNext();
        } else {
            log.error("File stream not initialized when calling hasNext(). Likely a bug in UIMA!");
            throw new IOException("File stream may not ever be null. Something went wrong with initialization.");
        }
    }

    /**
     * Gets information about the number of entities and/or amount of data that has been read from
     * this <code>CollectionReader</code>, and the total amount that remains (if that information
     * is available).
     * <p/>
     * This method returns an array of <code>Progress</code> objects so that results can be reported
     * using different units. For example, the CollectionReader could report progress in terms of the
     * number of documents that have been read and also in terms of the number of bytes that have been
     * read. In many cases, it will be sufficient to return just one <code>Progress</code> object.
     *
     * @return an array of <code>Progress</code> objects. Each object may have different units (for
     * example number of entities or bytes).
     */
    @Override
    public synchronized Progress[] getProgress() {
        log.debug("DocumentReader.getProgress() is called.");
        return new Progress[] { new ProgressImpl(progress, totalFiles, Progress.ENTITIES) };
    }

    /**
     * Closes this <code>CollectionReader</code>, after which it may no longer be used.
     *
     * @throws java.io.IOException if an I/O failure occurs
     */
    @Override
    public synchronized void close() throws IOException {
        log.info("Closing document reader.");
        // allow garbage collection
        fileIterator = null;
    }
}
