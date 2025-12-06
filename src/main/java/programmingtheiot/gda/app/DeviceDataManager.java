/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */
package programmingtheiot.gda.app;

import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.CloudClientConnector;
import programmingtheiot.gda.connection.CoapClientConnector;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.ICloudClient;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.gda.connection.SmtpClientConnector;

/**
 * Central data manager for the Gateway Device Application.
 * Coordinates all connections (MQTT, CoAP, Cloud, SMTP, Persistence).
 */
public class DeviceDataManager implements IDataMessageListener
{
	// static
	private static final Logger _Logger = Logger.getLogger(DeviceDataManager.class.getName());
	
	// private var's
	private ConfigUtil configUtil;
	private DataUtil dataUtil;
	
	private boolean enableMqttClient = false;
	private boolean enableCoapServer = false;
	private boolean enableCoapClient = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private ICloudClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private IRequestResponseClient coapClient = null;
	private CoapServerGateway coapServer = null;
	
	// constructors
	
	/**
	 * Default constructor.
	 */
	public DeviceDataManager()
	{
		super();
		
		this.configUtil = ConfigUtil.getInstance();
		this.dataUtil = DataUtil.getInstance();
		
		initConnections();
	}
	
	/**
	 * Constructor with enable flags.
	 */
	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapServer,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)
	{
		super();
		
		this.configUtil = ConfigUtil.getInstance();
		this.dataUtil = DataUtil.getInstance();
		
		this.enableMqttClient = enableMqttClient;
		this.enableCoapServer = enableCoapServer;
		this.enableCoapClient = enableCoapClient;
		this.enableCloudClient = enableCloudClient;
		this.enableSmtpClient = enableSmtpClient;
		this.enablePersistenceClient = enablePersistenceClient;
		
		initConnections();
	}
	
	
	// public methods
	
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		_Logger.log(Level.INFO, "Handling actuator command response: " + resourceName);
		
		if (data != null) {
			// TODO: In Part IV, convert to JSON and send upstream
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (data != null) {
			_Logger.log(
				Level.FINE,
				"Actuator request received: {0}. Message: {1}",
				new Object[] {resourceName.getResourceName(), Integer.valueOf((data.getCommand()))});
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}
			
			int qos = ConfigConst.DEFAULT_QOS;
			
			// Send actuator command to CDA via MQTT
			this.sendActuatorCommandToCda(resourceName, data);
			
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		if (resourceName != null && msg != null) {
			try {
				if (resourceName == ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE) {
					_Logger.info("Handling incoming ActuatorData message: " + msg);
					
					// Convert to ActuatorData and back for validation
					ActuatorData ad = DataUtil.getInstance().jsonToActuatorData(msg);
					String jsonData = DataUtil.getInstance().actuatorDataToJson(ad);
					
					if (this.mqttClient != null) {
						_Logger.info("Publishing actuator command to CDA via MQTT: " + jsonData);
						return this.mqttClient.publishMessage(resourceName, jsonData, ConfigConst.DEFAULT_QOS);
					}
					
					// TODO: If using CoAP, add that logic here
					
				} else {
					_Logger.warning("Failed to parse incoming message. Unknown type: " + msg);
					return false;
				}
			} catch (Exception e) {
				_Logger.log(Level.WARNING, "Failed to process incoming message for resource: " + resourceName, e);
			}
		} else {
			_Logger.warning("Incoming message has no data. Ignoring for resource: " + resourceName);
		}
		
		return false;
	}
	
	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		if (data != null) {
			_Logger.fine("Handling sensor message: " + data.getName());
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for SensorData instance.");
			}
			
			String jsonData = DataUtil.getInstance().sensorDataToJson(data);
			
			_Logger.fine("JSON [SensorData] -> " + jsonData);
			
			int qos = ConfigConst.DEFAULT_QOS;
			
			// Store data if persistence is enabled
			if (this.enablePersistenceClient && this.persistenceClient != null) {
				this.persistenceClient.storeData(resourceName.getResourceName(), qos, data);
			}
			
			// Analyze incoming data (e.g., threshold checks)
			this.handleIncomingDataAnalysis(resourceName, data);
			
			// Send to cloud service
			this.handleUpstreamTransmission(resourceName, data);
			
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		if (data != null) {
			_Logger.info("Handling system performance message: " + data.getName());
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for SystemPerformanceData instance.");
			}
			
			int qos = ConfigConst.DEFAULT_QOS;
			
			// Send to cloud service
			this.handleUpstreamTransmission(resourceName, data);
			
			return true;
		} else {
			return false;
		}
	}
	
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
		if (listener != null) {
			this.actuatorDataListener = listener;
		}
	}
	
	/**
	 * Starts all enabled connections and managers.
	 */
	public void startManager()
	{
		_Logger.log(Level.INFO, "Starting DeviceDataManager...");
		
		// Start cloud client FIRST to ensure connection is ready before data flows
		if (this.enableCloudClient && this.cloudClient != null) {
			if (this.cloudClient.connectClient()) {
				_Logger.log(Level.INFO, "Successfully connected cloud client to CSP.");
			} else {
				_Logger.log(Level.WARNING, "Failed to connect cloud client to CSP.");
			}
		}
		
		if (this.enableMqttClient && this.mqttClient != null) {
			if (this.mqttClient.connectClient()) {
				_Logger.log(Level.INFO, "Successfully connected MQTT client to broker.");
			} else {
				_Logger.log(Level.WARNING, "Failed to connect MQTT client to broker.");
			}
		}
		
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.coapServer.startServer()) {
				_Logger.info("CoAP server started.");
			} else {
				_Logger.severe("Failed to start CoAP server. Check log file for details.");
			}
		}
		
		if (this.enableCoapClient && this.coapClient != null) {
			_Logger.log(Level.INFO, "CoAP client is enabled and ready to send requests.");
		}
		
		_Logger.log(Level.INFO, "DeviceDataManager started.");
	}
	
	/**
	 * Stops all enabled connections and managers.
	 */
	public void stopManager()
	{
		_Logger.log(Level.INFO, "Stopping DeviceDataManager...");
		
		if (this.enableMqttClient && this.mqttClient != null) {
			// Unsubscribe from topics
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE);
			
			// Disconnect from broker
			if (this.mqttClient.disconnectClient()) {
				_Logger.log(Level.INFO, "Successfully disconnected MQTT client from broker.");
			}
		}
		
		if (this.enableCoapServer && this.coapServer != null) {
			if (this.coapServer.stopServer()) {
				_Logger.info("CoAP server stopped.");
			} else {
				_Logger.severe("Failed to stop CoAP server. Check log file for details.");
			}
		}
		
		if (this.enableCoapClient && this.coapClient != null) {
			_Logger.log(Level.INFO, "CoAP client stopped.");
		}
		
		// Disconnect cloud client LAST
		if (this.enableCloudClient && this.cloudClient != null) {
			if (this.cloudClient.disconnectClient()) {
				_Logger.log(Level.INFO, "Successfully disconnected cloud client from CSP.");
			}
		}
		
		_Logger.log(Level.INFO, "DeviceDataManager stopped.");
	}
	
	// private methods
	
	/**
	 * Initializes the enabled connections.
	 */
	private void initConnections()
	{
		_Logger.log(Level.INFO, "Initializing DeviceDataManager connections...");
		
		// Read MQTT enable flag from configuration
		this.enableMqttClient = this.configUtil.getBoolean(
			ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);
		
		if (this.enableMqttClient) {
			this.mqttClient = new MqttClientConnector();
			this.mqttClient.setDataMessageListener(this);
			_Logger.log(Level.INFO, "MQTT client initialized.");
		}
		
		// Read CoAP server enable flag from configuration
		this.enableCoapServer = this.configUtil.getBoolean(
			ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);
		
		if (this.enableCoapServer) {
			this.coapServer = new CoapServerGateway(this);
			_Logger.log(Level.INFO, "CoAP server initialized.");
		}
		
		// Read CoAP client enable flag from configuration
		this.enableCoapClient = this.configUtil.getBoolean(
			ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_CLIENT_KEY);
		
		if (this.enableCoapClient) {
			this.coapClient = new CoapClientConnector();
			this.coapClient.setDataMessageListener(this);
			_Logger.log(Level.INFO, "CoAP client initialized.");
		}
		
		// Read Cloud client enable flag from configuration
		this.enableCloudClient = this.configUtil.getBoolean(
			ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);
		
		if (this.enableCloudClient) {
			this.cloudClient = new CloudClientConnector();
			this.cloudClient.setDataMessageListener(this);
			_Logger.log(Level.INFO, "Cloud client initialized.");
		}
		
		_Logger.log(Level.INFO, "DeviceDataManager connections initialized.");
	}
	
	/**
	 * Sends actuator command to CDA via MQTT.
	 */
	private void sendActuatorCommandToCda(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (this.mqttClient != null) {
			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);
			
			if (this.mqttClient.publishMessage(resourceName, jsonData, ConfigConst.DEFAULT_QOS)) {
				_Logger.fine("Sent actuator command to CDA: " + resourceName);
			} else {
				_Logger.warning("Failed to send actuator command to CDA: " + resourceName);
			}
		}
	}
	
	/**
	 * Handles incoming data analysis (e.g., threshold checking).
	 */
	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SensorData data)
	{
		_Logger.fine("Analyzing incoming sensor data: " + data.getName());
	}
	
	/**
	 * Handles upstream transmission of SensorData to cloud service.
	 */
	private void handleUpstreamTransmission(ResourceNameEnum resourceName, SensorData data)
	{
		_Logger.fine("Sending sensor data to cloud service: " + resourceName);
		
		if (this.cloudClient != null && this.enableCloudClient) {
			if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
				_Logger.fine("Sent sensor data upstream to CSP.");
			} else {
				_Logger.warning("Failed to send sensor data to cloud service.");
			}
		}
	}
	
	/**
	 * Handles upstream transmission of SystemPerformanceData to cloud service.
	 */
	private void handleUpstreamTransmission(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		_Logger.fine("Sending system performance data to cloud service: " + resourceName);
		
		if (this.cloudClient != null && this.enableCloudClient) {
			if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
				_Logger.fine("Sent system performance data upstream to CSP.");
			} else {
				_Logger.warning("Failed to send system performance data to cloud service.");
			}
		}
	}
}