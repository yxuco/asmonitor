package com.tibco.cts;

import java.util.Collection;

import com.tibco.as.space.FieldDef;
import com.tibco.as.space.MemberDef;
import com.tibco.as.space.Metaspace;
import com.tibco.as.space.Space;
import com.tibco.as.space.SpaceDef;
import com.tibco.as.space.Tuple;
import com.tibco.as.space.browser.Browser;
import com.tibco.as.space.browser.BrowserDef;

/**
 * Hello world!
 *
 */
public class App {
	public static void main(String[] args) throws Exception {
		String memberName = "default_mon";
		String discovery = "tibpgm";
		String metaspaceName = "ms";
		boolean collectSystemSpaces = false;

		MemberDef memberDef = com.tibco.as.space.MemberDef.create(memberName,
				discovery, null);
		Metaspace ms = Metaspace.connect(metaspaceName, memberDef);

		Space statsSpace = ms.getSpace("$space_stats");
		SpaceDef spaceDef = ms.getSpaceDef(statsSpace.getName());
		Collection<FieldDef> fieldDefs = spaceDef.getFieldDefs();
		FieldDef[] fields = new FieldDef[fieldDefs.size()];
		fieldDefs.toArray(fields);

		String[] fieldNames = new String[fields.length];
		StringBuffer hBuf = new StringBuffer();
		for (int i = 0; i < fields.length; i++) {
			fieldNames[i] = fields[i].getName();
			hBuf.append(fieldNames[i]).append(',');
		}
		System.out.println(hBuf.toString());

		BrowserDef browserBD = BrowserDef.create();
		browserBD.setTimeout(BrowserDef.NO_WAIT);
		browserBD.setTimeScope(BrowserDef.TimeScope.SNAPSHOT);
		browserBD.setPrefetch(BrowserDef.PREFETCH_ALL);
		Browser browser = statsSpace.browse(BrowserDef.BrowserType.GET,
				browserBD, null);
		Tuple t = browser.next();
		while (t != null) {
			if (!collectSystemSpaces) {
				String spaceName = t.getString("space_name");
				if (null == spaceName || spaceName.startsWith("$")) {
					t = browser.next();
					continue;
				}
			}

			StringBuffer vBuf = new StringBuffer();
			for (int i = 0; i < fieldNames.length; i++) {
				vBuf.append(t.get(fieldNames[i])).append(',');
			}
			System.out.println(vBuf.toString());
			t = browser.next();
		}
		browser.stop();
		statsSpace.close();
	}
}
