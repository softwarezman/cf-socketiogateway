package com.sra.socketiogateway;

import static org.jboss.netty.channel.Channels.*;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.corundumstudio.socketio.AckManager;
import com.corundumstudio.socketio.AuthorizeHandler;
import com.corundumstudio.socketio.CompositeIterable;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.Disconnectable;
import com.corundumstudio.socketio.HeartbeatHandler;
import com.corundumstudio.socketio.PacketHandler;
import com.corundumstudio.socketio.PacketListener;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOEncoder;
import com.corundumstudio.socketio.namespace.NamespacesHub;
import com.corundumstudio.socketio.parser.Decoder;
import com.corundumstudio.socketio.parser.Encoder;
import com.corundumstudio.socketio.parser.JsonSupport;
import com.corundumstudio.socketio.scheduler.CancelableScheduler;
import com.corundumstudio.socketio.transport.BaseClient;
import com.corundumstudio.socketio.transport.WebSocketTransport;
import com.corundumstudio.socketio.transport.XHRPollingTransport;

public class SocketIOSSLPipelineFactory implements ChannelPipelineFactory, Disconnectable {
	protected static final String SOCKETIO_ENCODER = "socketioEncoder";
    protected static final String WEB_SOCKET_TRANSPORT = "webSocketTransport";
    protected static final String XHR_POLLING_TRANSPORT = "xhrPollingTransport";
    protected static final String AUTHORIZE_HANDLER = "authorizeHandler";
    protected static final String PACKET_HANDLER = "packetHandler";
    protected static final String HTTP_ENCODER = "encoder";
    protected static final String HTTP_AGGREGATOR = "aggregator";
    protected static final String HTTP_REQUEST_DECODER = "decoder";
    protected static final String SSL_HANDLER = "ssl";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final int protocol = 1;

    private AckManager ackManager;

    private AuthorizeHandler authorizeHandler;
    private XHRPollingTransport xhrPollingTransport;
    private WebSocketTransport webSocketTransport;
    private SocketIOEncoder socketIOEncoder;

    private CancelableScheduler scheduler;

    private PacketHandler packetHandler;
    private HeartbeatHandler heartbeatHandler;
    
    public void start(Configuration configuration, NamespacesHub namespacesHub) {
        scheduler = new CancelableScheduler(configuration.getHeartbeatThreadPoolSize());

        JsonSupport jsonSupport = configuration.getJsonSupport();
        Encoder encoder = new Encoder(jsonSupport);
        Decoder decoder = new Decoder(jsonSupport);

        ackManager = new AckManager(scheduler);
        heartbeatHandler = new HeartbeatHandler(configuration, scheduler);
        PacketListener packetListener = new PacketListener(heartbeatHandler, ackManager, namespacesHub);

        String connectPath = configuration.getContext() + "/" + protocol + "/";

        packetHandler = new PacketHandler(packetListener, decoder, namespacesHub);
        authorizeHandler = new AuthorizeHandler(connectPath, scheduler, configuration, namespacesHub);
        xhrPollingTransport = new XHRPollingTransport(connectPath, ackManager, this, scheduler, authorizeHandler, configuration);
        webSocketTransport = new WebSocketTransport(connectPath, ackManager, this, authorizeHandler, heartbeatHandler);
        socketIOEncoder = new SocketIOEncoder(encoder);
    }

    public Iterable<SocketIOClient> getAllClients() {
        Iterable<SocketIOClient> xhrClients = xhrPollingTransport.getAllClients();
        Iterable<SocketIOClient> webSocketClients = webSocketTransport.getAllClients();
        return new CompositeIterable<SocketIOClient>(xhrClients, webSocketClients);
    }
    
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();
        
        
        SSLEngine engine = SocketIOSslServerSslContext.getInstance().getServerContext().createSSLEngine();
        engine.setUseClientMode(false);
        
        pipeline.addLast(SSL_HANDLER, new SslHandler(engine));
        
        pipeline.addLast(HTTP_REQUEST_DECODER, new HttpRequestDecoder());
        pipeline.addLast(HTTP_AGGREGATOR, new HttpChunkAggregator(65536));
        pipeline.addLast(HTTP_ENCODER, new HttpResponseEncoder());

        pipeline.addLast(PACKET_HANDLER, packetHandler);

        pipeline.addLast(AUTHORIZE_HANDLER, authorizeHandler);
        pipeline.addLast(XHR_POLLING_TRANSPORT, xhrPollingTransport);
        pipeline.addLast(WEB_SOCKET_TRANSPORT, webSocketTransport);

        pipeline.addLast(SOCKETIO_ENCODER, socketIOEncoder);

        return pipeline;
    }
    
    public void onDisconnect(BaseClient client) {
        log.debug("Client with sessionId: {} disconnected", client.getSessionId());
        heartbeatHandler.onDisconnect(client);
        ackManager.onDisconnect(client);
        xhrPollingTransport.onDisconnect(client);
        webSocketTransport.onDisconnect(client);
        authorizeHandler.onDisconnect(client);
    }

    public void stop() {
        scheduler.shutdown();
    }
}
