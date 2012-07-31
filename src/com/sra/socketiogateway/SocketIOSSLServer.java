package com.sra.socketiogateway;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.ClientOperations;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.listener.ClientListeners;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.namespace.NamespacesHub;


public class SocketIOSSLServer implements ClientListeners {
	private final Logger log = LoggerFactory.getLogger(getClass());

    private ServerBootstrap bootstrap;

    private final NamespacesHub namespacesHub = new NamespacesHub();
    private final SocketIONamespace mainNamespace;

    private SocketIOSSLPipelineFactory pipelineFactory = new SocketIOSSLPipelineFactory();
    
    private Channel mainChannel;
    private Configuration config;
    private boolean started;
    
    public SocketIOSSLServer(Configuration configuration) {
        this.config = new Configuration(configuration);

        mainNamespace = addNamespace(Namespace.DEFAULT_NAME);
    }
    
    public void setPipelineFactory(SocketIOSSLPipelineFactory pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }
    
    public ClientOperations getBroadcastOperations() {
        return new BroadcastOperations(pipelineFactory.getAllClients());
    }

    public void start() {
        ChannelFactory factory = new NioServerSocketChannelFactory(config.getBossExecutor(), config.getWorkerExecutor());
        bootstrap = new ServerBootstrap(factory);

        pipelineFactory.start(config, namespacesHub);

        bootstrap.setPipelineFactory(pipelineFactory);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        mainChannel = bootstrap.bind(new InetSocketAddress(config.getHostname(), config.getPort()));

        started = true;
        log.info("SocketIO server started at port: {}", config.getPort());
    }
    
    public void stop() {
        pipelineFactory.stop();
        mainChannel.close();
        bootstrap.releaseExternalResources();
        started = false;
    }

    public SocketIONamespace addNamespace(String name) {
        return namespacesHub.create(name);
    }

    public SocketIONamespace getNamespace(String name) {
        return namespacesHub.get(name);
    }

    public void removeNamespace(String name) {
        namespacesHub.remove(name);
    }

    @Override
    public void addEventListener(String eventName, DataListener<Object> listener) {
        mainNamespace.addEventListener(eventName, listener);
    }

    @Override
    public void addJsonObjectListener(DataListener<Object> listener) {
        mainNamespace.addJsonObjectListener(listener);
    }

    @Override
    public void addDisconnectListener(DisconnectListener listener) {
        mainNamespace.addDisconnectListener(listener);
    }

    @Override
    public void addConnectListener(ConnectListener listener) {
        mainNamespace.addConnectListener(listener);
    }

    @Override
    public void addMessageListener(DataListener<String> listener) {
        mainNamespace.addMessageListener(listener);
    }
}
