/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.txt in the main directory of this
 * repository for more information.
 */

package info.koosah.jacarsdec;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.cli.*;

/**
 * Entry point for the utility that reads packets from acarsdec (not
 * Jacarsdec!) in -o 6 mode and sends them on to Koosah. This is here
 * as a quick and dirty way to enable Linux systems with RTL SDR's to
 * send data to us.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class AcarsdecToKoosah {

    public static final String MYNAME = "AcarsdecToKoosah";
    public static final String DEFAULT_PROPS = "post.properties";
    public static final int HEADER_LEN = 16;
    public static CommandLine cmdLine;

    public static void main(String[] args) {
        // Parse command-line options. The ones for endianness should typically
        // not be needed; by default, this code matches the native byte order
        // sent by acarsdec -o 6.
        Options options = new Options();
        OptionGroup endianness = new OptionGroup();
        endianness.addOption(new Option("b", "big", false, "Input is big-endian."));
        endianness.addOption(new Option("l", "little", false, "Input is little-endian."));
        options.addOptionGroup(endianness);
        options.addOption(new Option("h", "help", false, "Print this help message."));
        options.addOption(new Option("s", "size", true, "Buffer size."));
        try {
            cmdLine = (new DefaultParser()).parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println(MYNAME + ": " + getMessage(e));
            System.exit(2);
        }
        if (cmdLine.hasOption("help")) {
            (new HelpFormatter()).printHelp(MYNAME + " [options] [properties-file]", options);
            System.exit(0);
        }

        // Get POST properties file to use
        String[] extraArgs = cmdLine.getArgs();
        String propsFile = null;
        switch (extraArgs.length) {
        case 0:
            propsFile = DEFAULT_PROPS;
            break;
        case 1:
            propsFile = extraArgs[0];
            break;
        default:
            System.err.println(MYNAME + ": too many arguments");
            System.exit(2);
        }

        // Load POST properties
        Properties props = new Properties();
        try (BufferedReader rdr = new BufferedReader(new FileReader(propsFile))) {
            props.load(rdr);
        } catch (IOException e) {
            System.err.format("%s: unable to load properties - %s%n", MYNAME, getMessage(e));
            System.exit(1);
        }

        // Determine byte order
        ByteOrder order = ByteOrder.nativeOrder();
        if (cmdLine.hasOption("big"))
            order = ByteOrder.BIG_ENDIAN;
        else if (cmdLine.hasOption("little"))
            order = ByteOrder.LITTLE_ENDIAN;

        // Determine buffer size
        int bufSize = 50;
        String rawSize = cmdLine.getOptionValue("size");
        if (rawSize != null) {
            try {
                bufSize = Integer.parseInt(rawSize);
            } catch (NumberFormatException e) {
                System.err.format("%s: invalid value for --size - %s%n", MYNAME, rawSize);
                System.exit(1);
            }
        }

        // Wire things up
        Channel<DemodMessage> hChan = new Channel<DemodMessage>(bufSize);
        HttpOutputThread hWriter = null;
        try {
            hWriter = new HttpOutputThread(hChan, props);
        } catch (MalformedURLException e) {
            System.err.format("%s: %s%n", MYNAME, getMessage(e));
            System.exit(1);
        }
        hWriter.start();

        // Loop, processing our input
        byte[] rawHeader = new byte[HEADER_LEN];
        ByteBuffer header = ByteBuffer.wrap(rawHeader);
        header.order(order);
        try {
            while (true) {
                header.clear();
                int nread = System.in.read(rawHeader);
                if (nread != HEADER_LEN)
                    break;
                long sec = header.getLong();
                int usec = header.getInt();
                short channel = header.getShort();
                short length = header.getShort();
                byte[] body = new byte[length];
                nread = System.in.read(body);
                if (nread != length)
                    break;
                DemodMessage msg = new DemodMessage(
                    new Date(sec*1000 + usec/1000), (int) channel, 0, body);
                hChan.write(msg);
            }
        } catch (IOException e) {
            System.err.format("%s: %s%n", MYNAME, getMessage(e));
            hWriter.interrupt();
            System.exit(1);
        }
    }

    public static String getMessage(Throwable e) {
        String ret = e.getMessage();
        if (ret == null)
            ret = e.getClass().getCanonicalName();
        return ret;
    }
}
