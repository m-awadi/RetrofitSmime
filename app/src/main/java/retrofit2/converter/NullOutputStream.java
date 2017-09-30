package retrofit2.converter;

import java.io.IOException;
import java.io.OutputStream;
/**
 * Created by dawud_tan on 9/30/17.
 */

/**
 * An {@link OutputStream} that discards all bytes to be written
 *
 * @author Philip Helger
 */
public class NullOutputStream extends OutputStream {
    /**
     * A singleton.
     */
    public static final NullOutputStream NULL_OUTPUT_STREAM = new NullOutputStream();

    /**
     * Does nothing - output to <code>/dev/null</code>.
     *
     * @param b   The bytes to write
     * @param off The start offset
     * @param len The number of bytes to write
     */
    @Override
    public void write(final byte[] b, final int off, final int len) {
        // do nothing
    }

    /**
     * Does nothing - output to <code>/dev/null</code>.
     *
     * @param b The byte to write
     */
    @Override
    public void write(final int b) {
        // do nothing
    }

    /**
     * Does nothing - output to <code>/dev/null</code>.
     *
     * @param b The bytes to write
     * @throws IOException never
     */
    @Override
    public void write(final byte[] b) throws IOException {
        // do nothing
    }

    /**
     * Does not nothing and therefore does not throw an Exception.
     */
    @Override
    public void flush() {
    }

    /**
     * Does not nothing and therefore does not throw an Exception.
     */
    @Override
    public void close() {
    }
}