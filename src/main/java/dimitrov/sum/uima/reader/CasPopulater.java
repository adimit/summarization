package dimitrov.sum.uima.reader;

import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Created by aleks on 20/01/15.
 */
public abstract class CasPopulater {
    private final Path inputDirectory;
    private final Path outputDirectory;

    public CasPopulater(final File inputDirectory, final File outputDirectory) {
        this.inputDirectory = inputDirectory.toPath();
        this.outputDirectory = outputDirectory.toPath();
    }

    protected String makeOutputPath(File f) {
        final Path filePath = f.toPath();
        final Path relativePath = inputDirectory.relativize(filePath);
        final Path output = outputDirectory.resolve(relativePath);
        return output.toString();
    }

    public abstract void populateCAS(JCas cas, File fromfile) throws IOException, CollectionException;
}
