package name.blackcap.jacarsdec;

public class OutputThread extends Thread {
	private Channel<DemodMessage> in;
	
	public OutputThread(Channel<DemodMessage> in) {
		this.in = in;
	}

	public void run() {
		System.out.println("Output " + Thread.currentThread().getId() + " started."); // debug
		/* not finished, so currently a no-op */
		while (true)
			try {
				Thread.sleep(86400 * 1000);
			} catch (InterruptedException e) {
				break;
			}
	}
}
