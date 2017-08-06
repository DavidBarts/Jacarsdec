/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This represents a decoded ACARS message.
 * 
 * @author  David Barts <david.w.barts@gmail.com>
 *
 */
public class DemodMessage {
	/* ACARS message types */
	private static final String GENERAL_LAB = "General response, demand mode; no information to transmit";
	private static final String AIRLINE_LAB =  "Airline defined message";
	private static final Map<String, String> EXPLANATIONS =
			Collections.unmodifiableMap(new HashMap<String, String>() {{ 
			    put("_", GENERAL_LAB);
			    put("_d", GENERAL_LAB);
			    put("_\u007f", GENERAL_LAB);
			    put("_j", "No info to transmit; polled mode");
			    put("00", "Emergency situation report");
			    put("2S", "Weather request");
			    put("2U", "Weather");
			    put("4M", "Cargo information");
			    put("51", "Ground GMT request/response");
			    put("52", "Ground UTC request/response");
			    put("54", "Aircrew initiated voice contact request");
			    put("57", "Alternate aircrew initiated position report");
			    put("5D", "ATIS request");
			    put("5P", "Temporary suspension of ACARS");
			    put("5R", "Aircraft initiated position report");
			    put("5U", "Weather request");
			    put("5Y", "Revision to previous ETA");
			    put("5Z", "Airline designated downlink");
			    put("7A", "Aircraft initiated engine data");
			    put("7B", "Aircraft initiated miscellaneous message");
			    put("80", "Aircraft addressed downlink 0");
			    put("81", "Aircraft addressed downlink 1");
			    put("82", "Aircraft addressed downlink 2");
			    put("83", "Aircraft addressed downlink 3");
			    put("84", "Aircraft addressed downlink 4");
			    put("85", "Aircraft addressed downlink 5");
			    put("86", "Aircraft addressed downlink 6");
			    put("87", "Aircraft addressed downlink 7");
			    put("88", "Aircraft addressed downlink 8");
			    put("89", "Aircraft addressed downlink 9");
			    put("8~", "Aircraft addressed downlink 10");
			    put("A1", "Deliver oceanic clearance");
			    put("A2", "Deliver departure clearance");
			    put("A4", "Acknowledge PDC");
			    put("A5", "Request position report");
			    put("A6", "Request ADS report");
			    put("A7", "Forward free text to aircraft");
			    put("A8", "Deliver departure slot");
			    put("A9", "Deliver ATIS information");
			    put("A0", "ATIS Facilities notification");
			    put("AA", "ATC Communications");
			    put("AB", "Terminal Weather Information for Pilots (TWIP)");
			    put("AC", "Pushback clearance");
			    put("AD", "Expected taxi clearance");
			    put("AE", "Unassigned");
			    put("AF", "CPC Command Response");
			    put("AG", "Unassigned");
			    put("B1", "Request oceanic clearance");
			    put("B2", "Request oceanic readback");
			    put("B3", "Request departure clearance");
			    put("B4", "Acknowledge departure clearance");
			    put("B5", "Provide position report");
			    put("B6", "Provide ADS report");
			    put("B7", "Forward free text to ATS");
			    put("B8", "Request departure slot");
			    put("B9", "Request ATIS information");
			    put("B0", "ATS Facility Notification (AFN)");
			    put("BA", "ATC communications");
			    put("BB", "Terminal Weather Information for Pilots (TWIP)");
			    put("BC", "Pushback clearance request");
			    put("BD", "Expected taxi clearance request");
			    put("BE", "CPC log-on/log-off request");
			    put("BF", "CPC WILCO/unassigned BLE response");
			    put("BG", "Unassigned");
			    put("C0", "Uplink message to all cockpit printers");
			    put("C1", "Uplink message to printer #1");
			    put("C2", "Uplink message to printer #2");
			    put("C3", "Uplink message to printer #3");
			    put("C4", "Uplink message to printer #4");
			    put("C5", "Uplink message to printer #5");
			    put("C6", "Uplink message to printer #6");
			    put("C7", "Uplink message to printer #7");
			    put("C8", "Uplink message to printer #8");
			    put("C9", "Uplink message to printer #9");
			    put("CA", "Printer status = error");
			    put("CB", "Printer status = busy");
			    put("CC", "Printer status = local");
			    put("CD", "Printer status = no paper");
			    put("CE", "Printer status = buffer overrun");
			    put("CF", "Printer status = reserved");
			    put("EI", "Internet e-mail message");
			    put("F3", "Dedicated transceiver advisory");
			    put("H1", "Message to/from terminal");
			    put("H2", "Meteorological report");
			    put("H3", "Icing report");
			    put("HX", "Undelivered uplink report");
			    put("M1", "IATA Departure message");
			    put("M2", "IATA Arrival message");
			    put("M3", "IATA Return to ramp message");
			    put("M4", "IATA Return from airborne message");
			    put("Q0", "ACARS link test");
			    put("Q1", "ETA Departure/arrival reports");
			    put("Q2", "ETA reports");
			    put("Q3", "Clock update");
			    put("Q4", "Voice circuit busy (response to 54)");
			    put("Q5", "Unable to process uplinked messages");
			    put("Q6", "Voice-to-ACARS change-over");
			    put("Q7", "Delay message");
			    put("QA", "Out/fuel report");
			    put("QB", "Off report");
			    put("QC", "On report");
			    put("QD", "In/fuel report");
			    put("QE", "Out/fuel destination report");
			    put("QF", "Off/destination report");
			    put("QG", "Out/return in report");
			    put("QH", "Out report");
			    put("QK", "Landing report");
			    put("QL", "Arrival report");
			    put("QM", "Arrival information report");
			    put("QN", "Diversion report");
			    put("QP", "OUT report");
			    put("QQ", "OFF report");
			    put("QR", "ON report");
			    put("QS", "IN report");
			    put("QT", "OUT/return IN report");
			    put("QX", "Intercept");
			    put("S1", "Network statistics request/response");
			    put("S2", "VHF performance report request");
			    put("S3", "LRU configuration request/response");
			    put("SA", "Media advisory");
			    put("SQ", "Squitter message");
			    put("X1", "Service provider defined DSP");
			    put("RA", "Command aircraft term. to transmit data");
			    put("RB", "Response of aircraft terminal to RA message");
			    put(":;", "Command aircraft xcvr to change frequency");
			    put("10", AIRLINE_LAB);
			    put("11", AIRLINE_LAB);
			    put("12", AIRLINE_LAB);
			    put("13", AIRLINE_LAB);
			    put("14", AIRLINE_LAB);
			    put("15", AIRLINE_LAB);
			    put("16", AIRLINE_LAB);
			    put("17", AIRLINE_LAB);
			    put("18", AIRLINE_LAB);
			    put("19", AIRLINE_LAB);
			    put("20", AIRLINE_LAB);
			    put("21", AIRLINE_LAB);
			    put("22", AIRLINE_LAB);
			    put("23", AIRLINE_LAB);
			    put("24", AIRLINE_LAB);
			    put("25", AIRLINE_LAB);
			    put("26", AIRLINE_LAB);
			    put("27", AIRLINE_LAB);
			    put("28", AIRLINE_LAB);
			    put("29", AIRLINE_LAB);
			    put("30", AIRLINE_LAB);
			    put("31", AIRLINE_LAB);
			    put("32", AIRLINE_LAB);
			    put("33", AIRLINE_LAB);
			    put("34", AIRLINE_LAB);
			    put("35", AIRLINE_LAB);
			    put("36", AIRLINE_LAB);
			    put("37", AIRLINE_LAB);
			    put("38", AIRLINE_LAB);
			    put("39", AIRLINE_LAB);
			    put("40", AIRLINE_LAB);
			    put("41", AIRLINE_LAB);
			    put("42", AIRLINE_LAB);
			    put("43", AIRLINE_LAB);
			    put("44", AIRLINE_LAB);
			    put("45", AIRLINE_LAB);
			    put("46", AIRLINE_LAB);
			    put("47", AIRLINE_LAB);
			    put("48", AIRLINE_LAB);
			    put("49", AIRLINE_LAB);
			    put("4~", AIRLINE_LAB);
			}});
	
	/* ACARS H1 message subtypes */
	private static final String AIRLINE_H1 = "Airline defined";
	private static final String TERMINAL_H1 = "Cabin terminal";
	private static final Map<String, String> H1_EXPLANATIONS =
			Collections.unmodifiableMap(new HashMap<String, String>() {{ 
			    put("CF", "Central fault data indicator");
			    put("DF", "Flight data recorder");
			    put("EC", "Engine display system");
			    put("EI", "Engine report");
			    put("H1", "HF data radio #1");
			    put("H2", "HF data radio #2");
			    put("HD", "HF data radio");
			    put("M1", "Flight management computer #1");
			    put("M2", "Flight management computer #2");
			    put("M3", "Flight management computer #3");
			    put("MD", "Flight management computer");
			    put("PS", "Keyboard/display unit");
			    put("S1", "Satellite data unit #1");
			    put("S2", "Satellite data unit #2");
			    put("SD", "Satellite data unit");
			    put("T0", TERMINAL_H1);
			    put("T1", TERMINAL_H1);
			    put("T2", TERMINAL_H1);
			    put("T3", TERMINAL_H1);
			    put("T4", TERMINAL_H1);
			    put("T5", TERMINAL_H1);
			    put("T6", TERMINAL_H1);
			    put("T7", TERMINAL_H1);
			    put("T8", TERMINAL_H1);
			    put("WO", "Weather observation");
			}});
	
	/* Character set the messages use. */
	private static final Charset CHARSET = Charset.forName("US-ASCII");
	
	/* For returning explanations of unknown things */
	private static final String UNKNOWN = "Unknown";
	
	/* For keeping track of whether this message has been parsed. */
	private enum MessageState { UNPARSED, BAD, GOOD };
	private MessageState state;
	
	/* ACARS message fields, can only be retrieved after a successful parse */
	private String registration;
	public String getRegistration() {
		verifyState();
		return registration;
	}
	
	private String flightId;
	public String getFlightId() {
		verifyState();
		return flightId;
	}
	
	private String label;
	public String getLabel() {
		verifyState();
		return label;
	}
	public String getLabelExplanation() {
		return EXPLANATIONS.getOrDefault(getLabel(), UNKNOWN);
	}
	
	private char mode;
	public char getMode() {
		verifyState();
		return mode;
	}
	
	private char blockId;
	public char getBlockId() {
		verifyState();
		return blockId;
	}
	
	private char acknowledge;
	public char getAcknowledge() {
		verifyState();
		return acknowledge;
	}
	
	private String messageId;
	public String getMessageId() {
		verifyState();
		return messageId;
	}
	
	private String source;
	public String getSource() {
		verifyState();
		return source;
	}
	public String getSourceExplanation() {
		if (source == null)
			throw new IllegalStateException("No message source!");
		char ch1 = getSource().charAt(0);
		if (ch1 >= '0' && ch1 <= '9')
			return AIRLINE_H1;
		else
			return H1_EXPLANATIONS.getOrDefault(getSource(), UNKNOWN);
	}
	
	private String message;
	public String getMessage() {
		verifyState();
		return message;
	}
	
	/* parameters passed on from RawMessage, can always be retrieved */
	private Date time;
	public Date getTime() {
		return time;
	}
	
	private int channel;
	public int getChannel() {
		return channel;
	}
	
	private int errors;
	public int getErrors() {
		return errors;
	}
	
	private byte[] raw;
	public byte[] getRaw() {
		return raw;
	}
	
	/**
	 * Constructor
	 * @param time			Time message was received.
	 * @param channel		Audio channel it was received on.
	 * @param level			Message signal level.
	 * @param errors		Error count.
	 * @param raw			Byte array containing the raw message.
	 */
	public DemodMessage(Date time, int channel, int errors, byte[] raw) {
		this.time = time;
		this.channel = channel;
		this.errors = errors;
		this.raw = raw;
		state = MessageState.UNPARSED;
	}
	
	private void verifyState() {
		switch (state) {
		case UNPARSED:
			throw new IllegalStateException("Message has not been parsed.");
		case BAD:
			throw new IllegalStateException("Message could not be parsed.");
		case GOOD:
			break;
		}
	}
	
	/*
	 * Despite not being synchronized, the following method is thread-safe;
	 * each parallel-running instance will get its own local variables and
	 * set the instance variables to the same values. This is admittedly
	 * inefficient, but harmless, and synchronization has its own costs.
	 */
	
	/**
	 * Parse this message into its various fields. This is done separate from
	 * construction, so that the construction phase is simpler and faster.
	 * @return				Whether or not parsing was successful.
	 */
	public boolean parse() {
		/* refuse to parse twice */
		if (state != MessageState.UNPARSED)
			return state == MessageState.GOOD;
		
		/* runt packets are not parseable */
		if (raw.length < 13) {
			state = MessageState.BAD;
			return false;
		}
		
		/* ensure it's ASCII (as it must be) */
		for (byte b : raw) {
			if (b < 0) {
				state = MessageState.BAD;
				return false;
			}
		}
		
		/* parse */
		mode = (char) raw[0];
		registration = new String(raw, 1, 7, CHARSET);
		acknowledge = (char) raw[8];
		label = new String(raw, 9, 2, CHARSET);
		blockId = (char) raw[11];
		byte blockStart = raw[12];
		int k = 13;
		if (blockStart == 3) {
			messageId = null;
			flightId = null;
		} else if (mode <= 'Z' && blockId <= '9') {
			int len = Integer.min(raw.length, k+4) - k;
			messageId = new String(raw, k, len, CHARSET);
			k += len;
			len = Integer.min(raw.length, k+6) - k;
			flightId = new String(raw, k, len, CHARSET);
			k += len;
		}
		int len = Integer.max(0, raw.length - k - 1);
		message = new String(raw, k, len, CHARSET);
		if (label.equals("H1")) {
			int mesh = message.indexOf((int) '#');
			if (mesh == -1 || mesh > 3 || message.length() - mesh < 3)
				source = null;
			else
				source = message.substring(mesh+1, mesh+3);
		} else {
			source = null;
		}
		
		/* remember we parsed and return success */
		state = MessageState.GOOD;
		return true;
	}
}
