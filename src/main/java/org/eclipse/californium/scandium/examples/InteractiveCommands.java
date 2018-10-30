package org.eclipse.californium.scandium.examples;

import java.io.PrintWriter;

import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jline.console.ConsoleReader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Interactive commands for the DTLS server CLI 
 */
public class InteractiveCommands {

	private static final Logger LOG = LoggerFactory.getLogger(InteractiveCommands.class);

	@Command(name = "", description = "", footer = { "Press Ctl-D to exit." }, subcommands = { SendCommand.class, ClearConnectionCommand.class, ForceResumeCommand.class })
	static class CliCommands implements Runnable {

		final ConsoleReader reader;
		final PrintWriter out;
		final DtlsServerCLI server;

		CliCommands(ConsoleReader reader, DtlsServerCLI server) {
			this.reader = reader;
			out = new PrintWriter(reader.getOutput());
			this.server = server;
		}

		public void run() {
			out.println(new CommandLine(this).getUsageMessage());
		}
	}

	/**
	 * A command to send data.
	 */
	@Command(name = "send", description = "Send APPLICATION DATA to foreign peer")
	static class SendCommand implements Runnable {

		@Parameters(description="Data to send to the foreign peer.")
		private String data;

		@ParentCommand
		CliCommands parent;

		public void run() {
			if (parent.server.lastPeerAddress != null) {
				parent.server.dtlsConnector.send(RawData.outbound(data.getBytes(),
						new AddressEndpointContext(parent.server.lastPeerAddress), parent.server.createMessageCallback(), false));

				String dataInHexa = StringUtil.byteArray2HexString(data.getBytes(), StringUtil.NO_SEPARATOR, 0);
				LOG.info("APPLICATION DATA sending to {}: {} (0x{})...", parent.server.lastPeerAddress, data, dataInHexa);
			} else {
				LOG.info("Foreign peer (Client) should send APPLICATION DATA before");
			}
		}
	}
	
	/**
	 * A command to send data.
	 */
	@Command(name = "clear", description = "Clear all DTLS connection.")
	static class ClearConnectionCommand implements Runnable {

		@ParentCommand
		CliCommands parent;

		public void run() {
			parent.server.dtlsConnector.clearConnectionState();
			LOG.info("All DTLS connection cleared.");
		}
	}

	/**
	 * A command to send data.
	 */
	@Command(name = "forceResume", description = "Force session resumption when we will sent next DATA")
	static class ForceResumeCommand implements Runnable {

		@ParentCommand
		CliCommands parent;

		public void run() {
			parent.server.dtlsConnector.forceResumeAllSessions();
			LOG.info("An abbreviated handshake will be try before we send next data.");
		}
	}
}
