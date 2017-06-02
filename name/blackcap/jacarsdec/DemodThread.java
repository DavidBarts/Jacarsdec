/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

/**
 * Given some digitized audio from a single audio channel of input,
 * demodulate it into an ACARS message.
 * 
 * @author davidb
 *
 */
public class DemodThread extends Thread {
	private Channel<RawMessage> in;
	private Channel<DemodMessage> out;
	
	public DemodThread(Channel<RawMessage> in, Channel<DemodMessage> out) {
		this.in = in;
		this.out = out;
	}
	
	public void run() {
		System.out.println("Demod " + Thread.currentThread().getId() + " started."); // debug
		/* currently just dump channel, time, min, and max */
		RawMessage r = null;
		while (true) {
			try {
				r = in.read();
			} catch (InterruptedException e) {
				break;
			}
			if (r == null)
				break;
			float min = (float) 0.0;
			float max = (float) 0.0;
			float[] data = r.getMessage();
			for (float e : data) {
				if (e < min)
					min = e;
				if (e > max)
					max = e;
			}
			System.out.println("Time: " + r.getTime());
			System.out.format("channel=%d, min=%f, max=%f%n", r.getChannel(), min, max);
		}
	}
}
