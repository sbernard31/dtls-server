package org.eclipse.californium.scandium.examples;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.MessageCallback;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.HandshakeException;
import org.eclipse.californium.scandium.dtls.Handshaker;
import org.eclipse.californium.scandium.dtls.SessionListener;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.californium.scandium.examples.InteractiveCommands.CliCommands;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.Appender;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.shell.jline2.PicocliJLineCompleter;

@Command(sortOptions = false)
public class DtlsServerCLI implements Callable<DTLSConnector> {

	static {
		// Define a default logback.configurationFile
		String property = System.getProperty("logback.configurationFile");
		if (property == null) {
			System.setProperty("logback.configurationFile", "logback-config.xml");
		}
	}
	private static final Logger LOG = (Logger) LoggerFactory.getLogger(DtlsServerCLI.class);

	@Option(names = { "-h", "--help" }, description = "Show usage.", usageHelp = true)
	private boolean help;

	@Option(names = { "-p", "--port" }, description = "The server port. (default ${DEFAULT-VALUE})")
	private int port = 4433; // default port used by openssl

	@Option(names = { "-i", "--pskid" }, description = "The psk identity. (default '${DEFAULT-VALUE}')")
	private String pskId = "Client_identity";

	@Option(names = { "-k",
			"--pskkey" }, description = "The psk secret key in hexa. (default '${DEFAULT-VALUE}' means 'secretKey')")
	private String pskKey = "73656372657450534b";

	@Option(names = { "-n",
			"--noRetransmission" }, description = "Disable DTLS retransmission. It could be usefull for debugging. (Currently disabling retransmission is not possible so we delay it to 1 hour ...)")
	private boolean noRetransmission = false;

	@Option(names = { "-x",
			"--exchangeRole" }, description = "Use to test DTLS exchange role. Server wait until it receive an application data, after it acts as a client and try to initiate an handshake with the foreign peer, then send to it a ACK again.")
	private boolean exchangeRole = false;

	public DTLSConnector dtlsConnector;
	public InetSocketAddress lastPeerAddress;
	public ConsoleReader console;

	public static void main(String[] args) {
		try {
			// Parse command line
			DtlsServerCLI dtlsServer = new DtlsServerCLI();
			DTLSConnector connector = CommandLine.call(dtlsServer, args);

			// Invalid Argument, so we stop the application
			if (connector == null)
				return;

			// Create Interactive Shell
			dtlsServer.console = new ConsoleReader();
			dtlsServer.console.setPrompt("> ");

			// Configure Terminal appender if it is present.
			Appender<?> appender = ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
					.getAppender("TERMINAL");
			if (appender instanceof TerminalAppender<?>) {
				((TerminalAppender<?>) appender).setConsole(dtlsServer.console);
			}

			// set up the completion
			CliCommands commands = new CliCommands(dtlsServer.console, dtlsServer);
			CommandLine cmd = new CommandLine(commands);
			dtlsServer.console.addCompleter(new PicocliJLineCompleter(cmd.getCommandSpec()));

			// start the shell and process input until the user quits with Ctl-D
			String line;
			while ((line = dtlsServer.console.readLine()) != null) {
				ArgumentList list = new WhitespaceArgumentDelimiter().delimit(line, line.length());
				CommandLine.run(commands, list.getArguments());

				// Erase prompt if already here, as realLine will prompt
				// again.
				dtlsServer.console.resetPromptLine("", "", 0);
				dtlsServer.console.setPrompt("> ");
			}
		} catch (Throwable t) {
			System.err.println(t.getMessage());
		}
	}

	public DTLSConnector call() throws Exception {

		// Create DTLS connector
		DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
		builder.setAddress(new InetSocketAddress(port));
		builder.setPskStore(new StaticPskStore(pskId, StringUtil.hex2ByteArray(pskKey)));
		if (noRetransmission) {
			// TODO there is no way to remove retransmission with scandium, so I
			// set it to 60000ms (1h) which should be enough to debug...
			builder.setRetransmissionTimeout(60000);
		}
		dtlsConnector = new DTLSConnector(builder.build()) {

			@Override
			protected void onInitializeHandshaker(Handshaker handshaker) {
				handshaker.addSessionListener(new SessionListener() {

					public void sessionEstablished(Handshaker handshaker, DTLSSession establishedSession)
							throws HandshakeException {
						LOG.info("Session established with {}.", handshaker.getPeerAddress());
					}

					public void handshakeStarted(Handshaker handshaker) throws HandshakeException {
						LOG.info("Handshake started with {}...", handshaker.getPeerAddress());
					}

					public void handshakeFlightRetransmitted(Handshaker handshaker, int flight) {
						LOG.info("Flight {} retransmitted ...", flight);
					}

					public void handshakeFailed(Handshaker handshaker, Throwable error) {
						LOG.info("Handshake Failed with {} !", handshaker.getPeerAddress(), error);
					}

					public void handshakeCompleted(Handshaker handshaker) {
						// LOG.info("Handshake to {} Complete.",
						// handshaker.getPeerAddress());
					}
				});
				super.onInitializeHandshaker(handshaker);
			}
		};

		// Start it
		dtlsConnector.setRawDataReceiver(new RawDataChannelImpl(dtlsConnector));
		dtlsConnector.start();

		LOG.info("DTLS Server started at {} ", dtlsConnector.getAddress().toString());
		LOG.info("DTLS PSK Identity: {} ", pskId);
		LOG.info("DTLS PSK Secret: {} ", pskKey);
		return dtlsConnector;
	}

	/**
	 * Class responsible to handle Application Data received. The default
	 * behavior is just to send a ACK each time an application DATA is received.
	 */
	private class RawDataChannelImpl implements RawDataChannel {

		private DTLSConnector connector;

		public RawDataChannelImpl(DTLSConnector con) {
			this.connector = con;
		}

		public void receiveData(final RawData raw) {
			lastPeerAddress = raw.getInetSocketAddress();
			String stringValue = new String(raw.bytes);
			String hexValue = StringUtil.byteArray2HexString(raw.getBytes(), StringUtil.NO_SEPARATOR, 0);

			LOG.info("APPLICATION DATA received from {}: '{}' (0x{})", lastPeerAddress, stringValue, hexValue);

			RawData response = RawData.outbound("ACK".getBytes(), new AddressEndpointContext(lastPeerAddress),
					createMessageCallback(), false);
			connector.send(response);
			LOG.info("APPLICATION DATA sending to {}: 'ACK' (0x41434B)...", lastPeerAddress);

			if (exchangeRole) {
				new Thread(new Runnable() {

					public void run() {
						// Wait a little bit...
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
						}

						// Clear dtls connection
						connector.clearConnectionState();

						LOG.info("Clear DTLS Connection to initiate a new handshake (act as client)");
						// Wait a little bit...
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}

						// Resent a ACK
						RawData response2 = RawData.outbound("ACK2".getBytes(),
								new AddressEndpointContext(lastPeerAddress), createMessageCallback(), false);
						connector.send(response2);
						LOG.info("APPLICATION DATA sending to {}: 'ACK2' (0x41434B32)...", lastPeerAddress);
					}
				}).start();
			}
		}
	}

	public MessageCallback createMessageCallback() {
		return new MessageCallback() {

			public void onSent() {
				LOG.info("APPLICATION DATA sent successfully.");
			}

			public void onError(Throwable error) {
				LOG.info("Unable to sent APPLICATION DATA.", error);
			}

			public void onDtlsRetransmission(int flight) {
			}

			public void onContextEstablished(EndpointContext context) {
			}

			public void onConnecting() {
			}
		};
	}
}
