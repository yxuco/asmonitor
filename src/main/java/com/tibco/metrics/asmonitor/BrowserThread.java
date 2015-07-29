package com.tibco.metrics.asmonitor;

public class BrowserThread implements Runnable {
	SpaceStatsBrowser browser;

	public BrowserThread(SpaceStatsBrowser browser) {
		this.browser = browser;
	}
	
	public void run() {
		browser.collectMetrics();
	}

}
