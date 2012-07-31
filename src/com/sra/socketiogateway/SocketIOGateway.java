package com.sra.socketiogateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import coldfusion.eventgateway.CFEvent;
import coldfusion.eventgateway.Gateway;
import coldfusion.eventgateway.GatewayHelper;
import coldfusion.eventgateway.GatewayServices;
import coldfusion.eventgateway.Logger;
import coldfusion.runtime.Array;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;

//import static org.webbitserver.WebServers.createWebServer;

public class SocketIOGateway implements Gateway {

    // The handle to the CF gateway service
    protected GatewayServices gatewayService;

    // ID provided by EventService
    protected String gatewayID;

    // CFC Listeners for our events
    private ArrayList<String> cfcListeners = new ArrayList<String>();

    // The default function to pass our events to
    protected String cfcEntryPoint = "onIncomingMessage";

    // A collection of connected clients
    private Hashtable<String, SocketIOClient> connectionRegistry = new Hashtable<String, SocketIOClient>();

    // The thread that is running the SocketIO webserver
    protected Thread clientThread;

    // Current status
    protected int status = STOPPED;

    // A logger
    protected Logger log;

    // The SocketIO webserver
    protected SocketIOServer server = null;
    protected SocketIOSSLServer serverSSL = null;
    
    public Properties p = new Properties();

    // The SocketIO webserver defaults
    public static final int DEFAULT_PORT = 9092;
    
    // The SocketIO webserver defaults
    public static final String DEFAULT_HOST = "localhost";

    public static String CONFIG_PATH;
    
    // The SocketIO webserver settings
    protected int port = DEFAULT_PORT; 
    
    protected String host = DEFAULT_HOST;
    
    public static final Boolean ENABLE_LOGGING = false;
    
    protected Boolean logging = ENABLE_LOGGING;
    
    public static final String[] eventList = {"EVENT", "JSON", "MESSAGE"};
    
    private Boolean SSLEnabled = false; 
    
    public SocketIOGateway(String id, String configpath) {
    	//Set the config path so the SSLContect java can see it for parsing keystorePath
    	CONFIG_PATH = configpath;
    	
        gatewayID = id;
        gatewayService = GatewayServices.getGatewayServices();
        // log things to socketio-gateway.log in the CF log directory
        log = gatewayService.getLogger("socketio-gateway");        
        
        try {
            FileInputStream pFile = new FileInputStream(configpath);
            //Properties p = new Properties();
            p.load(pFile);
            pFile.close();
            if (p.containsKey("port"))
                port = Integer.parseInt(p.getProperty("port"));
            if (p.containsKey("host"))
                host = p.getProperty("host");
            if (p.containsKey("logging"))
            	logging = Boolean.parseBoolean( p.getProperty("logging"));
            if (p.containsKey("useSSL"))
            	SSLEnabled = true;
         } catch (IOException e) {
            // do nothing. use default value for port.
            log.error("SocketIOGateway(" + gatewayID
                    + ") Unable to read configuration file '" + configpath
                    + "': " + e.toString(), e);
        }
        
    }

    /* Gateway */    
    
    
    
    @Override
    public void start() {
        status = STARTING;
        try {
            Configuration config = new Configuration();
            config.setHostname(host);
            config.setPort(port);
            
            
            
            //config.setListener(handler);
            if(logging)
            	log.warn("Hostname: " + host + " | Port: " + port);
            
            if(SSLEnabled) {
            	if(logging) 
            		log.warn("Found ssl key. Going to create SSL server");
            	
            	serverSSL = new SocketIOSSLServer(config);
            	this.buildConnectListener(serverSSL);
                this.buildDisconnectListener(serverSSL);
                this.buildJSONListener(serverSSL);
                this.buildEventListeners(serverSSL);
                
                serverSSL.start();
            } else {
            	server = new SocketIOServer(config);
            	this.buildConnectListener(server);
                this.buildDisconnectListener(server);
                this.buildJSONListener(server);
                this.buildEventListeners(server);
                
                server.start();
            }
            
            
        } catch(Exception e) {
            log.error("SocketIOGateway(" + gatewayID
                    + ") Unable to start SocketIO webserver." 
                    + "': " + e.toString(), e);
        }
        status = RUNNING;
    }
    
    @Override
    public void stop() {
    	status = STOPPING;
        try {
        	if(SSLEnabled) {
        		serverSSL.stop();
        	} else {
        		server.stop();
        	}
        } catch(Exception e) {
            log.error("SocketIOGateway(" + gatewayID
                    + ") Unable to stop SocketIO webserver." 
                    + "': " + e.toString(), e);
        }
        status = STOPPED;
    }
    
    @Override
    public void restart() {
    	if(SSLEnabled) {
    		serverSSL.stop();
            serverSSL.start();
    	} else {
    		server.stop();
            server.start();
    	}
    }
    
    public void buildConnectListener(SocketIOServer server) {
    	server.addConnectListener(new ConnectListener() {
        	@Override
    	    public void onConnect(SocketIOClient conn) {
        		if(logging) 
        			log.warn("SocketIO Session A connection request was made with sessionID: "
                        + conn.getSessionId() + ".");
    	        // Get a key for the connection
    	        //String theKey = getUniqueKey(conn);
        		UUID theKey = conn.getSessionId();
    	        connectionRegistry.put(theKey.toString(), conn);
    	        
    	        for (String path : cfcListeners) {
    	            
    	            CFEvent event = new CFEvent(gatewayID);
    	            
    	            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
    	            mydata.put("CONN", conn);
    	            
    	            event.setData(mydata);
    	            event.setGatewayType("WebSocket");
    	            event.setOriginatorID(theKey.toString());
    	            event.setCfcMethod("onOpen");
    	            event.setCfcTimeOut(10);
    	            if (path != null) {
    	                event.setCfcPath(path);
    	            }
    	            
    	            boolean sent = gatewayService.addEvent(event);
    	            if (!sent) {
    	                log.error("SocketGateway("
    	                        + gatewayID
    	                        + ") Unable to put message on event queue. Message not sent from "
    	                        + gatewayID + ", thread " + theKey + ".");
    	            }
    	        }
    	        
    	    }
        });
    }
    
    public void buildDisconnectListener(SocketIOServer server) {
    	server.addDisconnectListener(new DisconnectListener() {
        	@Override
            public void onDisconnect(SocketIOClient conn) {
        		if(logging)
        			log.warn("SocketIO Session(" + conn.getSessionId()
                        + ") Disconnected from SocketIO webserver.");
                // Get a key for the connection
        		UUID theKey = conn.getSessionId();
                connectionRegistry.remove(theKey.toString());
                
                for (String path : cfcListeners) {
                    
                    CFEvent event = new CFEvent(gatewayID);
                    
                    Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                    mydata.put("CONN", conn);
                    
                    event.setData(mydata);
                    event.setGatewayType("WebSocket");
                    event.setOriginatorID(theKey.toString());
                    event.setCfcMethod("onClose");
                    event.setCfcTimeOut(10);
                    if (path != null) {
                        event.setCfcPath(path);
                    }
                    
                    boolean sent = gatewayService.addEvent(event);
                    if (!sent) {
                        log
                        .error("SocketGateway("
                                + gatewayID
                                + ") Unable to put message on event queue. Message not sent from "
                                + gatewayID + ", thread " + theKey + ".");
                    }
                }
                
            }            	
        });
    }
    
    public void buildMessageListener(SocketIOServer server) {
    	server.addMessageListener(new DataListener<String>() {
        	@Override
            public void onData(SocketIOClient conn, String message) {
        		if(logging)
        			log.warn("SocketIOGateway(" + gatewayID
                        + ") Received a message from: SessionId: " + conn.getSessionId());
                // Get a key for the connection
                //String theKey = getUniqueKey(conn);
        		UUID theKey = conn.getSessionId();
        		
                for (String path : cfcListeners) {
                    
                    CFEvent event = new CFEvent(gatewayID);
                    
                    Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                    mydata.put("MESSAGE", message);
                    mydata.put("CONN", conn);
                    
                    event.setData(mydata);
                    event.setGatewayType("WebSocket");
                    event.setOriginatorID(theKey.toString());
                    event.setCfcMethod(cfcEntryPoint+"MESSAGE");
                    event.setCfcTimeOut(10);
                    if (path != null) {
                        event.setCfcPath(path);
                    }
                    
                    boolean sent = gatewayService.addEvent(event);
                    if (!sent) {
                        log
                        .error("SocketGateway("
                                + gatewayID
                                + ") Unable to put message on event queue. Message not sent from "
                                + gatewayID + ", thread " + theKey
                                + ".  Message was " + message);
                    }
                }
            }
        });
    }
    
    public void buildJSONListener(SocketIOServer server) {
    	 server.addJsonObjectListener(new DataListener<Object>() {
         	@Override
             public void onData(SocketIOClient conn, Object data) {
         		if(logging) 
         			log.warn("SocketIOGateway(" + gatewayID
                         + ") Received a message from SessionId: " + conn.getSessionId());
                 // Get a key for the connection
                 //String theKey = getUniqueKey(conn);
         		UUID theKey = conn.getSessionId();
         		
                 for (String path : cfcListeners) {
                     
                     CFEvent event = new CFEvent(gatewayID);
                     
                     Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                     mydata.put("DATA", data);
                     mydata.put("CONN", conn);
                     
                     event.setData(mydata);
                     event.setGatewayType("WebSocket");
                     event.setOriginatorID(theKey.toString());
                     event.setCfcMethod(cfcEntryPoint+"JSON");
                     event.setCfcTimeOut(10);
                     if (path != null) {
                         event.setCfcPath(path);
                     }
                     
                     boolean sent = gatewayService.addEvent(event);
                     if (!sent) {
                         log
                         .error("SocketGateway("
                                 + gatewayID
                                 + ") Unable to put message on event queue. Message not sent from "
                                 + gatewayID + ", thread " + theKey
                                 + ".  Data was " + data);
                     }
                 }
             }
         });
    }
    
	public void buildEventListeners(SocketIOServer server) {
		try {
			String[] events = p.getProperty("eventlist").split(",");
			for(int i = 0; i < events.length; i++) {
				final String keyProperty = events[i];
				
				if(logging) 
        			log.warn("Adding non-SSL event listener: " + keyProperty);
        		
        		server.addEventListener(keyProperty, new DataListener<Object>() {
        			@Override
        			public void onData(SocketIOClient conn, Object data) {
        				if(logging) 
                			log.warn("SocketIOGateway(" + gatewayID
                                + ") Received a custom event: " + keyProperty + " from SessionId: " + conn.getSessionId());
                        // Get a key for the connection
                        //String theKey = getUniqueKey(conn);
                		UUID theKey = conn.getSessionId();
                		
                        for (String path : cfcListeners) {
                            
                            CFEvent event = new CFEvent(gatewayID);
                            
                            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                            mydata.put("DATA", data);
                            mydata.put("CONN", conn);
                            mydata.put("EVENT", keyProperty);
                            
                            event.setData(mydata);
                            event.setGatewayType("WebSocket");
                            event.setOriginatorID(theKey.toString());
                            event.setCfcMethod(cfcEntryPoint+"EVENT");
                            event.setCfcTimeOut(10);
                            if (path != null) {
                                event.setCfcPath(path);
                            }
                            
                            boolean sent = gatewayService.addEvent(event);
                            if (!sent) {
                                log
                                .error("SocketGateway("
                                        + gatewayID
                                        + ") Unable to put message on event queue. Message not sent from "
                                        + gatewayID + ", SessionId" + theKey
                                        + ".  Data was " + data + ". Event was: " + keyProperty);
                            }
                        }
        			}        			
        		});
				
			}
		 } catch(Exception e) {
			 log.error("SocketIOGateway(" + gatewayID
	                    + ") Unable to add custom event listener" 
	                    + "': " + e.toString(), e);
    	 }
    }
	
	public void buildConnectListener(SocketIOSSLServer server) {
		try {
			server.addConnectListener(new ConnectListener() {
	        	@Override
	    	    public void onConnect(SocketIOClient conn) {
	        		if(logging) 
	        			log.warn("SocketIOSSL Session A connection request was made with sessionID: "
	                        + conn.getSessionId() + ".");
	    	        // Get a key for the connection
	    	        //String theKey = getUniqueKey(conn);
	        		UUID theKey = conn.getSessionId();
	    	        connectionRegistry.put(theKey.toString(), conn);
	    	        
	    	        for (String path : cfcListeners) {
	    	            
	    	            CFEvent event = new CFEvent(gatewayID);
	    	            
	    	            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
	    	            mydata.put("CONN", conn);
	    	            
	    	            event.setData(mydata);
	    	            event.setGatewayType("WebSocket");
	    	            event.setOriginatorID(theKey.toString());
	    	            event.setCfcMethod("onOpen");
	    	            event.setCfcTimeOut(10);
	    	            if (path != null) {
	    	                event.setCfcPath(path);
	    	            }
	    	            
	    	            boolean sent = gatewayService.addEvent(event);
	    	            if (!sent) {
	    	                log.error("SocketGateway("
	    	                        + gatewayID
	    	                        + ") Unable to put message on event queue. Message not sent from "
	    	                        + gatewayID + ", thread " + theKey + ".");
	    	            }
	    	        }
	    	        
	    	    }
	        });
		} catch(Exception e) {
			 log.error("SocketIOGateway(" + gatewayID
		    + ") Unable to add build connect Listener" 
		    + "': " + e.toString(), e);
		}
    }
    
    public void buildDisconnectListener(SocketIOSSLServer server) {
    	try {
    		server.addDisconnectListener(new DisconnectListener() {
            	@Override
                public void onDisconnect(SocketIOClient conn) {
            		if(logging)
            			log.warn("SocketIOSSL Session(" + conn.getSessionId()
                            + ") Disconnected from SocketIO webserver.");
                    // Get a key for the connection
            		UUID theKey = conn.getSessionId();
                    connectionRegistry.remove(theKey.toString());
                    
                    for (String path : cfcListeners) {
                        
                        CFEvent event = new CFEvent(gatewayID);
                        
                        Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                        mydata.put("CONN", conn);
                        
                        event.setData(mydata);
                        event.setGatewayType("WebSocket");
                        event.setOriginatorID(theKey.toString());
                        event.setCfcMethod("onClose");
                        event.setCfcTimeOut(10);
                        if (path != null) {
                            event.setCfcPath(path);
                        }
                        
                        boolean sent = gatewayService.addEvent(event);
                        if (!sent) {
                            log
                            .error("SocketGateway("
                                    + gatewayID
                                    + ") Unable to put message on event queue. Message not sent from "
                                    + gatewayID + ", thread " + theKey + ".");
                        }
                    }
                    
                }            	
            });
    	} catch(Exception e) {
			 log.error("SocketIOGateway(" + gatewayID
		    + ") Unable to add build disconnect Listener" 
		    + "': " + e.toString(), e);
		}
    	
    }
    
    public void buildMessageListener(SocketIOSSLServer server) {
    	try {
    		server.addMessageListener(new DataListener<String>() {
            	@Override
                public void onData(SocketIOClient conn, String message) {
            		if(logging)
            			log.warn("SocketIOSSLGateway(" + gatewayID
                            + ") Received a message from: SessionId: " + conn.getSessionId());
                    // Get a key for the connection
                    //String theKey = getUniqueKey(conn);
            		UUID theKey = conn.getSessionId();
            		
                    for (String path : cfcListeners) {
                        
                        CFEvent event = new CFEvent(gatewayID);
                        
                        Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                        mydata.put("MESSAGE", message);
                        mydata.put("CONN", conn);
                        
                        event.setData(mydata);
                        event.setGatewayType("WebSocket");
                        event.setOriginatorID(theKey.toString());
                        event.setCfcMethod(cfcEntryPoint+"MESSAGE");
                        event.setCfcTimeOut(10);
                        if (path != null) {
                            event.setCfcPath(path);
                        }
                        
                        boolean sent = gatewayService.addEvent(event);
                        if (!sent) {
                            log
                            .error("SocketGateway("
                                    + gatewayID
                                    + ") Unable to put message on event queue. Message not sent from "
                                    + gatewayID + ", thread " + theKey
                                    + ".  Message was " + message);
                        }
                    }
                }
            });
    	} catch(Exception e) {
			 log.error("SocketIOGateway(" + gatewayID
		    + ") Unable to add build Message Listener" 
		    + "': " + e.toString(), e);
		}
    }
    
    public void buildJSONListener(SocketIOSSLServer server) {
    	try {
    		server.addJsonObjectListener(new DataListener<Object>() {
             	@Override
                 public void onData(SocketIOClient conn, Object data) {
             		if(logging) 
             			log.warn("SocketIOSSLGateway(" + gatewayID
                             + ") Received a message from SessionId: " + conn.getSessionId());
                     // Get a key for the connection
                     //String theKey = getUniqueKey(conn);
             		UUID theKey = conn.getSessionId();
             		
                     for (String path : cfcListeners) {
                         
                         CFEvent event = new CFEvent(gatewayID);
                         
                         Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                         mydata.put("DATA", data);
                         mydata.put("CONN", conn);
                         
                         event.setData(mydata);
                         event.setGatewayType("WebSocket");
                         event.setOriginatorID(theKey.toString());
                         event.setCfcMethod(cfcEntryPoint+"JSON");
                         event.setCfcTimeOut(10);
                         if (path != null) {
                             event.setCfcPath(path);
                         }
                         
                         boolean sent = gatewayService.addEvent(event);
                         if (!sent) {
                             log
                             .error("SocketGateway("
                                     + gatewayID
                                     + ") Unable to put message on event queue. Message not sent from "
                                     + gatewayID + ", thread " + theKey
                                     + ".  Data was " + data);
                         }
                     }
                 }
             });
    	} catch(Exception e) {
			 log.error("SocketIOGateway(" + gatewayID
		    + ") Unable to add build JSON Listener" 
		    + "': " + e.toString(), e);
		}
    	 
    }
    
	public void buildEventListeners(SocketIOSSLServer server) {
		try {
			String[] events = p.getProperty("eventlist").split(",");
			for(int i = 0; i < events.length; i++) {
				final String keyProperty = events[i];
				
				if(logging) 
        			log.warn("Adding SSL event listener: " + keyProperty);
        		
        		server.addEventListener(keyProperty, new DataListener<Object>() {
        			@Override
        			public void onData(SocketIOClient conn, Object data) {
        				if(logging) 
                			log.warn("SocketIOSSLGateway(" + gatewayID
                                + ") Received a custom event: " + keyProperty + " from SessionId: " + conn.getSessionId());
                        // Get a key for the connection
                        //String theKey = getUniqueKey(conn);
                		UUID theKey = conn.getSessionId();
                		
                        for (String path : cfcListeners) {
                            
                            CFEvent event = new CFEvent(gatewayID);
                            
                            Hashtable<String, Object> mydata = new Hashtable<String, Object>();
                            mydata.put("DATA", data);
                            mydata.put("CONN", conn);
                            mydata.put("EVENT", keyProperty);
                            
                            event.setData(mydata);
                            event.setGatewayType("WebSocket");
                            event.setOriginatorID(theKey.toString());
                            event.setCfcMethod(cfcEntryPoint+"EVENT");
                            event.setCfcTimeOut(10);
                            if (path != null) {
                                event.setCfcPath(path);
                            }
                            
                            boolean sent = gatewayService.addEvent(event);
                            if (!sent) {
                                log
                                .error("SocketGateway("
                                        + gatewayID
                                        + ") Unable to put message on event queue. Message not sent from "
                                        + gatewayID + ", SessionId" + theKey
                                        + ".  Data was " + data + ". Event was: " + keyProperty);
                            }
                        }
        			}        			
        		});
				
			}
		 } catch(Exception e) {
			 log.error("SocketIOGateway(" + gatewayID
	                    + ") Unable to add custom event listener" 
	                    + "': " + e.toString(), e);
    	 }
    }
    
    @Override
    public String outgoingMessage(CFEvent cfmsg) {
    	
        // Get the table of data returned from the even handler
        Map<?, ?> data = cfmsg.getData();
        
        if(logging)
    		log.warn("Trying to send message that came from function: " + data.get("FUNCTIONNAME") 
    				+ " originatorId: " + cfmsg.getOriginatorID());
        
        //This should be: 
        //String cfcMethod = cfmsg.getCFCMethod();
        //But it is returning null for some reason? Not sure why.
        String cfcMethod = (String) data.get("FUNCTIONNAME");
        
        //Check for an event cfc firing and get the event name for passing back to Socket.IO
        String messageType = null, eventType = null;
        
        for(int i = 0; i < eventList.length; i++) {
        	if(cfcMethod.equalsIgnoreCase(cfcEntryPoint + eventList[i])) {
        		messageType = eventList[i];
        	}
        }
        
        if(messageType.equalsIgnoreCase("EVENT")) {
        	eventType = (String) data.get("EVENT");
        }
        
        Object value = data.get("MESSAGE");
        
        if(logging) 
        	log.warn("SocketIOGateway(" + gatewayID
                + ") Trying to send outgoingMessage! Message:" + value);
        
        if (value != null) {
            // Play it safe and convert message to a String
            // TODO: convert value to JSON
            //String message = value.toString();
            
            String theKey = (String) data.get("DESTINATIONWEBSOCKETID");
            SocketIOClient conn = null;
            if (theKey != null) {
                conn = connectionRegistry.get(theKey);
                if (conn != null) {
                    sendTo(conn,value,messageType, eventType);
                }
                return "OK";
            }
            
            Array keys = (Array) data.get("DESTINATIONWEBSOCKETIDS");
            HashSet<SocketIOClient> conns = null;
            if(keys != null) {
                conns = new HashSet<SocketIOClient>(keys.size());
                for (int i = 0; i < keys.size(); i++)
                {
                	SocketIOClient c = connectionRegistry.get(keys.get(i));
                    if (c != null  && !c.getSessionId().equals(cfmsg.getOriginatorID())) {
                        conns.add(c);
                    }
                }
                if (conns != null && conns.size() > 0) { 
                    sendTo(conns,value,messageType, eventType); 
                }
                return "OK"; 
            } 
            
            //data.get("BROADCASTALLBUTSENDER");
                        
            sendToAll(value,messageType, eventType); 
            
        }
        
        // Return a String, possibly a messageID number or error string.
        return "OK";
    }
    
    @Override
    public String getGatewayID() {
        return gatewayID;
    }
    
    @Override
    public GatewayHelper getHelper() {
        return null;
    }
    
    @Override
    public int getStatus() {
        return status;
    }
    
    @Override
    public void setCFCListeners(String[] listeners) {
        for (int i = 0; i < listeners.length; i++) {
            cfcListeners.add(listeners[i]);
        }
    }
    
    @Override
    public void setGatewayID(String id) {
        gatewayID = id;
    }    
    
    /* Helpers */
    
    private void sendToAll(Object message, String messageType, String eventType) {
    	if(logging) 
    		log.warn("Executing: sendToAll with messageType: " + messageType + " and message: " + message);
    	if(SSLEnabled) {
    		if(messageType == "MESSAGE") {
    			serverSSL.getBroadcastOperations().sendMessage(message.toString());
        	} else if (messageType == "EVENT") {
        		serverSSL.getBroadcastOperations().sendEvent(eventType, message);
    		} else if (messageType == "JSON") {
    			serverSSL.getBroadcastOperations().sendJsonObject(message);
        	} else {
        		log.error("Tried to fire a message for event type: " + messageType + " but couldnt find it!");
        	}
    	} else {
    		if(messageType == "MESSAGE") {
        		server.getBroadcastOperations().sendMessage(message.toString());
        	} else if (messageType == "EVENT") {
    			server.getBroadcastOperations().sendEvent(eventType, message);
    		} else if (messageType == "JSON") {
        		server.getBroadcastOperations().sendJsonObject(message);
        	} else {
        		log.error("Tried to fire a message for event type: " + messageType + " but couldnt find it!");
        	}
    	}
    }

    private void sendTo(HashSet<SocketIOClient> conns, Object message, String messageType, String eventType) {
    	if(logging)
    		log.warn("Executing: sendTo with multiple connections with messageType: " + messageType + " and message: " + message);
    	
        for ( SocketIOClient conn : conns ) {
        	if(messageType == "MESSAGE") {
        		conn.sendMessage(message.toString());
        	} else if (messageType == "EVENT") {
    			conn.sendEvent(eventType, message);
    		} else if (messageType == "JSON") {
        		conn.sendJsonObject(message);
        	} else {
        		log.error("Tried to fire a message for event type: " + messageType + " but couldnt find it!");
        	}
        }        
    }

    private void sendTo(SocketIOClient conn, Object message, String messageType, String eventType) {
    	if(logging)
    		log.warn("Executing: sendTo sessionId: + " + conn.getSessionId() + " with messageType: " + messageType + " and message: " + message);
    	
    	if(messageType == "MESSAGE") {
    		conn.sendMessage(message.toString());
    	} else if (messageType == "EVENT") {
			conn.sendEvent(eventType, message);
		} else if (messageType == "JSON") {
    		conn.sendJsonObject(message);
    	} else {
    		log.error("Tried to fire a message for event type: " + messageType + " but couldnt find it!");
    	}
    	
        //conn.send(message);
    }

}
