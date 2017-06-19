/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.ArrayList;

import javax.sound.sampled.*;
import org.apache.commons.cli.*;

/**
 * Entry point.
 * 
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class Main {
	
	public static final String MYNAME = "Jacarsdec";
	private static final int SSIZE = 16;  // sample size (bits)
	private static final int RATE = 44100;  // sample rate (Hz)
	
	public static CommandLine cmdLine;
	
	private static class LineSpecifierException extends Exception {
		public LineSpecifierException() { super(); }
		public LineSpecifierException(String message) { super(message); }
		public LineSpecifierException(String message, Throwable cause) { super(message, cause); }
		public LineSpecifierException(Throwable cause) { super(cause); }
	}

	public static void main(String[] args) {
		// Parse command-line options
		Options options = new Options();
		// would be nice to make first letters unique, then no d and D.
		options.addOption(new Option("c", "channels", true, "Number input channels to open (typ. 1 or 2)."));
		options.addOption(new Option("s", "select", true, "Channels to select (comma-separated list, 0-based)."));
		options.addOption(new Option("i", "input-size", true, "Input buffer size."));
		options.addOption(new Option("o", "output-size", true, "Output buffer size."));
		options.addOption(new Option("h", "help", false, "Print this help message."));
		options.addOption(new Option("l", "list", false, "List available audio devices and exit."));
		options.addOption(new Option("q", "quiet", false, "Suppress start-up messages."));
		try {
			cmdLine = (new DefaultParser()).parse(options, args);
		} catch (org.apache.commons.cli.ParseException e) {
			System.err.println(MYNAME + ": " + e.getMessage());
			System.exit(2);
		}
		if (cmdLine.hasOption("help")) {
			(new HelpFormatter()).printHelp(MYNAME + " [options] mixer line", options);
			System.exit(0);
		}
		
		// If -l/--list specified, just list the devices.
		if (cmdLine.hasOption("list")) {
			listAudioDevices();
		} else {
			demodulateAcars();
		}
	}
	
	private static void listAudioDevices() {
		boolean printed = false;
		Mixer.Info[] mix = AudioSystem.getMixerInfo();
		for(int i=0; i<mix.length; i++) {
			Mixer.Info mi = mix[i];
			Mixer mx = null;
			try {
				mx = AudioSystem.getMixer(mi);
			} catch (SecurityException e) {
				// if we don't have permission to use it, don't list it
				continue;
			}
			if (printed)
				System.out.println();
			System.out.format("Mixer: %d%n", i);
			System.out.format("Name: %s%n", mi.getName());
			System.out.format("Vendor: %s%n", mi.getVendor());
			System.out.format("Version: %s%n", mi.getVersion());
			System.out.format("Description: %s%n", mi.getDescription());
			listLines("Input", mx.getTargetLineInfo());
			listLines("Output", mx.getSourceLineInfo());
			printed = true;
		}
		if (!printed) {
			System.err.println(MYNAME + ": No permitted devices found!");
			System.exit(1);
		}
	}
	
	private static void listLines(String title, Line.Info[] li) {
		for(int i=0; i<li.length; i++) {
			System.out.format("%s %d: %s%n", title, i, li[i].toString());
		}
	}
	
	private static void demodulateAcars() {
		// Get mixer and line IDs
		String[] args = cmdLine.getArgs();
		if (args.length != 2) {
			System.err.println(MYNAME + ": expecting mixer and line IDs");
			System.exit(1);
		}
		int mixerId = toInt("mixer", args[0]);
		int lineId = toInt("line", args[1]);
		
		// Get the mixer and line to use
		Mixer.Info[] mix = AudioSystem.getMixerInfo();
		Mixer.Info mi = null;
		try {
			mi = mix[mixerId];
		} catch (IndexOutOfBoundsException e) {
			System.err.format("%s: invalid mixer ID %d%n", MYNAME, mixerId);
			System.exit(1);
		}
		Mixer mixer = AudioSystem.getMixer(mi);
		Line.Info[] lix = mixer.getTargetLineInfo();
		Line.Info li = null;
		try {
			li = lix[lineId];
		} catch (IndexOutOfBoundsException e) {
			System.err.format("%s: invalid line ID %d%n", MYNAME, lineId);
			System.exit(1);
		}
		TargetDataLine line = null;
		try {
			line = (TargetDataLine) mixer.getLine(li);
		} catch (LineUnavailableException|IllegalArgumentException|SecurityException e) {
			System.err.format("%s: %s%n", MYNAME, e.getMessage());
			System.exit(1);
		}
		
		// Determine number of channels then open the device. Default is to
		// try using 2 channels; if that fails, try 1. Note that if you
		// change the encoding, you'll have to change ReaderThread! Note
		// that encodings other than PCM_SIGNED tend in my experience to
		// fail, sometimes exposing apparent bugs in the Java runtime (e.g.
		// reads block forever).
		boolean bigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
		int channels = toInt("channels", -1);
		if (channels == -1) {
			AudioFormat[] formats = {
				new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED, // encoding
					(float) RATE, // sample rate
					SSIZE, // sample size (bits)
					2, // channels
					2 * SSIZE / 8, // frame size (bytes)
					(float) RATE, // frame rate
					bigEndian // big endian?
				),
			    new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED, // encoding
					(float) RATE, // sample rate
					SSIZE, // sample size (bits)
					1, // channels
					SSIZE / 8, // frame size (bytes)
					(float) RATE, // frame rate
					bigEndian // big endian?
					) };
			Exception badOpen = null;
			for (AudioFormat format : formats) {
				try {
					channels = format.getChannels();
					line.open(format);
					badOpen = null;
					break;
				} catch (LineUnavailableException|IllegalArgumentException|IllegalStateException|SecurityException e) {
					badOpen = e;
				}
			}
			if (badOpen != null) {
				System.err.format("%s: %s%n", MYNAME, badOpen.getMessage());
				System.exit(1);
			}
		} else {
			AudioFormat format = new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED, // encoding
					(float) RATE, // sample rate
					SSIZE, // sample size (bits)
					channels, // channels
					channels * SSIZE / 8, // frame size (bytes)
					(float) RATE, // frame rate
					bigEndian // big endian?
					);
			try {
				line.open(format);
			} catch (LineUnavailableException|IllegalArgumentException|IllegalStateException|SecurityException e) {
				System.err.format("%s: %s%n", MYNAME, e.getMessage());
				System.exit(1);
			}
		}
		
		// Get the channels to actually select. Default is to just select
		// channel 0 (left).
		int[] select;
		if (cmdLine.hasOption("select")) {
			String rselect = cmdLine.getOptionValue("select");
			int commas = 0;
			for(int i=0; i < rselect.length(); i++) {
				if (rselect.charAt(i) == ',')
					commas += 1;
			}
			select = new int[commas+1];
			int last = 0;
			for (int i=0; i<select.length; i++) {
				int pos = rselect.indexOf((int) ',', last);
				try {
					if (pos == -1) {
						select[i] = Integer.parseInt(rselect.substring(last).trim());
					} else {
						select[i] = Integer.parseInt(rselect.substring(last, pos).trim());
					}
				} catch (NumberFormatException e) {
					System.err.format("%s: invalid channel selection - %s%n", MYNAME, rselect);
					System.exit(1);
				}
				last = pos + 1;
			}
		} else {
			select = new int[] { 0 };
		}
		
		// Get other parameters needed to wire things up.
		int inputSize = toInt("input-size", 50);
		int outputSize = toInt("output-size", inputSize * select.length);
		
		// Allocate message channels
		ArrayList<Channel<RawMessage>> inChans = new ArrayList<Channel<RawMessage>>(select.length);
		for (int i=0; i<select.length; i++) {
			inChans.add(new Channel<RawMessage>(inputSize));
		}
		Channel<DemodMessage> outChan = new Channel<DemodMessage>(outputSize);
		
		// Wire things up
		ReaderThread reader = new ReaderThread(line, channels, select, inChans);
		DemodThread[] demods = new DemodThread[select.length];
		for (int i=0; i<demods.length; i++) {
			demods[i] = new DemodThread(inChans.get(i), outChan, (float) RATE);
		}
		OutputThread writer = new OutputThread(outChan);
		
		// Log some standard start messages, unless in quiet mode
		if (!cmdLine.hasOption("quiet")) {
			System.out.format("This is %s at %tFT%<tTJ.%n", MYNAME, System.currentTimeMillis());
			System.out.format("Reading from %d-channel input line %d of mixer %d (%s).%n",
					channels, lineId, mixerId, mi.getName());
			System.out.format("Input buffer size %d, output buffer size %d.%n",
					inputSize, outputSize);
			System.out.format("%d threads total.%n%n", Thread.activeCount() + demods.length + 2);
		}
		
		// And away we go! We start things from the back first, so everything
		// will be ready when the reader is started.
		writer.start();
		for (DemodThread demod : demods) {
			demod.start();
		}
		reader.start();
		
		// Termination is by a keyboard interrupt, which we simply wait for,
		// indefinitely.
		while (true)
			try {
				Thread.sleep(84600 * 1000);
			} catch (InterruptedException e) {
				break;
			}
	}
	
	private static int toInt(String name, String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			System.err.format("%s: invalid value for %s - %s%n", MYNAME, name, raw);
			System.exit(1);
		}
		return -1;  /* here just to make Java happy */
	}
	
	private static int toInt(String name, int def) {
		String val = cmdLine.getOptionValue(name);
		if (val == null) {
			return def;
		} else {
			try {
				return Integer.parseInt(val);
			} catch (NumberFormatException e) {
				System.err.format("%s: invalid value for --%s - %s%n", MYNAME, name, val);
				System.exit(1);
			}
		}
		return -1;  /* here just to make Java happy */
	} 
}
