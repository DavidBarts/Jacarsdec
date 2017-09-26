/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Format and print demodulated ACARS to standard output.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class StandardOutputThread extends Thread {
    private Channel<DemodMessage> in;
    private DemodMessage demodMessage;

    private static final SimpleDateFormat LOCAL = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'J'");
    private static final SimpleDateFormat UTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static {
        UTC.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public StandardOutputThread(Channel<DemodMessage> in) {
        this.in = in;
    }

    public void run() {
        demodMessage = null;
        while (true) {
            try {
                demodMessage = in.read();
            } catch (InterruptedException e) {
                break;
            }
            if (demodMessage == null)
                break;
            printMessage();
        }
    }

    private void printMessage() {
        /* our standard header */
        System.out.format("%n[#%d E:%d %s %s ----------]%n",
                demodMessage.getChannel(),
                demodMessage.getErrors(),
                LOCAL.format(demodMessage.getTime()),
                UTC.format(demodMessage.getTime()));

        /* attempt to parse message, do a hex dump if we can't */
        if (!demodMessage.parse()) {
            System.out.print("Unparseable message :");
            int i = 0;
            for (byte b: demodMessage.getRaw()) {
                System.out.format(i++ % 24 == 0 ? "%n%02x" : " %02x", b);
            }
            System.out.println();
            return;
        }

        /* the ACARS header */
        if (demodMessage.getMode() < 0x5d) {
            System.out.print("Aircraft registration: ");
            seeString(demodMessage.getRegistration());
            System.out.print(" Flight ID: ");
            seeMsgIdFlt(demodMessage.getFlightId());
            System.out.println();
        }

        System.out.print("Mode: ");
        seeChar(demodMessage.getMode());
        System.out.println();

        System.out.print("Message label: ");
        seeString(demodMessage.getLabel());
        System.out.print(" (");
        System.out.print(demodMessage.getLabelExplanation());
        System.out.println(")");

        System.out.print("Block ID: ");
        seeChar(demodMessage.getBlockId());
        System.out.print(" Acknowledge: ");
        seeChar(demodMessage.getAcknowledge());
        System.out.println();

        System.out.print("Message ID: ");
        seeMsgIdFlt(demodMessage.getMessageId());
        System.out.println();

        if (demodMessage.getSource() != null) {
            System.out.print("Message source: ");
            seeString(demodMessage.getSource());
            System.out.print(" (");
            System.out.print(demodMessage.getSourceExplanation());
            System.out.println(")");
        }

        /* the message body */
        System.out.println("Message :");
        seeBuffer(demodMessage.getMessage());
    }

    /* this assumes ASCII (which is what ACARS uses) */
    private void seeChar(char c) {
        if (c < '\040' || c == '\177') {
            System.out.print('^');
            c ^= 0100;
        }
        System.out.print(c);
    }

    private void seeString(String s) {
        int len = s.length();
        for (int i=0; i<len; i++)
            seeChar(s.charAt(i));
    }

    private void seeMsgIdFlt(String s) {
        if (s == null)
            System.out.print("(none)");
        else if (s.isEmpty())
            System.out.print("(empty)");
        else
            seeString(s);
    }

    private void seeBuffer(String b) {
        int len = b.length();
        int last = len - 1;
        for (int i=0; i<len; i++) {
            char c = b.charAt(i);
            switch(c) {
            case '\t':
                /* tabs get passed verbatim */
                System.out.print(c);
                break;
            case '\r':
                /* carriage return before line feed or at end gets deleted */
                if (i != last && b.charAt(i+1) != '\n')
                    seeChar(c);
                break;
            case '\n':
                /* delete LF at end, map others to newline */
                if (i != last)
                    System.out.println();
                break;
            default:
                seeChar(c);
                break;
            }
        }
        System.out.println();
    }
}
