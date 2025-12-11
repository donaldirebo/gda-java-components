/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */
package programmingtheiot.gda.connection;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Cloud client connector that implements ICloudClient and IConnectionListener.
 * Uses MqttClientConnector to communicate with cloud services via MQTT.
 * Handles LED actuation events from the cloud service.
 */
public class CloudClientConnector implements ICloudClient, IConnectionListener
{
	// static
	private static final Logger _Logger =
		Logger.getLogger(CloudClientConnector.class.getName());
	
	// private var's
	private String topicPrefix = "";
	private MqttClientConnector mqttClient = null;
	private IDataMessageListener dataMsgListener = null;
	
	// QoS level for cloud communications
	private int qosLevel = 1;
	
	// constructors
	
	/**
	 * Default constructor.
	 * Initializes the topic prefix from configuration.
	 */
	public CloudClientConnector()
	{
		super();
		
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.topicPrefix =
			configUtil.getProperty(ConfigConst.CLOUD_GATEWAY_SERVICE, ConfigConst.BASE_TOPIC_KEY);
		
		// Depending on the cloud service, the topic names may or may not begin with a "/"
		if (topicPrefix == null) {
			topicPrefix = "/";
		} else {
			if (! topicPrefix.endsWith("/")) {
				topicPrefix += "/";
			}
		}
		
		_Logger.info("CloudClientConnector initialized with topic prefix: " + this.topicPrefix);
	}
	
	// public methods - ICloudClient implementation
	
	@Override
	public boolean connectClient()
	{
		if (this.mqttClient == null) {
			// Create MqttClientConnector using cloud gateway configuration
			this.mqttClient = new MqttClientConnector(ConfigConst.CLOUD_GATEWAY_SERVICE);
			
			// Set this as the connection listener to receive onConnect() callback
			this.mqttClient.setConnectionListener(this);
		}
		
		return this.mqttClient.connectClient();
	}
	
	@Override
	public boolean disconnectClient()
	{
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			return this.mqttClient.disconnectClient();
		}
		
		return false;
	}
	
	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SensorData data)
	{
		if (resource != null && data != null) {
			String payload = DataUtil.getInstance().sensorDataToTimeAndValueJson(data);
			
			return publishMessageToCloud(resource, data.getName(), payload);
		}
		
		return false;
	}
	
	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SystemPerformanceData data)
	{
		if (resource != null && data != null) {
			// Send CPU utilization as a SensorData representation
			SensorData cpuData = new SensorData();
			cpuData.updateData(data);
			cpuData.setName(ConfigConst.CPU_UTIL_NAME);
			cpuData.setValue(data.getCpuUtilization());
			
			boolean cpuDataSuccess = sendEdgeDataToCloud(resource, cpuData);
			
			if (! cpuDataSuccess) {
				_Logger.warning("Failed to send CPU utilization data to cloud service.");
			}
			
			// Send memory utilization as a SensorData representation
			SensorData memData = new SensorData();
			memData.updateData(data);
			memData.setName(ConfigConst.MEM_UTIL_NAME);
			memData.setValue(data.getMemoryUtilization());
			
			boolean memDataSuccess = sendEdgeDataToCloud(resource, memData);
			
			if (! memDataSuccess) {
				_Logger.warning("Failed to send memory utilization data to cloud service.");
			}
			
			return (cpuDataSuccess == memDataSuccess);
		}
		
		return false;
	}
	
	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;
		
		String topicName = null;
		
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			topicName = createTopicName(resource);
			
			this.mqttClient.subscribeToTopic(topicName, this.qosLevel);
			
			success = true;
		} else {
			_Logger.warning("Subscription methods only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
		}
		
		return success;
	}
	
	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource)
	{
		boolean success = false;
		
		String topicName = null;
		
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			topicName = createTopicName(resource);
			
			this.mqttClient.unsubscribeFromTopic(topicName);
			
			success = true;
		} else {
			_Logger.warning("Unsubscribe method only available for MQTT. No MQTT connection to broker. Ignoring. Topic: " + topicName);
		}
		
		return success;
	}
	
	/**
	 * Checks if the cloud client is connected.
	 */
	public boolean isConnected()
	{
		return (this.mqttClient != null && this.mqttClient.isConnected());
	}
	
	// IConnectionListener implementation
	
	@Override
	public void onConnect()
	{
		_Logger.info("Handling CSP subscriptions and device topic provisioning...");
		
		LedEnablementMessageListener ledListener = new LedEnablementMessageListener(this.dataMsgListener);
		
		// Topic may not exist yet, so create a 'response' actuation event with invalid value
		// This will create the relevant topic if it doesn't yet exist
		ActuatorData ad = new ActuatorData();
		ad.setAsResponse();
		ad.setName(ConfigConst.LED_ACTUATOR_NAME);
		ad.setValue((float) -1.0); // Invalid actuation value
		
		String ledTopic = createTopicName(ledListener.getResource().getDeviceName(), ad.getName());
		
		String adJson = DataUtil.getInstance().actuatorDataToTimeAndValueJson(ad);
		
		this.publishMessageToCloud(ledTopic, adJson);
		
		this.mqttClient.subscribeToTopic(ledTopic, this.qosLevel, ledListener);
		
		_Logger.info("Subscribed to LED actuation topic: " + ledTopic);
	}
	
	@Override
	public void onDisconnect()
	{
		_Logger.info("MQTT client disconnected from cloud service. Nothing else to do.");
	}
	
	// private methods
	
	/**
	 * Creates a topic name from the resource enum.
	 */
	private String createTopicName(ResourceNameEnum resource)
	{
		return createTopicName(resource.getDeviceName(), resource.getResourceType());
	}
	
	/**
	 * Creates a topic name from resource and item name.
	 */
	private String createTopicName(ResourceNameEnum resource, String itemName)
	{
		return (createTopicName(resource) + "-" + itemName).toLowerCase();
	}
	
	/**
	 * Creates a topic name from device name and resource type.
	 */
	private String createTopicName(String deviceName, String resourceTypeName)
	{
		StringBuilder buf = new StringBuilder();
		
		if (deviceName != null && deviceName.trim().length() > 0) {
			buf.append(topicPrefix).append(deviceName);
		}
		
		if (resourceTypeName != null && resourceTypeName.trim().length() > 0) {
			buf.append('/').append(resourceTypeName);
		}
		
		return buf.toString().toLowerCase();
	}
	
	/**
	 * Publishes a message to the cloud with resource and item name.
	 */
	private boolean publishMessageToCloud(ResourceNameEnum resource, String itemName, String payload)
	{
		String topicName = createTopicName(resource) + "-" + itemName;
		
		return publishMessageToCloud(topicName, payload);
	}
	
	/**
	 * Publishes a message to the cloud service.
	 */
	private boolean publishMessageToCloud(String topicName, String payload)
	{
		try {
			_Logger.finest("Publishing payload value(s) to CSP: " + topicName);
			
			this.mqttClient.publishMessage(topicName, payload.getBytes(), this.qosLevel);
			
			return true;
		} catch (Exception e) {
			_Logger.warning("Failed to publish message to CSP: " + topicName);
		}
		
		return false;
	}
	
	// Inner class for LED actuation message handling
	
	/**
	 * Inner class to handle LED enablement messages from the cloud service.
	 */
	private class LedEnablementMessageListener implements IMqttMessageListener
	{
		private IDataMessageListener dataMsgListener = null;
		
		private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;
		
		private int    typeID   = ConfigConst.LED_ACTUATOR_TYPE;
		private String itemName = ConfigConst.LED_ACTUATOR_NAME;
		
		LedEnablementMessageListener(IDataMessageListener dataMsgListener)
		{
			this.dataMsgListener = dataMsgListener;
		}
		
		public ResourceNameEnum getResource()
		{
			return this.resource;
		}
		
		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception
		{
			try {
				String jsonData = new String(message.getPayload());
				
				_Logger.info("Received message from cloud on topic: " + topic);
				_Logger.info("Payload: " + jsonData);
				
				ActuatorData actuatorData =
					DataUtil.getInstance().jsonToActuatorData(jsonData);
				
				// Set location ID to match CDA's expected location
				actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
				actuatorData.setTypeID(this.typeID);
				actuatorData.setName(this.itemName);
				
				int val = (int) actuatorData.getValue();
				
				switch (val) {
					case ConfigConst.ON_COMMAND:
						_Logger.info("Received LED enablement message [ON].");
						actuatorData.setCommand(ConfigConst.ON_COMMAND);
						break;
						
					case ConfigConst.OFF_COMMAND:
						_Logger.info("Received LED enablement message [OFF].");
						actuatorData.setCommand(ConfigConst.OFF_COMMAND);
						break;
						
					default:
						_Logger.info("Received invalid LED command value: " + val + ". Ignoring.");
						return;
				}
				
				// Option 1: Pass JSON to handleIncomingMessage
				if (this.dataMsgListener != null) {
					jsonData = DataUtil.getInstance().actuatorDataToJson(actuatorData);
					
					this.dataMsgListener.handleIncomingMessage(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, jsonData);
				}
				
			} catch (Exception e) {
				_Logger.warning("Failed to convert message payload to ActuatorData: " + e.getMessage());
			}
		}
	}
}