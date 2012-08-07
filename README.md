#ColdFusion Socket.io WebSocket Gateway#

ColdFusion gateway designed to support the Socket.io WebSocket protocol implementation

This implementation is based on the [netty-socketio](https://github.com/mrniko/netty-socketio) package as well as [cf-websocket-gateway](https://github.com/nmische/cf-websocket-gateway) for inspiration on building the gateway

##Installing the Gateway##
* Download the `socketiogateway.zip` package from [Github](https://github.com/softwarezman/cf-socketiogateway/downloads).
* Extract all jar files to the ColdFusion classpath.
    * The best place to put the jar is in the `{cfusion.root}/gateway/lib` folder. For example `C:\ColdFusion9\gateway\lib` or `/Applications/JRun4/servers/cfusion/cfusion-ear/cfusion-war/WEB-INF/cfusion/gateway/lib/`
* Restart ColdFusion, if necessary.
* Log in to the ColdFusion administrator and navigate to the Event Gateways > Gateway Types page.
* Under Add / Edit ColdFusion Event Gateway Types enter the following:
    * Type Name: SocketIO
    * Description: Handles Socket.IO WebSocket Messaging
    * Java Class: com.sra.socketiogateway.SocketIOGateway
    * Startup Timeout: 30 seconds
    * Stop on Startup Timeout: checked

##Configuring the Gateway##
* Log in to the ColdFusion administrator and navigate to the Event Gateways > Gateway Instances page.
* Under Add / Edit ColdFusion Event Gateway Instances enter the following:
    * Gateway ID: An name for your gateway instance.
    * Gateway Type: SocketIO - Handles SocketIO WebSocket Messaging
    * CFC Path: Path to the CFC listner. For more about the listener CFC see Handling Incoming Messages.
    * Configuration File: The path to the configuration file. Below is an example configuration file
	
```
#port - the port the web server should listen on
port=9093

#host - the hostname you want the server to listen on. If you have your webserver listening on a defined hostname and not 'localhost' it most liekly won't see it
host=yourservername.com

#logging - set to true to have it output loging messages to the socketio-gateway.log file
logging=true

#eventlist - you can have more than one event to listen for but they MUST be comma delimited! It builds event listeners and passes them to the EVENT cfc
eventlist=client-doAdd,client-doUpdate,client-doRemove,client-doInitialLoad

#useSSL - enable this and java will try and find your security keys to use for a SSL connection. You must have your keystore file and password set somewhere in the Java runtime or else you must set it with the following properties
useSSL=true

#keystoreFilePath - the path to your cacerts file, notice how they are forward slashes
keyStoreFilePath=C:/ColdFusion9/runtime/jre/lib/security/cacerts

#keyStoreFilePassword - the password to access your keystore
keyStoreFilePassword=changeit

#keyPassword - the password for accessing the individual key
keyPassword=yourpasswordhere
```

## Handling Incoming Messages ##

### Events ###

The CFC listener may implement any of the three following methods to listen
to WebSocket Gateway events:

* onClose(event) - sent when a client connection is closed. The event has the following fields:
    * CfcMethod: Listener CFC method name, onClientClose in this case
    * Data.CONN: The underlying org.riaforge.websocketgateway.WebSocket object that fired the event
    * GatewayType: Always "WebSocket"
    * OriginatorID: A key identifying the underlying org.riaforge.websocketgateway.WebSocket object that fired the event
* onOpen(event) - sent when a client connection is opened. The event has the following fields:
    * CfcMethod: Listener CFC method name, onClientOpen in this case
    * Data.CONN: The underlying org.riaforge.websocketgateway.WebSocket object that fired the event
    * GatewayType: Always "WebSocket"
    * OriginatorID: A key identifying the underlying org.riaforge.websocketgateway.WebSocket object that fired the event
* onIncomingMessage(event) - sent when a client sends a message. The event has the following fields:
    * CfcMethod: Listener CFC method, onIncomingMessage in this case
    * Data.CONN: The underlying org.riaforge.websocketgateway.WebSocket object that fired the event
    * Data.MESSAGE: Message contents
    * GatewayType: Always "WebSocket"
    * OriginatorID: A key identifying the underlying org.riaforge.websocketgateway.WebSocket object that fired the event

### Targeted Messaging ###

By default outgoing messages are sent to all connected WebSocket clients. You may target an outgoing message such that it is only sent to a single client or subset of connected clients using either the DESTINATIONWEBSOCKETID or DESTINATIONWEBSOCKETIDS event fields. To send to a single client the DESTINATIONWEBSOCKETID field should be set to the key of the targeted client. To send to a subset of clients, the DESTINATIONWEBSOCKETIDS field should be set to an array of target keys.
### Example Listeners ###

Below is an example listener CFC that echos a message back to all connected clients:

    <cfcomponent>
      <cffunction name="onIncomingMessage">
        <cfargument name="event" />
        <cfset var msg = {} />
        <cfset msg["MESSAGE"] = event.data.message />
        <cfreturn msg />
      </cffunction>
    </cfcomponent>

Below is an example listener CFC that echos a message back to the client that sent the message:

    <cfcomponent>
      <cffunction name="onIncomingMessage">
        <cfargument name="event" />
        <cfset var msg = {} />
        <cfset msg["MESSAGE"] = event.data.message />
        <cfset msg["DESTINATIONWEBSOCKETID"] = event.originatorID />
        <cfreturn msg />
      </cffunction>
    </cfcomponent>

Your CFC should look something like...
<cfcomponent>
	
	<cffunction name="onIncomingMessageMESSAGE">
		<cfargument name="event" />
		<cfset var msg = {} />
		<cfset msg["MESSAGE"] = 'Messsage type' />
		<cfset msg["FUNCTIONNAME"] = GetFunctionCalledName()>
		<cfreturn msg />
	</cffunction>
	
	
	<cffunction name="onIncomingMessageEVENT">
		<cfargument name="event" />
		<cfset var msg = {} />
		<cfset msg["MESSAGE"] = event.data.DATA>
		<!---Change the response to send back so clients know it comes from the server--->
		<cfset msg["EVENT"] = Replace(event.data.EVENT, "client", "server") > 
		<cfset msg["FUNCTIONNAME"] = GetFunctionCalledName()>
		<cfreturn msg />
	</cffunction>
	
	
	<cffunction name="onIncomingMessageJSON" returnformat="json">
		<cfargument name="event" />
		<cfset var msg = StructNew() />
		<cfset msg["MESSAGE"] = event.data.DATA />
		<cfset msg["FUNCTIONNAME"] = GetFunctionCalledName()>
		<cfreturn msg />		
	</cffunction>
</cfcomponent>

* onIncomingMESSAGE listens for MESSAGE type events
* onIncomingMessageEVENT listens for those events that you defined in the cfg file
* onIncomingMessageJSON listens for events that were specifically JSON events