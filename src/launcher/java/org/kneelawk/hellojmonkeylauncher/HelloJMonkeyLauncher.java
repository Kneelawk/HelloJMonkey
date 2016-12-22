package org.kneelawk.hellojmonkeylauncher;

import java.io.IOException;

public class HelloJMonkeyLauncher {
	public static void main(String[] args) {
		CPControl3 cp = new CPControl3("org.kneelawk.hellojmonkey.HelloJMonkey");

		cp.addExtractingFromFileLibrary(CPControl3.ME)
				.addLibrary("application", new CPControl3.DirectoryEntryFilter("app"), CPControl3.ALWAYS_DELETE)
				.addLibrary("libraries", new CPControl3.DirectoryEntryFilter("libs"), CPControl3.ALWAYS_DELETE);

		try {
			cp.launch(args);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
