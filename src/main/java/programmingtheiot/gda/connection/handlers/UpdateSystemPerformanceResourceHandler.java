/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */
package programmingtheiot.gda.connection.handlers;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SystemPerformanceData;

/**
 * CoAP resource handler for system performance data updates from CDA.
 * Handles PUT, GET, POST, and DELETE requests.
 */
public class UpdateSystemPerformanceResourceHandler extends CoapResource
{
	private static final Logger _Logger =
		Logger.getLogger(UpdateSystemPerformanceResourceHandler.class.getName());
	
	private IDataMessageListener dataMsgListener = null;
	
	/**
	 * Constructor.
	 * 
	 * @param resourceName The resource name
	 */
	public UpdateSystemPerformanceResourceHandler(String resourceName)
	{
		super(resourceName);
	}
	
	/**
	 * Set the data message listener for callbacks.
	 * 
	 * @param listener IDataMessageListener instance
	 */
	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}
	
	/**
	 * Handle GET request.
	 * 
	 * @param context CoapExchange with request/response info
	 */
	@Override
	public void handleGET(CoapExchange context)
	{
		_Logger.info("=== handleGET() CALLED FOR: " + super.getName());
		_Logger.info("GET request received for: " + super.getName());
		
		context.accept();
		context.respond(ResponseCode.CONTENT, "GET request handled: " + super.getName());
	}
	
	/**
	 * Handle PUT request - updates system performance data.
	 * 
	 * @param context CoapExchange with request/response info
	 */
	@Override
	public void handlePUT(CoapExchange context)
	{
		_Logger.info("=== handlePUT() CALLED FOR: " + super.getName());
		
		ResponseCode code = ResponseCode.NOT_ACCEPTABLE;
		
		context.accept();
		
		if (this.dataMsgListener != null) {
			try {
				String jsonData = new String(context.getRequestPayload());
				
				_Logger.info("PUT request received for: " + super.getName() + 
					" with payload: " + jsonData);
				
				SystemPerformanceData sysPerfData =
					DataUtil.getInstance().jsonToSystemPerformanceData(jsonData);
				
				_Logger.info("Successfully converted JSON to SystemPerformanceData");
				
				this.dataMsgListener.handleSystemPerformanceMessage(
					ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, sysPerfData);
				
				_Logger.info("Successfully called dataMsgListener.handleSystemPerformanceMessage()");
				
				code = ResponseCode.CHANGED;
			} catch (Exception e) {
				_Logger.log(Level.WARNING,
					"Failed to handle PUT request. Message: " + e.getMessage(), e);
				
				code = ResponseCode.BAD_REQUEST;
			}
		} else {
			_Logger.info("No callback listener for request. Ignoring PUT.");
			code = ResponseCode.CONTINUE;
		}
		
		String msg = "Update system perf data request handled: " + super.getName();
		context.respond(code, msg);
	}
	
	/**
	 * Handle POST request.
	 * 
	 * @param context CoapExchange with request/response info
	 */
	@Override
	public void handlePOST(CoapExchange context)
	{
		_Logger.info("=== handlePOST() CALLED FOR: " + super.getName());
		_Logger.info("POST request received for: " + super.getName());
		
		context.accept();
		context.respond(ResponseCode.CREATED, "POST request handled: " + super.getName());
	}
	
	/**
	 * Handle DELETE request.
	 * 
	 * @param context CoapExchange with request/response info
	 */
	@Override
	public void handleDELETE(CoapExchange context)
	{
		_Logger.info("=== handleDELETE() CALLED FOR: " + super.getName());
		_Logger.info("DELETE request received for: " + super.getName());
		
		context.accept();
		context.respond(ResponseCode.DELETED, "DELETE request handled: " + super.getName());
	}
}