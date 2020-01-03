package mcworldinspector.utils;

import java.io.File;
import java.util.Objects;

/**
 *
 * @author matthias
 */
public class FileError {

    protected final File file;
    protected final Exception error;

    public FileError(File file, Exception error) {
        this.file = Objects.requireNonNull(file, "file");
        this.error = Objects.requireNonNull(error, "exception");
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

    public static FileError from(File file, Exception e) {
        if(e instanceof IOExceptionWithOffset) {
            IOExceptionWithOffset wo = (IOExceptionWithOffset)e;
            return new FileOffsetError(file, wo.getOffset(), wo.getCause());
        } else
            return new FileError(file, e);
    }
}
