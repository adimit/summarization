package dimitrov.sum.uima;

import dimitrov.sum.UimaDeployer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.uima.UIMA_IllegalArgumentException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by aleks on 13/12/14.
 *
 * See: {@link org.apache.uima.examples.cpe.FileSystemCollectionReader}
 */
public class DocumentReader extends CollectionReader_ImplBase {

    private static final String PARAM_INPUTDIR = UimaDeployer.PROP_INPUT_DIRECTORY;

    private Iterator<File> fileIterator; // documents to process.
    private int totalFiles; // documents to process total count.
    private int progress; // documents already processed.

    private static final Logger log = LoggerFactory.getLogger(DocumentReader.class);

    @Override
    public void initialize() throws ResourceInitializationException {
        log.info("Initializing Document Reader.");
        final String inputDirParam = (String) getConfigParameterValue(PARAM_INPUTDIR);
        if (inputDirParam == null) {
            log.error("Couldn't find input directory parameter setting!");
            throw new ResourceInitializationException(ResourceInitializationException.CONFIG_SETTING_ABSENT,
                    new Object[] {PARAM_INPUTDIR});
        }
        final File srcDirectory =
                new File (inputDirParam.trim());

        if (!(srcDirectory.exists() && srcDirectory.isDirectory())) {
            throw new ResourceInitializationException(ResourceConfigurationException.DIRECTORY_NOT_FOUND,
                    new Object[] { PARAM_INPUTDIR, this.getMetaData().getName(), srcDirectory.getPath() });
        }

        final IOFileFilter always = FileFilterUtils.trueFileFilter();
        final Collection<File> files = FileUtils.listFiles(srcDirectory, always, always);

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
     * @param aCAS the CAS to populate with the next element of the collection
     * @throws org.apache.uima.UIMA_IllegalStateException     if there are no more elements left in the collection
     * @throws java.io.IOException                            if an I/O failure occurs
     * @throws org.apache.uima.collection.CollectionException if there is some other problem with reading from the Collection
     */
    @Override
    public synchronized void getNext(CAS aCAS) throws IOException, CollectionException {
        final File f = this.getNextFile();
        log.info("Next document: {}", f.getName());
        if (aCAS == null) {
            log.error("Got a null cas.");
            throw new UIMA_IllegalArgumentException(UIMA_IllegalArgumentException.ILLEGAL_ARGUMENT,
                    new Object[] {"null", "aCAS", "getNext"});
        }
        final JCas jcas;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException e) {
            throw new CollectionException(e);
        }
        // FIXME: We just use the default encoding, which shouldn't be the case.
        final String fContents = FileUtils.readFileToString(f);
        jcas.setDocumentText(fContents);

        final SourceDocumentInformation srcInfo = new SourceDocumentInformation(jcas);
        srcInfo.setUri(f.getAbsoluteFile().toURI().toString());
        srcInfo.setOffsetInSource(0);
        srcInfo.setLastSegment(progress == totalFiles);

        // good graces, I have *no* idea who was stupid enough to make
        // documentSize an int, but we have to live with it.
        final long fSize = f.length();
        if (fSize > Integer.MAX_VALUE) {
            log.warn("File size of '{}' is larger than Integer.MAX_VALUE bytes. CAS document size unreliable!",
                    f.getAbsoluteFile().toString());
            srcInfo.setDocumentSize(Integer.MAX_VALUE);
        } else {
            srcInfo.setDocumentSize((int) f.length());
        }

        srcInfo.addToIndexes();
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
