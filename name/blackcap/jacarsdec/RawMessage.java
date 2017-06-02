/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.util.Date;

/**
 * This represents a single raw, undemodulated ACARS message as read from
 * the ADC.
 * 
 * @author  David Barts <david.w.barts@gmail.com>
 *
 */
public class RawMessage {
	private Date time;
	private float[] message;
	private int channel;
	
	/**
	 * Construct a new raw message.
	 * @param time			Time the message was received.
	 * @param channel		Number of the channel this message came in on.
	 * @param message		The message itself.
	 */
	public RawMessage(Date time, int channel, float[] message) {
		this.time = time;
		this.channel = channel;
		this.message = message;
	}
	
	public float[] getMessage() {
		return message;
	}

	public Date getTime() {
		return time;
	}

	public int getChannel() {
		return channel;
	}

}
