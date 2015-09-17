package com.tibco.metrics.asmonitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;

import com.tibco.as.space.ASException;
import com.tibco.as.space.FieldDef;
import com.tibco.as.space.MemberDef;
import com.tibco.as.space.Metaspace;
import com.tibco.as.space.Space;
import com.tibco.as.space.SpaceDef;
import com.tibco.as.space.Tuple;
import com.tibco.as.space.browser.Browser;
import com.tibco.as.space.browser.BrowserDef;

public class SpaceStatsBrowser {
	private String memberName;
	private String discovery;
	private String metaspaceName;

	private FileWriter writer = null;
	private String statFilename = null;

	private String reportFolder = null;
	private String timestamp;
	private long connectTimeout = 20000;

	Metaspace ms;
	Space statsSpace;
	String[] fieldNames = null;

	/**
	 * construct a browser for $space_stats
	 *
	 * @param metaspaceName
	 *            the metaspace to browse
	 * @param discovery
	 *            discovery URL of the metaspace
	 * @param memberName
	 *            name of the browser client
	 */
	public SpaceStatsBrowser(String metaspaceName, String discovery, String memberName) {
		this.metaspaceName = metaspaceName;
		this.discovery = discovery;
		this.memberName = memberName;
	}

	private void connectAS() throws ASException {
		System.out.println(String.format("Connecting to %s @ %s", metaspaceName, discovery));
		MemberDef memberDef = MemberDef.create(memberName, discovery, null);
		memberDef.setConnectTimeout(connectTimeout);
		ms = Metaspace.connect(metaspaceName, memberDef);
		statsSpace = ms.getSpace("$space_stats");

		// initialize report field names to include all field names in the
		// $space_stats
		if (null == fieldNames) {
			SpaceDef spaceDef = ms.getSpaceDef(statsSpace.getName());
			Collection<FieldDef> fieldDefs = spaceDef.getFieldDefs();
			FieldDef[] fields = new FieldDef[fieldDefs.size()];
			fieldDefs.toArray(fields);

			fieldNames = new String[fields.length];
			for (int i = 0; i < fields.length; i++) {
				fieldNames[i] = fields[i].getName();
			}
		}
	}

	/**
	 * Set folder name for stat log files
	 *
	 * @param reportFolder
	 *            name of the folder to write stats
	 */
	public void setReportFolder(String reportFolder) {
		this.reportFolder = reportFolder;
	}

	public void setConnectTimeout(long timeout) {
		this.connectTimeout = timeout;
	}

	/**
	 * Find or create a writer for AS stat file
	 *
	 * @return
	 * @throws IOException
	 *             when failed to create stat file
	 */
	private FileWriter getWriter() throws IOException {
		if (writer != null) {
			if (statFilename().equals(statFilename)) {
				return writer;
			} else {
				// start a new day, so close the old writer
				closeWriter();
			}
		}

		// return a new file writer
		return createWriter();
	}

	public void closeWriter() {
		if (writer != null) {
			try {
				System.out.println(
						String.format("Close writer for metaspace %s on discovery %s", metaspaceName, discovery));
				writer.close();
			} catch (IOException io) {
				// do nothing
			}
		}
		writer = null;
		statFilename = null;
	}

	/**
	 * Create a file and pre-configured report folder for writing AS stats
	 *
	 * @return file writer to append text data
	 * @throws IOException
	 */
	private FileWriter createWriter() throws IOException {
		File folder = null;
		if (reportFolder != null) {
			folder = new File(reportFolder);
			if (!folder.exists()) {
				boolean ok = folder.mkdirs();
				if (!ok) {
					throw new IOException("Failed to create directory " + reportFolder);
				}
			}
		}

		statFilename = statFilename();
		File statFile = new File(folder, statFilename);
		boolean isNew = !statFile.exists();
		writer = new FileWriter(statFile, true); // file for append
		if (isNew) {
			// write header as the first line of new file
			writer.write(getHeader());
		}

		return writer;
	}

	/**
	 * construct file name with date suffix, i.e., metaspace_member_MM_dd.csv
	 *
	 * @return
	 */
	private String statFilename() {
		Calendar cal = Calendar.getInstance();
		return String.format("%s_%s_%3$tm_%3$td.csv", metaspaceName, memberName, cal);
	}

	private String getHeader() {
		if (null == fieldNames) {
			return "";
		}
		StringBuffer hBuf = new StringBuffer();
		hBuf.append("DateTime");
		for (int i = 0; i < fieldNames.length; i++) {
			hBuf.append(',').append(fieldNames[i]);
		}
		hBuf.append('\n');
		return hBuf.toString();
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Collect AS stats, and write them to stat log file. can be called by
	 * separate worker threads.
	 *
	 * @throws IOException
	 *             when failed to write stat file
	 */
	public void collectMetrics() {
		if (null == timestamp) {
			// should not be here, just in case
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			timestamp = fmt.format(Calendar.getInstance().getTime());
		}
		try {
			writeMetrics(timestamp);
		} catch (IOException e) {
			// close old file and try again with new writer
			System.out
					.println(String.format("Failed to write stats of metaspace %s: %s", metaspaceName, e.getMessage()));
			closeWriter();
			try {
				writeMetrics(timestamp);
			} catch (IOException io) {
				System.out.println(
						String.format("Exception while writing stat of %s: %s", metaspaceName, io.getMessage()));
				closeWriter();
			}
		} finally {
			timestamp = null;
		}
	}

	/**
	 * Browse $space_stats, and write data to pre-configured log file
	 *
	 * @throws IOException
	 *             when failed to write stat data to file
	 */
	public void writeMetrics(String timestamp) throws IOException {
		FileWriter writer = getWriter();
		try {
			if (null == ms || null == statsSpace) {
				// try to reconnect to AS
				connectAS();
			}
			BrowserDef bDef = BrowserDef.create();
			bDef.setTimeout(BrowserDef.NO_WAIT);
			bDef.setTimeScope(BrowserDef.TimeScope.SNAPSHOT);
			bDef.setPrefetch(BrowserDef.PREFETCH_ALL);
			Browser browser = statsSpace.browse(BrowserDef.BrowserType.GET, bDef, null);
			Tuple t = browser.next();
			while (t != null) {
				if (!ASMonitor.isIgnoredStat(t.getString("space_name"), t.getString("member_name"))) {
					StringBuffer vBuf = new StringBuffer();
					vBuf.append(timestamp);
					for (int i = 0; i < fieldNames.length; i++) {
						vBuf.append(',').append(t.get(fieldNames[i]));
					}
					vBuf.append('\n');
					writer.write(vBuf.toString());
				}
				t = browser.next();
			}
			browser.stop();
		} catch (ASException ex) {
			System.out.println(
					String.format("Failed to get stats for metaspace %s: %s\n", metaspaceName, ex.getMessage()));

			// disconnect from AS, so the next round will try to reconnect
			disconnectAS();
		}
		writer.flush();

		// throw exception if file becomes stale, so the writer is closed and
		// re-created
		checkFile();
	}

	/**
	 * Check if the file still exists and is writable. Linux allows the system
	 * to continue to write even if file is deleted. So, this check will throw
	 * exception, which will lead to closing and re-creating the file writer.
	 *
	 * @throws IOException
	 *             when the AS stat file no longer exist or not writable.
	 */
	private void checkFile() throws IOException {
		File statFile = new File(reportFolder, statFilename());
		if (!statFile.exists() || !statFile.canWrite()) {
			throw new IOException(String.format("File %s no longer exist", statFile.getAbsolutePath()));
		}
	}

	private void disconnectAS() {
		try {
			if (statsSpace != null) {
				statsSpace.close();
			}
			if (ms != null) {
				ms.closeAll();
			}
		} catch (ASException e) {
			// do nothing
		}
		statsSpace = null;
		ms = null;
	}

	/**
	 * Close AS connection, and close log file writers
	 *
	 * @throws IOException
	 */
	public void cleanup() {
		disconnectAS();
		if (writer != null) {
			try {
				writer.close();
				writer = null;
				statFilename = null;
			} catch (IOException e) {
				// do nothing
			}
		}
	}

}
