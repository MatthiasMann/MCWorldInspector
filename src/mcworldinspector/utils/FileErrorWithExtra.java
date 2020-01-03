package mcworldinspector.utils;

import java.io.File;

/**
 *
 * @author matthias
 */
public class FileErrorWithExtra extends FileError {

    protected final Object extra;

    public FileErrorWithExtra(File file, Exception error, Object extra) {
        super(file, error);
        this.extra = extra;
    }

    public Object getExtra() {
        return extra;
    }
}
