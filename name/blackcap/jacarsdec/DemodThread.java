/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.nio.ByteBuffer;
import java.util.ArrayList;

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
	private double rate;
	
	private RawMessage rawMessage;
	
	private static final int FLEN = 11;
	private static final double PLLKa = 1.8991680918e+02;
	private static final double PLLKb = 9.8503292076e-01;
	private static final double PLLKc = 0.9995;
	private static final double DCCF = 0.02;
	private static final int MAXPERR = 2;
	
	private double mskFreq, mskPhi, mskClk, mskDf, mskKa, mskA, mskDc;
	private int mskS, idx;
	private double[] h, I, Q;
	
	private byte outbits;
	private int nbits;
	private int blkErr;
	DemodBuffer demodBuf;
	byte[] crc;
	
	private enum AcarsState { WSYN, SYN2, SOH1, TXT, CRC1, CRC2, END };
	AcarsState state;
	
	public DemodThread(Channel<RawMessage> in, Channel<DemodMessage> out, float rate) {
		this.in = in;
		this.out = out;
		this.rate = (double) rate;
	}
	
	private static final byte SYN = 0x16;
	private static final byte SOH = 0x01;
	private static final byte STX = 0x02;
	private static final byte ETX = (byte) 0x83;
	private static final byte ETB = (byte) 0x97;
	private static final byte DLE = 0x7f;
		
	private static final byte[] NUMBITS = {
			0,1,1,2,1,2,2,3,1,2,2,3,2,3,3,4,1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,
			1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
	        1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
	        2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
	        1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,
	        2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
	        2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,
	        3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,4,5,5,6,5,6,6,7,5,6,6,7,6,7,7,8 };

	public void run() {
		initMsk();
		initAcars();
		rawMessage = null;
		while (true) {
			/* read, exit if interrupted or we get a null message */
			try {
				rawMessage = in.read();
			} catch (InterruptedException e) {
				break;
			}
			if (rawMessage == null)
				break;
			/* demodulate */
			demodMsk();
		}
	}
	
	private void demodMsk() {
		double dphi;
		double p, s, in;
		int n;
		float[] buf = rawMessage.getMessage();
		
		for (n=0; n<buf.length; n++) {
			/* oscillator */
			p = mskFreq + mskDf;
			mskClk += p;
			p += mskPhi;
			if (p >= 2.0 * Math.PI)
				p -= 2.0 * Math.PI;
			mskPhi = p;
			
			if (mskClk > 3.0 * Math.PI/2.0) {
				int j;
				double iv, qv, bit;
				mskClk = 3.0 * Math.PI / 2.0;
				
				/* matched filter */
				for (j=0, iv=qv=0.0; j<FLEN; j++) {
					int k = (idx + j) % FLEN;
					iv += h[j] * I[k];
					qv += h[j] * Q[k];
				}
				
				if ((mskS & 1) == 0) {
					dphi = iv >= 0 ? fst_atan2(-qv, iv) : fst_atan2(qv, -iv);
					bit = (mskS & 2) != 0 ? iv : -iv;
				} else {
					dphi = qv >= 0 ? fst_atan2(iv, qv) : fst_atan2(-iv, -qv);
					bit = (mskS & 2) != 0 ? -qv : qv;
				}
				putbit(bit);
				mskS = (mskS + 1) & 3;
				
				/* PLL */
				dphi *= mskKa;
				mskDf = PLLKc * mskDf + dphi - PLLKb * mskA;
				mskA = dphi;
			}
			
			/* DC blocking */
			in = (double) buf[n];
			s = in - mskDc;
			mskDc = (1.0 - DCCF) * mskDc + DCCF * in;
			
			/* FI */
			I[idx] = s * Math.cos(p);
			Q[idx] = s * Math.sin(p);
			idx = (idx + 1) % FLEN;
		}
	}
	
	private void initMsk() {
		mskFreq = 1800.0 / rate * 2.0 * Math.PI;
		mskPhi = mskClk = 0.0;
		mskS = idx = 0;
		mskKa = PLLKa / rate;
		mskDf = mskA = mskDc = 0.0;
		I = new double[FLEN];
		Q = new double[FLEN];
		h = new double[FLEN];
		
		for (int i=0; i < FLEN; i++) {
			h[i] = Math.cos(2.0 * Math.PI * 600.0 / rate * (i-FLEN/2));
			I[i] = Q[i] = 0.0;
		}
	}
	
	private void initAcars() {
		outbits = 0;
		blkErr = 0;
		nbits = 8;
		state = AcarsState.WSYN;
		demodBuf = new DemodBuffer();
		crc = new byte[2];
	}
	
	private double fst_atan2(double y, double x) {
		double r, angle;
		double abs_y = Math.abs(y) + 1.0e-10;
		if (x >= 0.0) {
			r = (x - abs_y) / (x + abs_y);
			angle = Math.PI/4.0 * (1.0 - r);
		} else {
			r = (x + abs_y) / (abs_y - x);
			angle = Math.PI/4.0 * (3.0 - r);
		}
		return y < 0.0 ? -angle : angle;
	}
	
	private void putbit(double v) {
		outbits >>>= 1;
		if (v > 0.0)
			outbits |= 0x80;
		nbits--;
		if (nbits <= 0)
			decodeAcars();
	}
	
	private void decodeAcars() {
		switch (state) {
		case WSYN:
			if (outbits == SYN) {
				state = AcarsState.SYN2;
				nbits = 8;
				return;
			}
			if (outbits == ~SYN) {
				mskS ^= 2;
				state = AcarsState.SYN2;
				nbits = 8;
				return;
			}
			nbits = 1;
			return;
			
		case SYN2:
			if (outbits == SYN) {
				state = AcarsState.SOH1;
				nbits = 8;
				return;
			}
			if (outbits == ~SYN) {
				mskS ^= 2;
				nbits = 8;
				return;
			}
			state = AcarsState.WSYN;
			nbits = 1;
			return;
			
		case SOH1:
			if (outbits == SOH) {
				state = AcarsState.TXT;
				blkErr = 0;
				nbits = 8;
				return;
			}
			state = AcarsState.WSYN;
			nbits = 1;
			return;
			
		case TXT:
			demodBuf.put(outbits);
			if ((NUMBITS[outbits&0xff] & 1) == 0) {
				blkErr++;
				if (blkErr > MAXPERR + 1) {
					state = AcarsState.WSYN;
					nbits = 1;
					return;
				}
			}
			if (outbits == ETX || outbits == ETB) {
				state = AcarsState.CRC1;
				nbits = 8;
				return;
			}
			if (demodBuf.length() > 20 && outbits == DLE) {
				/* missed text end */
				byte[] buf = demodBuf.array();
				int len = demodBuf.length();
				crc[0] = buf[len-2];
				crc[1] = buf[len-1];
				demodBuf.unPut(3);
				state = AcarsState.CRC2;
				putMsg();
				return;
			}
			nbits = 8;
			return;
			
		case CRC1:
			crc[0] = outbits;
			state = AcarsState.CRC2;
			nbits = 8;
			return;
			
		case CRC2:
			crc[1] = outbits;
			putMsg();
			return;
			
		case END:
			state = AcarsState.WSYN;
			mskDf = 0;
			nbits = 8;
			return;
		}
	}
	
	private void putMsg() {
		state = AcarsState.END;
		nbits = 8;
		
		/* get this raw message, allocate buffer for next one, reject runts */
		byte[] buf = demodBuf.toArray();
		demodBuf = new DemodBuffer();
		if (buf.length < 13)
			return;
		
		/* force STX/ETX */
		buf[12] &= ETX | STX;
		buf[12] |= ETX & STX;
		
		/* parity check */
		int pn = 0;
		int[] pr = new int[MAXPERR];
		for (int i=0; i<buf.length; i++) {
			if ((NUMBITS[buf[i]] & 1) == 0) {
				if (pn < MAXPERR)
					pr[pn] = i;
				pn++;
			}
		}
		if (pn > MAXPERR)
			return;
		blkErr = pn;
		AcarsCrc c = new AcarsCrc();
		for (byte b : buf) {
			c.update(b);
		}
		c.update(crc[0]);
		c.update(crc[1]);
		
		/* try to fix error(s) */
		if (!c.fixErrors(buf, pr, 0, pn)) {
			return;
		}
		
		/* redo parity checking and remove parity bits */
		for (int i=0; i<buf.length; i++) {
			if ((NUMBITS[buf[i]] & 1) == 0) {
				System.err.format("%s: parity check failure on channel %d%n",
						Main.MYNAME, rawMessage.getChannel());
				return;
			}
			buf[i] &= 0x7f;
		}
		
		/* send message to output thread */
		int blkLvl = (int) (20.0 * Math.log10(mskDc) - 48.0);
		DemodMessage demodMessage = new DemodMessage(
				rawMessage.getTime(),
				rawMessage.getChannel(),
				blkLvl, blkErr, buf);
		if (out.write(demodMessage))
			System.err.format("%s: demod data lost on channel %d%n",
					Main.MYNAME, rawMessage.getChannel());
	}
}