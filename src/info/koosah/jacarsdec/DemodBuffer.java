/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package info.koosah.jacarsdec;

import java.util.Arrays;
import java.nio.BufferOverflowException;

/**
 * This is a simplified version of ByteBuffer that can do one thing
 * the former cannot do: un-put things. The fixed capacity of 256 is
 * a little more than we need; ACARS packets are a maximum of 240
 * data bytes.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class DemodBuffer {
    private static final int CAPACITY = 256;

    private int length;
    private byte[] buf;

    public DemodBuffer() {
        buf = new byte[CAPACITY];
        length = 0;
    }

    /**
     * Put a single byte into the buffer.
     *
     * @param b         Byte to put.
     */
    public void put(byte b) {
        if (length >= CAPACITY) {
            throw new BufferOverflowException();
        }
        buf[length++] = b;
    }

    /**
     * Undo the specified number of most recent put calls.
     *
     * @param count     Number of bytes to unPut.
     * @throws IllegalArgumentException On invalid count
     */
    public void unPut(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("unPut count of " + count + "is negative");
        }
        if (count > length) {
            throw new IllegalArgumentException("unPut count of " + count + "exceeds length");
        }
        length -= count;
    }

    /**
     * Clear this buffer.
     */
    public void clear() {
        length = 0;
    }

    /**
     * Obtain a new array containing the bytes in the buffer. The array
     * will be exactly long enough to represent the buffer.
     *
     * @return          Array of bytes.
     */
    public byte[] toArray() {
        return Arrays.copyOf(buf, length);
    }

    /**
     * Get the number of bytes in the buffer.
     *
     * @return          Number of bytes.
     */
    public int length() {
        return length;
    }

    /**
     * Obtain the array backing the current buffer. The array may be longer
     * than the number of bytes currently in the buffer.
     *
     * @return          Array of bytes.
     */
    public byte[] array() {
        return buf;
    }
}
