package spring.integration.example.tcp.server;

import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNetServerConnectionFactory;
import org.springframework.integration.ip.util.TestingUtilities;

import spring.integration.example.tcp.server.ExampleServerConfig.BroadcastGateway;

/**
 * @author Tarl Hahn
 * Feb 10, 2019
 * 
 */
@SpringBootApplication
public class Main implements CommandLineRunner {

	private static final long WAIT_FOR_START = 10000;
	private static final int SERVER_LISTENER_PORT = 1234;
	
	private static Logger LOG = LoggerFactory.getLogger(Main.class);
	
	@Autowired
	private AbstractServerConnectionFactory server;

	@Autowired
	public BroadcastGateway broadcast;
	
	public static void main(String... args) {
		LOG.info("Main entered");
		SpringApplication.run(Main.class, args);
		LOG.info("Main exiting");
	}
	
	@Override
	public void run(String... args) throws Exception {
		LOG.info("Running application...");

		StartServer(server, WAIT_FOR_START);

		ShowInstructions(server.getPort());
		
		blockingBroadcastUserInputLoop(broadcast);

		LOG.info("Exiting application...");
		System.exit(0);
	}
	
	// waits for user input before broadcasting to all open connections.
	public static void blockingBroadcastUserInputLoop(BroadcastGateway gateway) throws Exception {
		final Scanner scanner = new Scanner(System.in);
		while (true) {
			final String input = scanner.nextLine();

			if ("q".equals(input.trim())) {
				break;
			} else {
				// broadcast to all open connections;
				gateway.broadcast(input);
			}
		}
		scanner.close();
	}
	
	// shows interactive usage.
	public static void ShowInstructions(int port) {
		System.out.print("\n\nThe echo server is listening on port '" + port + "'\n"
				+ "\tTelnet or Putty may be used to establish a connection.\n"
				+ "\tAll messages will be capitalized and echoed.\n"
				+ "\tOpen connections receive a \"heartbeat\" message every " + "1000" + " seconds.\n"
				+ "\nTo broadcast a message to all open connections.\n"
				+ "\tPlease enter some text and press <enter>:\n\n");
	}

	// start the server with delayed execution until started and listening.
	public static void StartServer(AbstractServerConnectionFactory factory, long delay) {
		LOG.info("Starting server");
		TestingUtilities.waitListening(factory, delay);
		LOG.info("Server started and listening");
	}	
	
	// prepare Server
	@Bean
	public AbstractServerConnectionFactory factory() {
		return new TcpNetServerConnectionFactory(SERVER_LISTENER_PORT);
	}
}