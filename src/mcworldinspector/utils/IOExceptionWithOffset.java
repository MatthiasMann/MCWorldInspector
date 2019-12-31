package mcworldinspector.utils;

import java.io.IOException;

/**
 *
 * @author matthias
 */
public class IOExceptionWithOffset extends IOException {

    private final long offset;

    public IOExceptionWithOffset(long offset, Exception cause) {
        super(cause);
        this.offset = offset;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public Exception getCause() {
        return (Exception)super.getCause();
    }
}
