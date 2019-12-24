package mcworldinspector.utils;

import java.io.File;

/**
 *
 * @author matthias
 */
public class FileError {

    protected final File file;
    protected final Exception error;

    public FileError(File file, Exception error) {
        this.file = file;
        this.error = error;
    }

    public File getFile() {
        return file;
    }

    public Exception getError() {
        return error;
    }

    @Override
    public String toString() {
        return "In " + file + ": " + error;
    }

}
