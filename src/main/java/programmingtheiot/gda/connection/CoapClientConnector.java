/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */ 

package programmingtheiot.gda.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.core.config.CoapConfig;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

import programmingtheiot.data.DataUtil;

/**
 * CoAP client connector implementation using Californium library.
 * Implements IRequestResponseClient interface for CoAP communication.
 */
public class CoapClientConnector implements IRequestResponseClient
{
	// Static logger
	private static final Logger _Logger =
		Logger.getLogger(CoapClientConnector.class.getName());
	
	// Static initializer - initialize Californium configuration once per JVM
	static {
		try {
			// Register Californium configurations
			CoapConfig.register();
			UdpConfig.register();
			_Logger.info("Californium configurations registered");
		} catch (Exception e) {
			_Logger.fine("Configuration already registered or issue during registration: " + e.getMessage());
		}
	}
	
	// Class-scoped variables
	private String protocol;
	private String host;
	private int port;
	private String serverAddr;
	private String endpointPath;
	private CoapClient clientConn;
	private IDataMessageListener dataMsgListener;
	private boolean isSecure;
	private boolean enableConfirmedMsgs;
	private Map<String, CoapObserveRelation> observationMap = new HashMap<>();
	
	/**
	 * Default constructor.
	 * 
	 * All config data will be loaded from the config file.
	 */
	public CoapClientConnector()
	{
		this(null, false, false);
	}
		
	/**
	 * Constructor with parameters.
	 * 
	 * @param host The host address to connect to
	 * @param isSecure Whether to use secure CoAP (coaps)
	 * @param enableConfirmedMsgs Whether to use confirmed messages (CON)
	 */
	public CoapClientConnector(String host, boolean isSecure, boolean enableConfirmedMsgs)
	{
		this.dataMsgListener = null;
		this.isSecure = isSecure;
		this.enableConfirmedMsgs = enableConfirmedMsgs;
		
		// Get configuration
		ConfigUtil config = ConfigUtil.getInstance();
		
		// Use provided host or get from config
		if (host != null && !host.isEmpty()) {
			this.host = host;
		} else {
			this.host = config.getProperty(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
		}
		
		// Determine protocol and port based on encryption setting
		boolean enableCrypt = config.getBoolean(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.ENABLE_CRYPT_KEY);
		
		if (isSecure || enableCrypt) {
			this.protocol = ConfigConst.DEFAULT_COAP_SECURE_PROTOCOL;
			this.port = config.getInteger(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.SECURE_PORT_KEY, ConfigConst.DEFAULT_COAP_SECURE_PORT);
			this.isSecure = true;
		} else {
			this.protocol = ConfigConst.DEFAULT_COAP_PROTOCOL;
			this.port = config.getInteger(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_COAP_PORT);
			this.isSecure = false;
		}
		
		// Construct server address
		// NOTE: URL does not have a protocol handler for "coap",
		// so we need to construct the URL manually
		this.serverAddr = this.protocol + "://" + this.host + ":" + this.port;
		this.endpointPath = "";
		
		initClient();
		
		_Logger.info("Using URL for server conn: " + this.serverAddr);
	}
	
	/**
	 * Initializes the Californium CoAP client
	 */
	private void initClient()
	{
		try {
			// Create the CoAP client
			this.clientConn = new CoapClient(this.serverAddr);
			
			// Get the endpoint - this will create one if it doesn't exist
			org.eclipse.californium.core.network.Endpoint endpoint = this.clientConn.getEndpoint();
			
			// If endpoint exists and isn't started, start it
			if (endpoint != null && !endpoint.isStarted()) {
				endpoint.start();
				_Logger.info("CoAP client endpoint started on address: " + endpoint.getAddress());
			} else if (endpoint != null) {
				_Logger.fine("CoAP client endpoint already started");
			} else {
				_Logger.warning("Could not obtain endpoint from CoapClient");
			}
			
			_Logger.info("Created client connection to server / resource: " + this.serverAddr);
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize CoAP client: " + getServerUri(), e);
		}
	}
	
	/**
	 * Clears the endpoint path, resetting to base server address
	 */
	@Override
	public void clearEndpointPath()
	{
		this.endpointPath = "";
		updateClientUri();
		_Logger.fine("Cleared endpoint path. URI reset to: " + getServerUri());
	}
	
	/**
	 * Sets the endpoint path based on the resource
	 * 
	 * @param resource The resource enum to set
	 */
	@Override
	public void setEndpointPath(ResourceNameEnum resource)
	{
		if (resource != null) {
			this.endpointPath = resource.getResourceName();
			updateClientUri();
			_Logger.fine("Set endpoint path to: " + getServerUri());
		}
	}
	
	/**
	 * Updates the client URI with current endpoint path
	 */
	private void updateClientUri()
	{
		if (this.clientConn != null) {
			String fullUri = this.serverAddr + this.endpointPath;
			this.clientConn.setURI(fullUri);
			_Logger.fine("Updated client URI to: " + fullUri);
		}
	}
	
	/**
	 * Gets the full server URI
	 * 
	 * @return String representation of server URI
	 */
	private String getServerUri()
	{
		return this.serverAddr + this.endpointPath;
	}
	
	/**
	 * Creates a resource path from resource enum and optional name
	 * 
	 * @param resource The resource enum
	 * @param name Optional name to append
	 * @return String representation of full resource path
	 */
	private String createResourcePath(ResourceNameEnum resource, String name)
	{
		String resourcePath = "";
		boolean hasResource = false;
		
		if (resource != null) {
			resourcePath = resource.getResourceName();
			hasResource = true;
		}
		
		if (name != null && !name.isEmpty()) {
			if (hasResource) {
				resourcePath = resourcePath + "/";
			}
			resourcePath = resourcePath + name;
		}
		
		return resourcePath;
	}
	
	/**
	 * Sends a discovery request to the server
	 * 
	 * @param timeout The timeout in seconds
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean sendDiscoveryRequest(int timeout)
	{
		_Logger.info("Issuing discover...");
		
		try {
			if (this.clientConn != null) {
				// Give endpoint time to fully initialize
				try {
					Thread.sleep(200L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				
				// Use the well-known discovery endpoint (RFC 6690)
				this.clientConn.setURI(this.serverAddr + "/.well-known/core");
				
				// Set timeout for the request (convert seconds to milliseconds)
				this.clientConn.setTimeout((long) timeout * 1000L);
				
				CoapResponse response = this.clientConn.get();
				
				if (response != null) {
					_Logger.info("Discovery response received with code: " + response.getCode());
					
					if (response.isSuccess()) {
						String discoveryText = response.getResponseText();
						_Logger.info("Discovery response text: " + discoveryText);
						
						// Parse the comma-separated list of resources
						String[] resources = discoveryText.split(",");
						_Logger.info("Discovery returned " + resources.length + " resources");
						
						for (String resource : resources) {
							// Clean up the resource format: <path> attributes -> path
							String cleanResource = resource.trim().replace("<", "").replace(">", "");
							_Logger.info(" --> URI: " + cleanResource);
						}
						
						// Reset to base URI
						this.clientConn.setURI(this.serverAddr);
						return true;
					} else {
						_Logger.warning("Discovery request returned non-success code: " + response.getCode());
						return false;
					}
				} else {
					_Logger.warning("Discovery request returned null response (timeout or error)");
					return false;
				}
			} else {
				_Logger.warning("CoAP client is null. Cannot issue discovery request.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to issue discovery request", e);
			return false;
		}
	}
	
	/**
	 * Sends a GET request to the specified resource
	 * 
	 * @param resource The resource enum
	 * @param name Optional name to append to resource
	 * @param enableCON If true, use CON (confirmed) messaging
	 * @param timeout The timeout in seconds
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean sendGetRequest(ResourceNameEnum resource, String name, boolean enableCON, int timeout)
	{
		String resourcePath = createResourcePath(resource, name);
		_Logger.info("Issuing GET request to resource: " + resourcePath + " with CON=" + enableCON);
		
		try {
			if (this.clientConn != null) {
				// Set CON or NON messaging
				if (enableCON) {
					this.clientConn.useCONs();
					_Logger.fine("Using CON (confirmed) messaging");
				} else {
					this.clientConn.useNONs();
					_Logger.fine("Using NON (non-confirmed) messaging");
				}
				
				// Set the URI
				String fullUri = this.serverAddr + "/" + resourcePath;
				this.clientConn.setURI(fullUri);
				_Logger.fine("GET request URI set to: " + fullUri);
				
				// Set timeout (convert seconds to milliseconds)
				this.clientConn.setTimeout((long) timeout * 1000L);
				
				// Issue GET request
				CoapResponse response = this.clientConn.get();
				
				if (response != null) {
					_Logger.info("GET response received with code: " + response.getCode());
					
					if (response.isSuccess()) {
						String responseText = response.getResponseText();
						_Logger.info("GET response text: " + responseText);
						_Logger.info("GET response options: " + response.getOptions());
						
						// Notify listener if available
						if (this.dataMsgListener != null) {
							_Logger.fine("Notifying data message listener of GET response");
							// TODO: Parse and pass the response data to the listener
						}
						
						return true;
					} else {
						_Logger.warning("GET request returned non-success code: " + response.getCode());
						return false;
					}
				} else {
					_Logger.warning("GET request returned null response (timeout or error)");
					return false;
				}
			} else {
				_Logger.warning("CoAP client is null. Cannot issue GET request.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to issue GET request", e);
			return false;
		}
	}
	
	/**
	 * Sends a DELETE request to the specified resource
	 * 
	 * @param resource The resource enum
	 * @param name Optional name to append to resource
	 * @param enableCON If true, use CON (confirmed) messaging
	 * @param timeout The timeout in seconds
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean sendDeleteRequest(ResourceNameEnum resource, String name, boolean enableCON, int timeout)
	{
		String resourcePath = createResourcePath(resource, name);
		_Logger.info("Issuing DELETE request to resource: " + resourcePath + " with CON=" + enableCON);
		
		try {
			if (this.clientConn != null) {
				// Set CON or NON messaging
				if (enableCON) {
					this.clientConn.useCONs();
					_Logger.fine("Using CON (confirmed) messaging");
				} else {
					this.clientConn.useNONs();
					_Logger.fine("Using NON (non-confirmed) messaging");
				}
				
				// Set the URI
				String fullUri = this.serverAddr + "/" + resourcePath;
				this.clientConn.setURI(fullUri);
				_Logger.fine("DELETE request URI set to: " + fullUri);
				
				// Set timeout (convert seconds to milliseconds)
				this.clientConn.setTimeout((long) timeout * 1000L);
				
				// Issue DELETE request (no payload)
				CoapResponse response = this.clientConn.delete();
				
				if (response != null) {
					_Logger.info("DELETE response received with code: " + response.getCode());
					_Logger.info("Handling DELETE. Response: " + response.isSuccess() + " - " + 
						response.getOptions() + " - " + response.getCode() + " - " + response.getResponseText());
					
					if (response.isSuccess()) {
						String responseText = response.getResponseText();
						_Logger.info("DELETE response text: " + responseText);
						
						// Notify listener if available
						if (this.dataMsgListener != null) {
							_Logger.fine("Notifying data message listener of DELETE response");
							// TODO: Parse and pass the response data to the listener
						}
						
						return true;
					} else {
						_Logger.warning("DELETE request returned non-success code: " + response.getCode());
						return false;
					}
				} else {
					_Logger.warning("Handling DELETE. No response received.");
					return false;
				}
			} else {
				_Logger.warning("CoAP client is null. Cannot issue DELETE request.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to issue DELETE request", e);
			return false;
		}
	}
	
	/**
	 * Sends a POST request with payload to the specified resource
	 * 
	 * @param resource The resource enum
	 * @param name Optional name to append to resource
	 * @param enableCON If true, use CON (confirmed) messaging
	 * @param payload The JSON payload to send
	 * @param timeout The timeout in seconds
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean sendPostRequest(ResourceNameEnum resource, String name, boolean enableCON, String payload, int timeout)
	{
		String resourcePath = createResourcePath(resource, name);
		_Logger.info("Issuing POST request to resource: " + resourcePath + " with CON=" + enableCON);
		
		try {
			if (this.clientConn != null) {
				// Set CON or NON messaging
				if (enableCON) {
					this.clientConn.useCONs();
					_Logger.fine("Using CON (confirmed) messaging");
				} else {
					this.clientConn.useNONs();
					_Logger.fine("Using NON (non-confirmed) messaging");
				}
				
				// Set the URI
				String fullUri = this.serverAddr + "/" + resourcePath;
				this.clientConn.setURI(fullUri);
				_Logger.fine("POST request URI set to: " + fullUri);
				
				// Set timeout (convert seconds to milliseconds)
				this.clientConn.setTimeout((long) timeout * 1000L);
				
				// Issue POST request with payload
				CoapResponse response = this.clientConn.post(payload, MediaTypeRegistry.TEXT_PLAIN);
				
				if (response != null) {
					_Logger.info("POST response received with code: " + response.getCode());
					_Logger.info("Handling POST. Response: " + response.isSuccess() + " - " + 
						response.getOptions() + " - " + response.getCode() + " - " + response.getResponseText());
					
					if (response.isSuccess()) {
						String responseText = response.getResponseText();
						_Logger.info("POST response text: " + responseText);
						
						// Notify listener if available
						if (this.dataMsgListener != null) {
							_Logger.fine("Notifying data message listener of POST response");
							// TODO: Parse and pass the response data to the listener
						}
						
						return true;
					} else {
						_Logger.warning("POST request returned non-success code: " + response.getCode());
						return false;
					}
				} else {
					_Logger.warning("Handling POST. No response received.");
					return false;
				}
			} else {
				_Logger.warning("CoAP client is null. Cannot issue POST request.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to issue POST request", e);
			return false;
		}
	}
	
	/**
	 * Sends a PUT request with payload to the specified resource
	 * 
	 * @param resource The resource enum
	 * @param name Optional name to append to resource
	 * @param enableCON If true, use CON (confirmed) messaging
	 * @param payload The JSON payload to send
	 * @param timeout The timeout in seconds
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean sendPutRequest(ResourceNameEnum resource, String name, boolean enableCON, String payload, int timeout)
	{
		String resourcePath = createResourcePath(resource, name);
		_Logger.info("Issuing PUT request to resource: " + resourcePath + " with CON=" + enableCON);
		
		try {
			if (this.clientConn != null) {
				// Set CON or NON messaging
				if (enableCON) {
					this.clientConn.useCONs();
					_Logger.fine("Using CON (confirmed) messaging");
				} else {
					this.clientConn.useNONs();
					_Logger.fine("Using NON (non-confirmed) messaging");
				}
				
				// Set the URI
				String fullUri = this.serverAddr + "/" + resourcePath;
				this.clientConn.setURI(fullUri);
				_Logger.fine("PUT request URI set to: " + fullUri);
				
				// Set timeout (convert seconds to milliseconds)
				this.clientConn.setTimeout((long) timeout * 1000L);
				
				// Issue PUT request with payload
				CoapResponse response = this.clientConn.put(payload, MediaTypeRegistry.TEXT_PLAIN);
				
				if (response != null) {
					_Logger.info("PUT response received with code: " + response.getCode());
					_Logger.info("Handling PUT. Response: " + response.isSuccess() + " - " + 
						response.getOptions() + " - " + response.getCode() + " - " + response.getResponseText());
					
					if (response.isSuccess()) {
						String responseText = response.getResponseText();
						_Logger.info("PUT response text: " + responseText);
						
						// Notify listener if available
						if (this.dataMsgListener != null) {
							_Logger.fine("Notifying data message listener of PUT response");
							// TODO: Parse and pass the response data to the listener
						}
						
						return true;
					} else {
						_Logger.warning("PUT request returned non-success code: " + response.getCode());
						return false;
					}
				} else {
					_Logger.warning("Handling PUT. No response received.");
					return false;
				}
			} else {
				_Logger.warning("CoAP client is null. Cannot issue PUT request.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to issue PUT request", e);
			return false;
		}
	}
	
	/**
	 * Sets the data message listener for receiving callbacks
	 * 
	 * @param listener The IDataMessageListener instance
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
			_Logger.fine("Data message listener set successfully");
			return true;
		}
		
		_Logger.warning("Data message listener is null");
		return false;
	}
	
	/**
	 * Starts observing a resource on the server
	 * 
	 * @param resource The resource enum to observe
	 * @param name Optional name to append to resource
	 * @param ttl Time to live in seconds (0 or less = indefinite)
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean startObserver(ResourceNameEnum resource, String name, int ttl)
	{
		String resourcePath = createResourcePath(resource, name);
		_Logger.info("Starting OBSERVE request for resource: " + resourcePath);
		
		try {
			if (this.clientConn != null && resource != null) {
				// Create the full URI
				String fullUri = this.serverAddr + "/" + resourcePath;
				this.clientConn.setURI(fullUri);
				
				// Determine resource type and create appropriate handler
				CoapHandler handler = null;
				
				if (resource.getResourceName().contains("SensorMsg")) {
					handler = new SensorDataObserverHandler(resource);
					_Logger.info("Created SensorDataObserverHandler for: " + resource.getResourceName());
				} else if (resource.getResourceName().contains("SystemPerfMsg")) {
					handler = new SystemPerformanceDataObserverHandler(resource);
					_Logger.info("Created SystemPerformanceDataObserverHandler for: " + resource.getResourceName());
				} else {
					_Logger.warning("Unknown resource type for observation: " + resource.getResourceName());
					return false;
				}
				
				// Set the data message listener on the handler
				if (handler instanceof SensorDataObserverHandler) {
					((SensorDataObserverHandler) handler).setDataMessageListener(this.dataMsgListener);
				} else if (handler instanceof SystemPerformanceDataObserverHandler) {
					((SystemPerformanceDataObserverHandler) handler).setDataMessageListener(this.dataMsgListener);
				}
				
				// Start the observation
				CoapObserveRelation relation = this.clientConn.observe(handler);
				
				// Store the relation for potential cancellation later
				// Use a unique key based on resource enum name to ensure consistent lookup
				String mapKey = resource.name();
				
				if (relation != null && !relation.isCanceled()) {
					this.observationMap.put(mapKey, relation);
					_Logger.info("OBSERVE successfully started for: " + resourcePath + " (map key: " + mapKey + ")");
					return true;
				} else {
					_Logger.warning("Failed to establish observation relation for: " + resourcePath);
					return false;
				}
			} else {
				_Logger.warning("CoAP client is null or resource is null. Cannot start observation.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start observation for resource: " + resourcePath, e);
			return false;
		}
	}
	
	/**
	 * Stops observing a resource on the server
	 * 
	 * @param resourceType The resource enum to stop observing
	 * @param name Optional name to append to resource
	 * @param timeout The timeout in seconds
	 * @return boolean True if successful, False otherwise
	 */
	@Override
	public boolean stopObserver(ResourceNameEnum resourceType, String name, int timeout)
	{
		String resourcePath = createResourcePath(resourceType, name);
		_Logger.info("Stopping OBSERVE request for resource: " + resourcePath);
		
		try {
			// Use the same unique key that was used during startObserver
			String mapKey = resourceType.name();
			
			// Retrieve the observation relation from the map
			CoapObserveRelation relation = this.observationMap.get(mapKey);
			
			if (relation != null && !relation.isCanceled()) {
				relation.reactiveCancel();
				this.observationMap.remove(mapKey);
				_Logger.info("OBSERVE successfully stopped for: " + resourcePath + " (map key: " + mapKey + ")");
				return true;
			} else {
				_Logger.warning("No active observation found for resource: " + resourcePath + " (map key: " + mapKey + ")");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to stop observation for resource: " + resourcePath, e);
			return false;
		}
	}
}