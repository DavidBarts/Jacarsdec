/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.util.concurrent.Semaphore;

/**
 * This is a buffered communications "channel" somewhat reminiscent of the 
 * channels in CAR Hoare's communicating sequential processes. Unlike those,
 * this one never blocks on write. If the buffer is full, the oldest item
 * therein is simply overwritten.
 * 
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class Channel<T> {
	private Object[] buffer;
	private int start, length;
	private Semaphore sem;
	
	/**
	 * Construct a new channel.
	 * @param capacity	Buffer size.
	 */
	public Channel(int capacity) {
		buffer = new Object[capacity];
		start = length = 0;
		sem = new Semaphore(0);
	}
	
	/**
	 * Write a single item to this channel.
	 * @param item		Item to write.
	 * @return			Whether or not overwriting happened.
	 */
	public synchronized boolean write(T item) {
		int ndx = (start + length) % buffer.length;
		buffer[ndx] = item;
		if (length >= buffer.length) {
			/* data lost due to overwrite */
			start = (start + 1) % buffer.length;
			return true;
		} else {
			length++;
			sem.release();
			return false;
		}
	}
	
	/**
	 * Read a single item from this channel, blocking if needed.
	 * @return			The item read.
	 */
	public T read() throws InterruptedException {
		sem.acquire();
		synchronized (this) {
			if (length < 1)
				throw new RuntimeException("invalid length: " + length);
			T ret = (T) buffer[start];
			buffer[start] = null;  /* unref the item */
			start = (start + 1) % buffer.length;
			length--;
			return ret;
		}
	}
}
