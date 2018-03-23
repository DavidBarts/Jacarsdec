/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package info.koosah.jacarsdec;

/**
 * Given some digitized audio from a single audio channel of input,
 * demodulate it into an ACARS message.
 *
 * @author  David Barts <david.w.barts@gmail.com>
 *
 */
public class DemodThread extends Thread {
    private Channel<RawMessage> in;
    private Channel<DemodMessage> out;
    private double rate;

    private RawMessage rawMessage;

    /*
     * All MSK parameters are derivable from a baud rate and a center
     * frequency. Note that these must be chosen so that the resulting
     * mark and space frequencies are such that a complete mark and space
     * can be sent in an exact integer multiple of half-wavelengths; we
     * need to ensure that both start and end are at zero points.
     */
    private static final int BAUD = 2400;
    private static final double CENTER = 1800.0;
    private static final double SHIFT = BAUD / 2.0;
    private static final double DEVIATION = SHIFT / 2.0;

    /* max message length */
    private static final int ACARS_MAX = 240;

    /*
     * I honestly have no idea what's going on with PLLC1 and PLLC2.
     * Tried scaling them by ratio of sampling rates, but that failed
     * horribly. Decreasing them by a factor of 1000 from LeConte's
     * code that samples at 12.5 kHz seems to work.
     */
    private static final double PLLC1 = 4.0e-11;
    private static final double PLLC2 = 3.5e-6;
    private static final int MAXPERR = 2;
    private static final double MSK_RPC = 3.0 * Math.PI / 2.0;

    private int frameLength;
    private double mskFreq, mskPhi, mskClk, mskDf, mskA;
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
        boolean verbose = Main.cmdLine.hasOption("verbose");
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
            if (verbose)
                displayRaw();
            demodMsk();
        }
    }

    private void displayRaw() {
        float[] buf = rawMessage.getMessage();

        /* this is pretty boring if the buffer is empty */
        if (buf.length == 0) {
            System.out.format("%tT.%<tL: N=%d%n", rawMessage.getTime(), 0);
            return;
        }

        /* normal case: report basic stats on buffer */
        float total = 0.0f;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float sample : buf) {
            if (sample < min)
                min = sample;
            if (sample > max)
                max = sample;
            total += sample;
        }
        System.out.format("%tT.%<tL: N=%d, min=%f, max=%f, mean=%f%n",
                rawMessage.getTime(),
                buf.length, min, max, total/buf.length);
    }

    private void demodMsk() {
        float[] buf = rawMessage.getMessage();
        int n;

        for (n=0; n<buf.length; n++) {
            double s, in;

            /* oscillator */
            s = mskFreq + mskDf;
            mskPhi += s;
            if (mskPhi >= 2.0 * Math.PI)
                mskPhi -= 2.0 * Math.PI;

            /* mixer */
            in = buf[n];
            I[idx] = in * Math.cos(-mskPhi);
            Q[idx] = in * Math.sin(-mskPhi);
            idx = (idx + 1) % frameLength;

            /* bit clock */
            mskClk += s;
            if (mskClk >= MSK_RPC) {
                int j;
                double iv, qv, bit, dphi, lvl;

                mskClk -= MSK_RPC;

                /* matched filter */
                for (j=0, iv=qv=0.0; j<frameLength; j++) {
                    int k = (idx + j) % frameLength;
                    iv += h[j] * I[k];
                    qv += h[j] * Q[k];
                }

                /* normalize */
                lvl = Math.hypot(iv,  qv) + 1.0e-6;
                iv /= lvl;
                qv /= lvl;

                /* demod a bit */
                if ((mskS & 1) == 0) {
                    dphi = iv >= 0 ? qv : -qv;
                    /*                       0     2 */
                    bit = (mskS & 2) == 0 ? iv : -iv;
                } else {
                    dphi = qv >= 0 ? -iv : iv;
                    /*                       1     3 */
                    bit = (mskS & 2) == 0 ? qv : -qv;
                }
                putbit(bit);
                mskS = (mskS + 1) & 3;

                /* PLL */
                mskDf = PLLC2 * dphi + mskA;
                mskA = PLLC1 * dphi;
            }
        }
    }

    private void initMsk() {
        mskFreq = CENTER / rate * 2.0 * Math.PI;
        mskPhi = mskClk = 0.0;
        mskS = idx = 0;
        mskDf = mskA = 0.0;

        /* our frame needs to hold 2 bits worth of samples */
        frameLength = 2 * (int) rate;
        frameLength = frameLength / BAUD + frameLength % BAUD > 0 ? 1 : 0;
        I = new double[frameLength];
        Q = new double[frameLength];
        h = new double[frameLength];

        for (int i=0; i < frameLength; i++) {
            h[i] = Math.cos(2.0 * Math.PI * DEVIATION / rate * (i-frameLength/2));
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

    private void putbit(double v) {
        /* XXX: this his how to right-logical-shift a byte in Java */
        outbits = (byte) ((outbits & 0xff) >> 1);
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
                    demodBuf.clear();
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
            if (demodBuf.length() > ACARS_MAX) {
                state = AcarsState.WSYN;
                //mskDf = 0.0;
                nbits = 1;
                demodBuf.clear();
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
            mskDf = 0.0;
            nbits = 8;
            return;
        }
    }

    private void putMsg() {
        state = AcarsState.END;
        nbits = 8;

        /* get this raw message, allocate buffer for next one, reject runts */
        byte[] buf = demodBuf.toArray();
        demodBuf.clear();
        if (buf.length < 13) {
            return;
        }

        /* force STX/ETX */
        buf[12] &= ETX | STX;
        buf[12] |= ETX & STX;

        /* parity check */
        int pn = 0;
        int[] pr = new int[MAXPERR];
        for (int i=0; i<buf.length; i++) {
            if ((NUMBITS[buf[i]&0xff] & 1) == 0) {
                if (pn < MAXPERR)
                    pr[pn] = i;
                pn++;
            }
        }
        if (pn > MAXPERR) {
            return;
        }
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
            if ((NUMBITS[buf[i]&0xff] & 1) == 0) {
                System.err.format("%s: parity check failure on channel %d%n",
                        Main.MYNAME, rawMessage.getChannel());
                return;
            }
            buf[i] &= 0x7f;
        }

        /* send message to output thread */
        DemodMessage demodMessage = new DemodMessage(
                rawMessage.getTime(),
                rawMessage.getChannel(),
                blkErr, buf);
        if (out.write(demodMessage))
            System.err.format("%s: demod data lost on channel %d%n",
                    Main.MYNAME, rawMessage.getChannel());
    }
}
