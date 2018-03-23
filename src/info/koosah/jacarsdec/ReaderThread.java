/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package info.koosah.jacarsdec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.List;

import javax.sound.sampled.*;

/**
 * The sort of thread that reads from the audio device and hands the
 * digitized audio to a demod thread.
 *
 * @author davidb
 *
 */
public class ReaderThread extends Thread {
    private TargetDataLine line;
    private int channels;
    private int[] select;
    private List<Channel<RawMessage>> chans;

    /* number of samples we read at once */
    private static final int SAMPLES = 16384;

    public static class ReaderThreadException extends RuntimeException {
        public ReaderThreadException() { super(); }
        public ReaderThreadException(String message) { super(message); }
        public ReaderThreadException(String message, Throwable cause) { super(message, cause); }
        public ReaderThreadException(Throwable cause) { super(cause); }
    }

    /**
     *
     * @param line          Line to read from
     * @param channels      Number of channels the line has
     * @param select        List of channels to select
     * @param chans         List of IPC channels to write
     */
    public ReaderThread(TargetDataLine line, int channels, int[] select, List<Channel<RawMessage>> chans) {
        if (select.length != chans.size())
            throw new IllegalArgumentException("select and chan arrays must be same length");
        this.line = line;
        this.channels = channels;
        this.select = select;
        this.chans = chans;
    }

    public void run() {
        AudioFormat format = line.getFormat();
        int frameSize = format.getFrameSize();
        int sampleSize = format.getSampleSizeInBits() / 8;
        byte[] buf = new byte[frameSize * SAMPLES];
        ByteBuffer bbuf = ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder());

        line.start();
        while (true) {
            // read
            int nbytes = line.read(buf,  0,  buf.length);
            if (nbytes != buf.length) {
                throw new ReaderThreadException("Audio device got closed!");
            }
            Date timeRead = new Date();

            // extract channels of interest
            bbuf.rewind();
            float[][] bufs = new float[select.length][SAMPLES];
            for (int fr=0; fr<SAMPLES; fr++) {
                for(int ch=0; ch<select.length; ch++) {
                    bufs[ch][fr] = (float) bbuf.getShort(fr*frameSize + sampleSize*select[ch]) / 32768.0f;
                }
            }

            // write
            int i=0;
            for (Channel<RawMessage> chan : chans) {
                if (chan.write(new RawMessage(timeRead, select[i], bufs[i])))
                    System.err.format("%s: raw data lost on channel %d%n", Main.MYNAME, select[i]);
                i++;
            }
        }
    }
}
