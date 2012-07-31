package com.sra.socketiogateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.Security;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import coldfusion.eventgateway.GatewayServices;
import coldfusion.eventgateway.Logger;


public class SocketIOSslServerSslContext  {
	// The handle to the CF gateway service
    protected GatewayServices gatewayService;

    // A logger
    protected Logger log;
    
	private static final String PROTOCOL = "TLS";
    private SSLContext _serverContext;
    
    protected String keyStoreFilePath, keyStoreFilePassword, keyPassword = null;
    

    /**
     * Returns the singleton instance for this class
     */
    public static SocketIOSslServerSslContext getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * SingletonHolder is loaded on the first execution of Singleton.getInstance() or the first access to
     * SingletonHolder.INSTANCE, not before.
     *
     * See http://en.wikipedia.org/wiki/Singleton_pattern
     */
    private static class SingletonHolder {

        public static final SocketIOSslServerSslContext INSTANCE = new SocketIOSslServerSslContext();
    }

    /**
     * Constructor for singleton
     */
    private SocketIOSslServerSslContext() {
    	
    	gatewayService = GatewayServices.getGatewayServices();
        // log things to socketio-gateway.log in the CF log directory
        log = gatewayService.getLogger("socketio-gateway");
        log.error("Executing SocketIOSSLServerSSLContext thingamagig");
        
        try {
            // Key store (Server side certificate)
            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            if (algorithm == null) {
                algorithm = "SunX509";
            }
            
            log.error("Using algorithm: " + algorithm);

            SSLContext serverContext = null;
            try {
                keyStoreFilePath = System.getProperty("keystore.file.path");
                keyStoreFilePassword = System.getProperty("keystore.file.password");
                
                log.error("keyStoreFilePath: " + keyStoreFilePath + " keyStoreFilePassword: " + keyStoreFilePassword);
                
                if(keyStoreFilePath == null || keyStoreFilePassword == null){
                	this.readSSLConfig(SocketIOGateway.CONFIG_PATH);
                }
                
                log.error("keyStoreFilePath: " + keyStoreFilePath + " keyStoreFilePassword: " + keyStoreFilePassword);
                
                KeyStore ks = KeyStore.getInstance("JKS");
                FileInputStream fin = new FileInputStream(keyStoreFilePath);
                ks.load(fin, keyStoreFilePassword.toCharArray());

                // Set up key manager factory to use our key store
                // Assume key password is the same as the key store file
                // password
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
                
                // At least try and use the keyPassword if it exists.
                // Not sure on best practices for this. The cfg file should probably be moved out of 
                // the web-accessable area if you do this
                if(keyPassword != null) {
                	kmf.init(ks, keyPassword.toCharArray());
                } else {
                	kmf.init(ks, keyStoreFilePassword.toCharArray());
                }
                

                // Initialise the SSLContext to work with our key managers.
                serverContext = SSLContext.getInstance(PROTOCOL);
                serverContext.init(kmf.getKeyManagers(), null, null);
            } catch (Exception e) {
            	log.error("Failed to initialize the server-side SSLContext" + e.getMessage(), e);
                throw new Error("Failed to initialize the server-side SSLContext", e);
            }
            _serverContext = serverContext;
        } catch (Exception ex) {
            log.error("Error initializing SslContextManager. " + ex.getMessage(), ex);
            System.exit(1);
        }
    }
    
    private void readSSLConfig(String configpath) {
    	log.error("Reading SSL Config from configpath: " + configpath);
    	try {
            FileInputStream pFile = new FileInputStream(configpath);
            Properties p = new Properties();
            p.load(pFile);
            pFile.close();
            if (p.containsKey("keyStoreFilePath")) {
            	log.error("found keyStoreFilePath: " + p.getProperty("keyStoreFilePath"));
                this.keyStoreFilePath = p.getProperty("keyStoreFilePath");
            }            	
            if (p.containsKey("keyStoreFilePassword")) {
            	log.error("found keyStoreFilePassword: " + p.getProperty("keyStoreFilePassword"));
                this.keyStoreFilePassword = p.getProperty("keyStoreFilePassword");
            }            	
            if (p.containsKey("keyPassword")) {
            	log.error("found keyPassword: " + p.getProperty("keyPassword"));
                this.keyPassword = p.getProperty("keyPassword");
            }
         } catch (IOException e) {
            // do nothing. use default value for port.
            log.error("SocketIOSslServerSslContext: Unable to read configuration file '" + configpath
                    + "': " + e.toString(), e);
        }
    }

    /**
     * Returns the server context with server side key store
     */
    public SSLContext getServerContext() {
        return _serverContext;
    }
}
