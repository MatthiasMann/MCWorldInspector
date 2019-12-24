package mcworldinspector.utils;

import java.io.File;

/**
 *
 * @author matthias
 */
public class FileOffsetError extends FileError {

    private final long offset;

    public FileOffsetError(File file, long offset, Exception error) {
        super(file, error);
        this.offset = offset;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return "In " + file + ':' + offset + ": " + error;
    }
    
}
