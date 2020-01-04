package mcworldinspector.utils;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author matthias
 */
public class IOExceptionWithFile extends IOException {

    private final File file;

    public IOExceptionWithFile(File file, Exception cause) {
        super(cause);
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    @Override
    public Exception getCause() {
        return (Exception)super.getCause();
    }
}
