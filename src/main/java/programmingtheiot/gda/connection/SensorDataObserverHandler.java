package programmingtheiot.gda.connection;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;

/**
 * Observer handler for CoAP OBSERVE requests that receive SensorData updates.
 * Implements CoapHandler to process incoming sensor observations.
 */
public class SensorDataObserverHandler implements CoapHandler
{
	private static final Logger _Logger =
		Logger.getLogger(SensorDataObserverHandler.class.getName());
	
	private IDataMessageListener dataMsgListener = null;
	private ResourceNameEnum resourceName = null;
	
	
	public SensorDataObserverHandler()
	{
		super();
	}
	
	public SensorDataObserverHandler(ResourceNameEnum resourceName)
	{
		super();
		this.resourceName = resourceName;
	}
	
	
	@Override
	public void onError()
	{
		_Logger.warning("Handling CoAP error during SensorData observation...");
	}

	@Override
	public void onLoad(CoapResponse response)
	{
		if (response != null) {
			String responseText = response.getResponseText();
			_Logger.info("Received CoAP OBSERVE response for SensorData: " + responseText);
			
			try {
				SensorData sensorData = DataUtil.getInstance().jsonToSensorData(responseText);
				
				if (this.dataMsgListener != null && this.resourceName != null) {
					this.dataMsgListener.handleSensorMessage(this.resourceName, sensorData);
					_Logger.fine("Notified listener of SensorData update");
				}
			} catch (Exception e) {
				_Logger.warning("Failed to parse SensorData from response: " + e.getMessage());
			}
		} else {
			_Logger.warning("Received null CoAP response for SensorData observation");
		}
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		this.dataMsgListener = listener;
		if (listener != null) {
			_Logger.fine("Data message listener set for SensorDataObserverHandler");
		}
	}
	
	public void setResourceName(ResourceNameEnum resourceName)
	{
		this.resourceName = resourceName;
	}
}