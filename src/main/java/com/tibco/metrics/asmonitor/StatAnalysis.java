package com.tibco.metrics.asmonitor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Scanner;
import java.util.TreeMap;

public class StatAnalysis {
	
	public static void main(String... args) throws Exception {
		calculateSummary(args[0]);
	}
	
	@SuppressWarnings("resource")
	public static void calculateSummary(String statFile) throws Exception {
		HashMap<String, Integer> columnMap = new HashMap<String, Integer>();
		TreeMap<String, HashMap<String, Integer>> stats = new TreeMap<String, HashMap<String, Integer>>();
		String countPattern = "original_count|replica_count|put_count|take_count|client_put_count|client_take_count";
		String[] columns = countPattern.split("\\|");
		
		Path path = Paths.get(statFile);
		Scanner scanner = new Scanner(path);
		int timeCol = -1;
		int spaceCol = -1;

		System.out.println("DateTime,space_name," + countPattern.replaceAll("\\|", ","));
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			String[] fields = line.split(",");
			if (timeCol < 0) {
				for (int i = 0; i < fields.length; i++) {
					if ("DateTime".equals(fields[i])) {
						timeCol = i;
					}
					else if ("space_name".equals(fields[i])) {
						spaceCol = i;
					}
					else if (fields[i].matches(countPattern)) {
						columnMap.put(fields[i], i);
					}
				}
			}
			else {
				// add to cumulated counts of a space
				String key = fields[timeCol] + "," + fields[spaceCol];
				HashMap<String, Integer> spaceCount = stats.get(key);
				if (null == spaceCount) {
					spaceCount = new HashMap<String, Integer>();
					stats.put(key, spaceCount);
				}
				for (String col : columnMap.keySet()) {
					Integer fieldCount = spaceCount.get(col);
					if (null == fieldCount) fieldCount = 0;
					spaceCount.put(col, fieldCount + Integer.valueOf(fields[columnMap.get(col)]));
				}
			}
		}
		
		for (String key : stats.keySet()) {
			StringBuffer buff = new StringBuffer();
			buff.append(key);
			HashMap<String, Integer> spaceCount = stats.get(key);
			for (String col : columns) {
				buff.append(',').append(spaceCount.get(col));
			}
			System.out.println(buff.toString());
		}
	}
}
