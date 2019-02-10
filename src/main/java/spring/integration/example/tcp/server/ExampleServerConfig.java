/**
 * 
 */
package spring.integration.example.tcp.server;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Splitter;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.tcp.TcpSendingMessageHandler;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Tarl Hahn
 * Feb 10, 2019
 *
 */
@EnableIntegration
@IntegrationComponentScan
@Configuration
@EnableScheduling
public class ExampleServerConfig {

	private final Message<?> heartbeatMessage = MessageBuilder.withPayload("heartbeat").build();

	public static final int DEFAULT_RETRY_INTERVAL = 1000;
	public static final String OUTBOUND_CHANNEL = "tcp.out";
	public static final String INBOUND_CHANNEL = "tcp.in";
	public static final String BROADCAST_CHANNEL = "broadcast";
	
	public static final String INBOUND_POLL_RATE = "5";
	public static final String INBOUND_POLL_POLE_SIZE = "1";
	public static final String HEARTBEAT_POLL_DELAY = "1000";
	
	@Autowired
	public AbstractServerConnectionFactory factory;
	
	@InboundChannelAdapter(channel = BROADCAST_CHANNEL, poller = @Poller(fixedDelay = HEARTBEAT_POLL_DELAY))
    public Message<?> heartbeat() {
        return this.heartbeatMessage;
    }
		
	// Incoming message queue
	@Bean(name=INBOUND_CHANNEL)
	public MessageChannel inputChannel() {
		return new QueueChannel();
	}
	
	// Configure external message receiving adapter
	@Bean
	public TcpReceivingChannelAdapter inboundChannel(AbstractServerConnectionFactory factory) {
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(factory);
		adapter.setOutputChannel(inputChannel());
		return adapter;
	}

	// Configure external message sending handler
	@Bean
	@ServiceActivator(inputChannel = OUTBOUND_CHANNEL)	// sets the sending message handler's "inputChannel" to OUTBOUND_CHANNEL. 
	public TcpSendingMessageHandler outboundChannel(AbstractServerConnectionFactory factory) {
	    TcpSendingMessageHandler outbound = new TcpSendingMessageHandler();
	    outbound.setConnectionFactory(factory);
	    outbound.setRetryInterval(TimeUnit.SECONDS.toMillis(DEFAULT_RETRY_INTERVAL));
	    return outbound;
	}

	// setup external poller that echoes messages back
	@Transformer(inputChannel=INBOUND_CHANNEL, outputChannel=OUTBOUND_CHANNEL, poller=@Poller(fixedRate=INBOUND_POLL_RATE, maxMessagesPerPoll=INBOUND_POLL_POLE_SIZE))
	public String echoTransform(byte[] payload) {
		return new String(payload);
	}

	// setup a "broadcast splitter to broadcast messages to all open connections.
	@Splitter(inputChannel = BROADCAST_CHANNEL, outputChannel = OUTBOUND_CHANNEL)
	public List<Message<?>> distributeMessage( Message<?> message ) {
	    return factory.getOpenConnectionIds()
	    		.stream()
	    		.map(s -> MessageBuilder.fromMessage(message)
	    				.setHeader(IpHeaders.CONNECTION_ID, s)
	    				.build())
	    		.collect(Collectors.toList());
	}

	// Gateway interface for sending messages programmatically
    @MessagingGateway
    public interface BroadcastGateway {
    	@Gateway(requestChannel=BROADCAST_CHANNEL, replyTimeout=0)
        String broadcast(String msg);
    	@Gateway(requestChannel=OUTBOUND_CHANNEL, replyTimeout=0)
    	String send(@Header(IpHeaders.CONNECTION_ID)String id, String message);
    }
}
