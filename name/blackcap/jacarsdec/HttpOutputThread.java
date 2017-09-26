/*
 * This software is distributed under the Creative Commons Attribution 4.0
 * International license. See LICENSE.TXT in the main directory of this
 * repository for more information.
 */

package name.blackcap.jacarsdec;

import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.io.IOException;
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
    
    /* this is a test */

    private DemodMessage demodMessage;
    private Timer timer;

    private static final SimpleDateFormat JSON_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
    static {
        JSON_TIME.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public HttpOutputThread(Channel<DemodMessage> in, String url, String auth) throws MalformedURLException {
        this.in = in;
        this.url = new URL(url);
        this.auth = auth;
        timer = new Timer(true);
    }

    public void run() {
        bypassSslAuth();
        demodMessage = null;
        while (true) {
            try {
                demodMessage = in.read();
            } catch (InterruptedException e) {
                break;
            }
            if (demodMessage == null)
                break;
            sendMessage();
        }
    }

    /* xxx - this should be made to use fingerprints */
    private void bypassSslAuth() {
        // Create an all-trusting trust manager
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                }
            }
        };

        // Get the current SSL context
        SSLContext sc = null;
        try {
            sc = SSLContext.getInstance("SSL");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // Create empty HostnameVerifier
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        };

        // Install the all-trusting trust manager
        try {
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }

        // Make the all-trusting manager and hostname verifier the defaults
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
    }

    private void sendMessage() {
        // Build a JSON message.
        JsonObject jdata = Json.createObjectBuilder()
                .add("auth", auth)
                .add("time", JSON_TIME.format(demodMessage.getTime()))
                .add("channel", demodMessage.getChannel())
                .add("message", demodMessage.getRawAsString()).build();

        // Post it.
        NetworkTimeout timeout = new NetworkTimeout();
        try {
            // Be paranoid; don't trust the built-in timeouts to prevent
            // constipation in all cases. xxx - This won't strictly enforce
            // timeouts either, but it's better than nothing. JVM braindamage
            // makes a true solution needlessly difficult to code.
            timer.schedule(timeout, 60000);
            checkForInterrupt();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", Main.MYNAME);
            conn.setDoOutput(true);
            checkForInterrupt();
            JsonWriter writer = Json.createWriter(conn.getOutputStream());
            try {
                checkForInterrupt();
                writer.writeObject(jdata);
            } finally {
                writer.close();
            }
            // Verify we got a successful response
            checkForInterrupt();
            int status = conn.getResponseCode();
            if (!(status >= 200) && (status <= 299)) {
                checkForInterrupt();
                System.err.format("%s: got %03d", Main.MYNAME, status);
                String message = conn.getResponseMessage();
                if (message != null)
                    System.err.format(" %s", message);
                System.err.println(" response");
            }
        } catch(InterruptedException|JsonException|IOException e) {
            System.err.format("%s: %s in %s", Main.MYNAME, e.getClass().getCanonicalName(), getClass().getName());
            String message = e.getMessage();
            if (message != null)
                System.err.format(" - %s", message);
            System.err.println();
        } finally {
            if (timeout != null)
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
