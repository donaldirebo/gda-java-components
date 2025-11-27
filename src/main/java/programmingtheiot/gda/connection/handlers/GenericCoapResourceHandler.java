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
package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

/**
 * Generic CoAP resource handler supporting GET, PUT, POST, and DELETE operations.
 *
 */
public class GenericCoapResourceHandler extends CoapResource
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(GenericCoapResourceHandler.class.getName());
	
	// params
	
	private IDataMessageListener dataMsgListener = null;
	private String lastPayload = "";
	private ResourceNameEnum resourceEnum = null;
	
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 * @param resource Basically, the path (or topic)
	 */
	public GenericCoapResourceHandler(ResourceNameEnum resource)
	{
		this(resource.getResourceName());
		this.resourceEnum = resource;
	}
	
	/**
	 * Constructor.
	 * 
	 * @param resourceName The name of the resource.
	 */
	public GenericCoapResourceHandler(String resourceName)
	{
		super(resourceName);
	}
	
	
	// public methods
	
	@Override
	public void handleDELETE(CoapExchange context)
	{
		_Logger.info("Handling DELETE request for resource: " + this.getName());
		
		try {
			// Respond with DELETED (2.02)
			context.respond(ResponseCode.DELETED);
			_Logger.info("DELETE request handled successfully for: " + this.getName());
		} catch (Exception e) {
			_Logger.warning("Failed to handle DELETE request: " + e.getMessage());
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	@Override
	public void handleGET(CoapExchange context)
	{
		_Logger.info("Handling GET request for resource: " + this.getName());
		
		try {
			// Create a response payload
			String responsePayload = "{\"resource\":\"" + this.getName() + "\",\"status\":\"ok\"}";
			
			// Respond with CONTENT (2.05) and the payload
			context.respond(ResponseCode.CONTENT, responsePayload);
			
			_Logger.info("GET request handled successfully for: " + this.getName());
			_Logger.info("Returning payload: " + responsePayload);
		} catch (Exception e) {
			_Logger.warning("Failed to handle GET request: " + e.getMessage());
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	@Override
	public void handlePOST(CoapExchange context)
	{
		_Logger.info("Handling POST request for resource: " + this.getName());
		
		try {
			// Get the payload from the request
			byte[] payload = context.getRequestPayload();
			String payloadStr = payload != null ? new String(payload) : "";
			
			_Logger.info("POST payload received: " + payloadStr);
			
			// Store the payload
			this.lastPayload = payloadStr;
			
			// Notify listener if available
			if (this.dataMsgListener != null && this.resourceEnum != null) {
				this.dataMsgListener.handleIncomingMessage(this.resourceEnum, payloadStr);
				_Logger.fine("Notified listener of POST data");
			}
			
			// Respond with CREATED (2.01)
			context.respond(ResponseCode.CREATED);
			
			_Logger.info("POST request handled successfully for: " + this.getName());
		} catch (Exception e) {
			_Logger.warning("Failed to handle POST request: " + e.getMessage());
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	@Override
	public void handlePUT(CoapExchange context)
	{
		_Logger.info("Handling PUT request for resource: " + this.getName());
		
		try {
			// Get the payload from the request
			byte[] payload = context.getRequestPayload();
			String payloadStr = payload != null ? new String(payload) : "";
			
			_Logger.info("PUT payload received: " + payloadStr);
			
			// Store the payload
			this.lastPayload = payloadStr;
			
			// Notify listener if available
			if (this.dataMsgListener != null && this.resourceEnum != null) {
				this.dataMsgListener.handleIncomingMessage(this.resourceEnum, payloadStr);
				_Logger.fine("Notified listener of PUT data");
			}
			
			// Respond with CHANGED (2.04)
			context.respond(ResponseCode.CHANGED);
			
			_Logger.info("PUT request handled successfully for: " + this.getName());
		} catch (Exception e) {
			_Logger.warning("Failed to handle PUT request: " + e.getMessage());
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		this.dataMsgListener = listener;
		if (listener != null) {
			_Logger.fine("Data message listener set for resource: " + this.getName());
		}
	}
	
}