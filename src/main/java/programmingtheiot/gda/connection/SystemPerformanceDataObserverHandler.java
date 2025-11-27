package programmingtheiot.gda.connection;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Observer handler for CoAP OBSERVE requests that receive SystemPerformanceData updates.
 * Implements CoapHandler to process incoming system performance observations.
 */
public class SystemPerformanceDataObserverHandler implements CoapHandler
{
	private static final Logger _Logger =
		Logger.getLogger(SystemPerformanceDataObserverHandler.class.getName());
	
	private IDataMessageListener dataMsgListener = null;
	private ResourceNameEnum resourceName = null;
	
	
	public SystemPerformanceDataObserverHandler()
	{
		super();
	}
	
	public SystemPerformanceDataObserverHandler(ResourceNameEnum resourceName)
	{
		super();
		this.resourceName = resourceName;
	}
	
	
	@Override
	public void onError()
	{
		_Logger.warning("Handling CoAP error during SystemPerformanceData observation...");
	}

	@Override
	public void onLoad(CoapResponse response)
	{
		if (response != null) {
			String responseText = response.getResponseText();
			_Logger.info("Received CoAP OBSERVE response for SystemPerformanceData: " + responseText);
			
			try {
				SystemPerformanceData spdData = DataUtil.getInstance().jsonToSystemPerformanceData(responseText);
				
				if (this.dataMsgListener != null && this.resourceName != null) {
					this.dataMsgListener.handleSystemPerformanceMessage(this.resourceName, spdData);
					_Logger.fine("Notified listener of SystemPerformanceData update");
				}
			} catch (Exception e) {
				_Logger.warning("Failed to parse SystemPerformanceData from response: " + e.getMessage());
			}
		} else {
			_Logger.warning("Received null CoAP response for SystemPerformanceData observation");
		}
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		this.dataMsgListener = listener;
		if (listener != null) {
			_Logger.fine("Data message listener set for SystemPerformanceDataObserverHandler");
		}
	}
	
	public void setResourceName(ResourceNameEnum resourceName)
	{
		this.resourceName = resourceName;
	}
}