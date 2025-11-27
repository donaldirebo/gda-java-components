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
import programmingtheiot.data.SensorData;
/**
 * CoAP resource handler for telemetry/sensor data updates from CDA.
 * Handles PUT, GET, POST, and DELETE requests.
 */
public class UpdateTelemetryResourceHandler extends CoapResource
{
	private static final Logger _Logger =
		Logger.getLogger(UpdateTelemetryResourceHandler.class.getName());
	
	private IDataMessageListener dataMsgListener = null;
	
	/**
	 * Constructor.
	 * 
	 * @param resourceName The resource name
	 */
	public UpdateTelemetryResourceHandler(String resourceName)
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
		_Logger.info("GET request received for: " + super.getName());
		
		context.accept();
		context.respond(ResponseCode.CONTENT, "GET request handled: " + super.getName());
	}
	
	/**
	 * Handle PUT request - updates telemetry/sensor data.
	 * 
	 * @param context CoapExchange with request/response info
	 */
	@Override
	public void handlePUT(CoapExchange context)
	{
		ResponseCode code = ResponseCode.NOT_ACCEPTABLE;
		
		context.accept();
		
		if (this.dataMsgListener != null) {
			try {
				String jsonData = new String(context.getRequestPayload());
				
				_Logger.info("PUT request received for: " + super.getName() + 
					" with payload: " + jsonData);
				
				SensorData sensorData =
					DataUtil.getInstance().jsonToSensorData(jsonData);
				
				this.dataMsgListener.handleSensorMessage(
					ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sensorData);
				
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
		
		String msg = "Update telemetry data request handled: " + super.getName();
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
		_Logger.info("DELETE request received for: " + super.getName());
		
		context.accept();
		context.respond(ResponseCode.DELETED, "DELETE request handled: " + super.getName());
	}
}