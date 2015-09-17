package com.tibco.metrics.asmonitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ASMonitor {

	// thread pool to fetch MBean data from multiple BE engines concurrently
	static ThreadPoolExecutor pool;

	// set to false if do not print out stats for system spaces
	static boolean collectSystemSpaces = true;

	// full path of directory for all stat report files, null for current
	// working directory
	static String reportFolder = null;

	// seconds to wait between consecutive MBean polls
	static int interval = 60;

	// all monitored AS metaspaces in hash metaspace -> SpaceStatsBrowser
	static HashMap<String, SpaceStatsBrowser> browserMap = new HashMap<String, SpaceStatsBrowser>();

	// include rule for space/member names
	// [space|member] -> list of pattern for space or member name
	static HashMap<String, Set<String>> includeMap = new HashMap<String, Set<String>>();

	// exclude rule for space/member names
	static HashMap<String, Set<String>> excludeMap = new HashMap<String, Set<String>>();

	/**
	 * Main driver to start monitoring BE inference engines.
	 *
	 * @param args
	 *            -config <config_file>
	 * @throws IOException
	 *             if failed to read specified config-file
	 */
	public static void main(String[] args) throws IOException {
		String configFile = null;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-config")) {
				configFile = args[i + 1];
			}

			if (args.length < 2 || args[i].contains("-help") || args[i].equals("-?")) {
				printUsage();
				System.exit(0);
			}
		}

		System.out.println(getVersions());
		System.out.println("Starting ASMonotor ...");

		// load monitor properties from config file, and create AS connections
		// for all listed metaspaces
		loadConfig(configFile);
		for (SpaceStatsBrowser browser : browserMap.values()) {
			initializeBrowser(browser);
		}

		// create thread pool
		pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
		pool.setKeepAliveTime(2 * interval, TimeUnit.SECONDS);

		System.out.println("Start monitoring ...");
		boolean forever = true;
		while (forever) {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			String timestamp = fmt.format(Calendar.getInstance().getTime());

			for (SpaceStatsBrowser browser : browserMap.values()) {
				browser.setTimestamp(timestamp);
				pool.execute(new BrowserThread(browser));
			}
			System.out.println(String.format("%d of %d threads are active", pool.getActiveCount(), pool.getPoolSize()));
			try {
				TimeUnit.SECONDS.sleep(interval);
			} catch (InterruptedException e) {
				forever = false;
				shutdown();
			}
		}
	}

	/**
	 * Load configuration file, and make AS connections to all configured AS
	 * metaspaces
	 *
	 * @param configFile
	 *            full path of the configuration file
	 * @throws IOException
	 * @throws Exception
	 *             when failed to read/parse configure file, or cannot connect
	 *             to AS
	 */
	private static void loadConfig(String configFile) throws IOException {
		System.out.println("Loading configuration from file " + configFile);
		Properties props = new Properties();
		FileInputStream fis = new FileInputStream(configFile);
		props.load(fis);
		fis.close();

		for (String key : props.stringPropertyNames()) {
			if (key.startsWith("metaspace.")) {
				if (key.startsWith("metaspace.name.")) {
					String metaspace = props.getProperty(key, "").trim();
					if (metaspace.length() > 0) {
						String discovery = props.getProperty(key.replaceFirst("name", "discovery"), "tibpgm").trim();
						String member = props.getProperty(key.replaceFirst("name", "member"), "").trim();
						if (!browserMap.containsKey(metaspace)) {
							System.out.println(
									String.format("Configure metaspace %s using discovery %s", metaspace, discovery));
							SpaceStatsBrowser browser = new SpaceStatsBrowser(metaspace, discovery, member);
							browserMap.put(metaspace, browser);
						}
					}
				}
			} else if (key.startsWith("include.")) {
				// add included entity pattern to filter type
				String[] tokens = key.split("\\.");
				Set<String> includes = includeMap.get(tokens[1]);
				if (null == includes) {
					includes = new HashSet<String>();
					includeMap.put(tokens[1], includes);
				}
				String pattern = props.getProperty(key, "").trim();
				if (pattern.length() > 0) {
					includes.add(pattern);
				}
				System.out.println(String.format("Report includes %s pattern %s", tokens[1], pattern));
			} else if (key.startsWith("exclude.")) {
				// add included entity pattern to filter type
				String[] tokens = key.split("\\.");
				Set<String> excludes = excludeMap.get(tokens[1]);
				if (null == excludes) {
					excludes = new HashSet<String>();
					excludeMap.put(tokens[1], excludes);
				}
				String pattern = props.getProperty(key, "").trim();
				if (pattern.length() > 0) {
					excludes.add(pattern);
				}
				System.out.println(String.format("Report excludes %s pattern %s", tokens[1], pattern));
			} else if (key.equals("interval")) {
				interval = Integer.parseInt(props.getProperty(key, "30").trim());
				System.out.println("Write stats every " + interval + " seconds");
			} else if (key.equals("collectSystemSpaces")) {
				collectSystemSpaces = Boolean.parseBoolean(props.getProperty(key, "false").trim());
				if (collectSystemSpaces) {
					System.out.println("Collect stats for AS system spaces");
				}
			} else if (key.equals("reportFolder")) {
				reportFolder = props.getProperty(key, "").trim();
				if (0 == reportFolder.length()) {
					reportFolder = null;
				}
				System.out.println("Statistics report is in folder " + reportFolder);
			} else {
				System.out.println("ignore config property " + key);
			}
		}
	}

	/**
	 * Initialize AS browser with configured parameters, i.e., output folder for
	 * stat reports.
	 *
	 * @param browser
	 *            AS space browser to be initialized
	 */
	private static void initializeBrowser(SpaceStatsBrowser browser) {
		browser.setReportFolder(reportFolder);
		browser.setConnectTimeout(interval * 900); // 90% of polling interval
	}

	/**
	 * Gracefully shutdown the thread pool, close all JMX connections.
	 */
	private static void shutdown() {
		System.out.println("Shutting down ...");
		pool.shutdown();
		for (SpaceStatsBrowser browser : browserMap.values()) {
			browser.cleanup();
		}

		// wait until all threads complete
		try {
			if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
				System.out.println("Force shutdown after 30 seconds ...");
				pool.shutdownNow();
				if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
					System.out.println("Terminate the process.");
					Thread.currentThread().interrupt();
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * print out usage info for the monitoring commands
	 */
	public static void printUsage() {
		System.out.println(getVersions());
		System.out.println("Collect AS space stats");
		System.out.println("ASMonitor Usage:");
		System.out.println("java com.tibco.metrics.asmonitor.ASMonitor -config <configFile>");
	}

	/**
	 * return version banner of this monitoring tool
	 *
	 * @return
	 */
	public static String getVersions() {
		String banner = "\n\t *********************************************************************************"
				+ "\n\t AS Metrics for TIBCO ActiveSpaces 2.x (2015-07-24)"
				+ "\n\t Copyright 2015 TIBCO Software Inc.  " + "\n\t All rights reserved."
				+ "\n\t *********************************************************************************\n";
		return banner;
	}

	/**
	 * Configured rules for filtering out entities that are not wanted in the
	 * stat report.
	 *
	 * @param spaceName
	 *            name of a space to be evaluated for stat reporting.
	 * @param memberName
	 *            name of a member to be evaluated.
	 * @return true if the entity is filtered out, and thus not tracked, false
	 *         otherwise.
	 */
	public static boolean isIgnoredStat(String spaceName, String memberName) {
		if ((!collectSystemSpaces) && spaceName.startsWith("$")) {
			return true;
		}

		if (matchesAnyPattern(spaceName, excludeMap.get("space"))) {
			return true; // space excluded
		}

		if (matchesAnyPattern(memberName, excludeMap.get("member"))) {
			return true; // member excluded
		}

		Set<String> includedSpaces = includeMap.get("space");
		if (includedSpaces != null && includedSpaces.size() > 0 && !matchesAnyPattern(spaceName, includedSpaces)) {
			return true; // space not included
		}

		Set<String> includedMembers = includeMap.get("member");
		if (includedMembers != null && includedMembers.size() > 0 && !matchesAnyPattern(memberName, includedMembers)) {
			return true; // member not included
		}

		return false;
	}

	private static boolean matchesAnyPattern(String source, Set<String> patterns) {
		if (patterns != null && patterns.size() > 0) {
			for (String pattern : patterns) {
				if (source.matches(pattern)) {
					return true;
				}
			}
		}
		return false;
	}
}
