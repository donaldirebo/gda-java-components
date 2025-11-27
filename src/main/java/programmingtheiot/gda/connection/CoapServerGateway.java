/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * You may find it more helpful to your design to adjust the
 * functionality, constants and interfaces (if there are any)
 * provided within in order to meet the needs of your specific
 * Programming the Internet of Things project.
 */
package programmingtheiot.gda.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.interceptors.MessageTracer;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.Configuration;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.GenericCoapResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateSystemPerformanceResourceHandler;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class CoapServerGateway
{
	// static
	
	static {
		// Suppress Californium config file loading before class initialization
		System.setProperty("org.eclipse.californium.config.file", "");
	}
	
	private static final Logger _Logger =
		Logger.getLogger(CoapServerGateway.class.getName());
	
	// params
	
	private CoapServer coapServer = null;
	
	private IDataMessageListener dataMsgListener = null;
	
	private Map<String, Resource> resourceMap = new HashMap<>();
	
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 * @param dataMsgListener
	 */
	public CoapServerGateway(IDataMessageListener dataMsgListener)
	{
		super();
		
		/*
		 * Basic constructor implementation provided. Change as needed.
		 */
		
		this.dataMsgListener = dataMsgListener;
		
		initServer();
	}
		
	// public methods
	
	public void addResource(ResourceNameEnum resource, Resource handler)
	{
		if (resource != null && handler != null) {
			String resourceName = resource.getResourceName();
			
			if (!this.resourceMap.containsKey(resourceName)) {
				this.resourceMap.put(resourceName, handler);
				_Logger.info("Added CoAP resource: " + resourceName);
			} else {
				_Logger.warning("CoAP resource already exists: " + resourceName);
			}
		}
	}
	
	public boolean hasResource(String name)
	{
		if (name != null) {
			return this.resourceMap.containsKey(name);
		}
		
		return false;
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}
	
	public boolean startServer()
	{
		try {
			if (this.coapServer != null) {
				this.coapServer.start();
				
				// Add message logging
				for (Endpoint ep : this.coapServer.getEndpoints()) {
					ep.addInterceptor(new MessageTracer());
				}
				
				_Logger.info("CoAP server started successfully.");
				return true;
			} else {
				_Logger.warning("CoAP server START failed. Not yet initialized.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start CoAP server.", e);
		}
		
		return false;
	}
	
	public boolean stopServer()
	{
		try {
			if (this.coapServer != null) {
				this.coapServer.stop();
				
				_Logger.info("CoAP server stopped successfully.");
				return true;
			} else {
				_Logger.warning("CoAP server STOP failed. Not yet initialized.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to stop CoAP server.", e);
		}
		
		return false;
	}
	
	
	// private methods
	
	private Resource createResourceChain(ResourceNameEnum resource)
	{
		String resourceName = resource.getResourceName();
		String[] pathSegments = resourceName.split("/");
		
		Resource rootResource = null;
		Resource currentResource = null;
		
		// Build the resource hierarchy from root to leaf
		for (String segment : pathSegments) {
			if (segment.isEmpty()) {
				continue; // Skip empty segments
			}
			
			if (rootResource == null) {
				// Create root resource
				rootResource = new CoapResource(segment);
				currentResource = rootResource;
				_Logger.info("Created root resource: " + segment);
			} else {
				// Create child resource and attach to current
				Resource childResource = new CoapResource(segment);
				currentResource.add(childResource);
				currentResource = childResource;
				_Logger.info("Created child resource: " + segment);
			}
		}
		
		return rootResource;
	}

	private void initServer(ResourceNameEnum ...resources)
	{
		try {
			// Create Configuration programmatically to avoid file loading
			Configuration config = new Configuration();
			
			// Create CoapServer with programmatic configuration and port
			this.coapServer = new CoapServer(config, ConfigConst.DEFAULT_COAP_PORT);
			
			_Logger.info("CoapServer instance created successfully on port " + ConfigConst.DEFAULT_COAP_PORT);
			
			// Create root PIOT resource
			CoapResource piotRoot = new CoapResource("PIOT");
			
			// Create ConstrainedDevice branch
			CoapResource constrainedDevice = new CoapResource("ConstrainedDevice");
			piotRoot.add(constrainedDevice);
			
			// Add all ConstrainedDevice children
			constrainedDevice.add(new CoapResource("ActuatorCmd"));
			constrainedDevice.add(new CoapResource("ActuatorResponse"));
			constrainedDevice.add(new CoapResource("MgmtStatusCmd"));
			constrainedDevice.add(new CoapResource("MgmtStatusMsg"));
			constrainedDevice.add(new CoapResource("SensorMsg"));
			
			// Create and register the System Performance handler for ConstrainedDevice
			UpdateSystemPerformanceResourceHandler cdSysPerfHandler = 
				new UpdateSystemPerformanceResourceHandler(ConfigConst.SYSTEM_PERF_MSG);
			cdSysPerfHandler.setDataMessageListener(this.dataMsgListener);
			constrainedDevice.add(cdSysPerfHandler);
			_Logger.info("Added System Performance handler to ConstrainedDevice");
			
			// Create GatewayDevice branch
			CoapResource gatewayDevice = new CoapResource("GatewayDevice");
			piotRoot.add(gatewayDevice);
			
			// Add all GatewayDevice children with handlers for GET support
			GenericCoapResourceHandler mgmtStatusCmd = new GenericCoapResourceHandler(ConfigConst.MGMT_STATUS_CMD);
			mgmtStatusCmd.setDataMessageListener(this.dataMsgListener);
			gatewayDevice.add(mgmtStatusCmd);
			_Logger.info("Added MgmtStatusCmd resource handler with GET/PUT/POST/DELETE support to GatewayDevice");
			
			// Add MgmtStatusMsg as a resource handler (supports GET, PUT, POST, DELETE)
			GenericCoapResourceHandler mgmtStatusMsg = new GenericCoapResourceHandler(ConfigConst.MGMT_STATUS_MSG);
			mgmtStatusMsg.setDataMessageListener(this.dataMsgListener);
			gatewayDevice.add(mgmtStatusMsg);
			_Logger.info("Added MgmtStatusMsg resource handler with GET/PUT/POST/DELETE support to GatewayDevice");
			
			// Create and register the System Performance handler for GatewayDevice
			UpdateSystemPerformanceResourceHandler gwSysPerfHandler = 
				new UpdateSystemPerformanceResourceHandler(ConfigConst.SYSTEM_PERF_MSG);
			gwSysPerfHandler.setDataMessageListener(this.dataMsgListener);
			gatewayDevice.add(gwSysPerfHandler);
			_Logger.info("Added System Performance handler to GatewayDevice");
			
			// Add the root PIOT resource to the server
			this.coapServer.add(piotRoot);
			_Logger.info("CoAP server initialized with complete resource hierarchy.");
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize CoAP server: " + e.getMessage(), e);
			this.coapServer = null;
		}
	}
}