package org.eclipse.californium.scandium.examples;

import java.io.IOException;

import ch.qos.logback.core.ConsoleAppender;
import jline.console.ConsoleReader;

/**
 * A logback Console appender compatible with a Jline 2 Console reader.
 */
public class TerminalAppender<E> extends ConsoleAppender<E> {

	private ConsoleReader console;

	@Override
	protected void subAppend(E event) {
		if (console == null)
			super.subAppend(event);
		else {
			// Erase prompt
			try {
				console.resetPromptLine("", "", 0);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Display logs
			super.subAppend(event);
			
			// Display prompt again
			try {
				console.resetPromptLine("> ", "", 0);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public void setConsole(ConsoleReader console) {
		this.console = console;
	}
}
