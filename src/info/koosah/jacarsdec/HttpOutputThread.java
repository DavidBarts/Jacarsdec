/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package info.koosah.jacarsdec;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.json.*;
import javax.net.ssl.*;

/**
 * Format and print demodulated ACARS to standard output.
 *
 * @author David Barts <david.w.barts@gmail.com>
 *
 */
public class HttpOutputThread extends Thread {

    private Channel<DemodMessage> in;
    private URL url;
    private String auth;
    private byte[] fingerprint;

    private DemodMessage demodMessage;
    private Timer timer;
    private boolean useStdAuth;
    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;

    /* lengths of the various fingerprint types we support, in bytes */
    private static final int MD5_LEN = 16;
    private static final int SHA1_LEN = 20;
    private static final int SHA256_LEN = 32;

    private static final SimpleDateFormat JSON_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    static {
        JSON_TIME.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public HttpOutputThread(Channel<DemodMessage> in, Properties props) throws MalformedURLException {
        this.in = in;
        // Must specify the URL, because it's pointless if we don't.
        url = new URL(mustGetProperty(props, "url"));
        boolean https = url.getProtocol().equalsIgnoreCase("https");
        // Must specify the authenticator, because we want to make insecure
        // empty ones explicit.
        auth = mustGetProperty(props, "auth");
        if (!https && !auth.isEmpty())
            System.err.format("%s: warning - sending non-empty authenticators plaintext%n", Main.MYNAME);
        // Fingerprint is optional; we do the standard cert authentication
        // if it's omitted.
        String rawFing = props.getProperty("fingerprint");
        useStdAuth = rawFing == null;
        if (useStdAuth) {
            fingerprint = null;
        } else if (rawFing.isEmpty()) {
            if (https)
                System.err.format("%s: warning - not authenticating SSL certificates%n", Main.MYNAME);
            fingerprint = null;
        } else
            fingerprint = parseFing(rawFing);
        timer = new Timer(true);
        socketFactory = null;
        hostnameVerifier = null;
    }

    /*
     * We require all three properties (URL, authenticator, fingerprint) be
     * specified. This is so insecure configurations must be explicit.
     */
    private String mustGetProperty(Properties props, String name) {
        String ret = props.getProperty(name);
        if (ret == null)
            throw new IllegalArgumentException("Missing required property " + name + ".");
        return ret;
    }

    private byte[] parseFing(String s) {
        String s2 = s.replaceAll(":", "");
        int len = s2.length();
        if (len != MD5_LEN*2 && len != SHA1_LEN*2 && len != SHA256_LEN*2)
            throw new IllegalArgumentException("bad fingerprint - " + s);
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int v0 = Character.digit(s2.charAt(i), 16);
            int v1 = Character.digit(s2.charAt(i+1), 16);
            if (v0 < 0 || v1 < 0)
                throw new IllegalArgumentException("bad fingerprint - " + s);
            data[i/2] = (byte) ((v0 << 4) | v1);
        }
        return data;
    }

    public void run() {
        if (!useStdAuth)
            bypassSslAuth(fingerprint);
        demodMessage = null;
        while (true) {
            try {
                demodMessage = in.read();
            } catch (InterruptedException e) {
                break;
            }
            if (demodMessage == null)
                break;
            try {
                sendMessage();
            } catch (Exception e) {
                System.err.println("Unexpected exception in sendMessage:");
                e.printStackTrace();
            }
        }
    }

    /*
     * We authenticate SSL certs based on their fingerprint instead of
     * using the standard means. That is because SSL certs have limited
     * lifetimes, which the standard means enforce, and we don't want
     * to impose the burden of upgrading certs on remote receivers on
     * ourselves. All standard fingerprint types are supported, but
     * SHA-256 is recommended, as it is the most secure.
     */
    public void bypassSslAuth(final byte[] fing) {
        // Determine fingerprint type from its length
        final String type;
        if (fing == null) {
            type = null;
        } else {
            switch (fing.length) {
                case MD5_LEN:
                    type = "MD5";
                    break;
                case SHA1_LEN:
                    type = "SHA-1";
                    break;
                case SHA256_LEN:
                    type = "SHA-256";
                    break;
                default:
                    throw new IllegalArgumentException("Invalid fingerprint.");
            }
        }

        // Create a trust manager
        TrustManager[] trustManager = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    matchFing(certs);
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    matchFing(certs);
                }

                private void matchFing(X509Certificate[] certs) throws CertificateException {
                    if (fing == null)
                        return;
                    MessageDigest md = null;
                    try {
                        md = MessageDigest.getInstance(type);
                    } catch (NoSuchAlgorithmException e) {
                        throw new CertificateException(e);
                    }
                    for (X509Certificate cert: certs) {
                        md.reset();
                        if (Arrays.equals(md.digest(cert.getEncoded()), fing))
                            return;
                    }
                    throw new CertificateException("No matching fingerprint found.");
                }
            }
        };

        // Install the trust manager
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("SSL");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // Create empty HostnameVerifier
        hostnameVerifier = new HostnameVerifier() {
            public boolean verify(String arg0, SSLSession arg1) {
                    return true;
            }
        };

        try {
            sslContext.init(null, trustManager, new java.security.SecureRandom());
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
        socketFactory = sslContext.getSocketFactory();
    }

    private void sendMessage() throws Exception {
        // Get raw message; silently discard bad messages.
        String rawMessage = demodMessage.getRawAsString();
        if (rawMessage == null)
            return;

        // Build a JSON message.
        String jString = Json.createObjectBuilder()
                .add("auth", auth)
                .add("time", JSON_TIME.format(demodMessage.getTime()))
                .add("channel", demodMessage.getChannel())
                .add("message", rawMessage)
                .build().toString();

        // POST it.
        NetworkTimeout timeout = new NetworkTimeout();
        try {
            // Be paranoid; don't trust the built-in timeouts to prevent
            // constipation in all cases. xxx - This won't strictly enforce
            // timeouts either, but it's better than nothing. Java braindamage
            // makes a true solution needlessly difficult to code.
            timer.schedule(timeout, 60000);
            checkForInterrupt();
            // Do our special cert authentication if requested.
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection sconn = (HttpsURLConnection) conn;
                if (socketFactory != null)
                    sconn.setSSLSocketFactory(socketFactory);
                if (hostnameVerifier != null)
                    sconn.setHostnameVerifier(hostnameVerifier);
            }
            // Set timeouts and other standard parameters.
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", Main.MYNAME);
            conn.setDoOutput(true);
            checkForInterrupt();
            // Debug
            System.out.println("Sending data:");
            System.out.println(jString);
            // Send POST data.
            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(jString.getBytes(UTF8));
                stream.flush();
            }
            // Verify we got a successful response.
            checkForInterrupt();
            int status = conn.getResponseCode();
            if (!(status >= 200 && status <= 299)) {
                checkForInterrupt();
                System.err.format("%s: got %03d", Main.MYNAME, status);
                String message = conn.getResponseMessage();
                if (message != null)
                    System.err.format(" %s", message);
                System.err.println(" response");
            }
        } finally {
            timeout.cancel();
        }
    }

    private void checkForInterrupt() throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
    }

    private class NetworkTimeout extends TimerTask {
        private Thread toInterrupt;

        public NetworkTimeout() {
            toInterrupt = Thread.currentThread();
        }

        public void run() {
            toInterrupt.interrupt();
        }
    }
}
