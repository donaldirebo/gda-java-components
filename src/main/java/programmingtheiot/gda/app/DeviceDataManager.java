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
import programmingtheiot.gda.connection.CoapServerGateway;
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
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private IPubSubClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
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
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)
	{
		super();
		
		this.configUtil = ConfigUtil.getInstance();
		this.dataUtil = DataUtil.getInstance();
		
		this.enableMqttClient = enableMqttClient;
		this.enableCoapServer = enableCoapClient;
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
		_Logger.log(Level.INFO, "Handling actuator command request: " + resourceName);
		
		if (data != null) {
			// TODO: Forward to appropriate actuator handler
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		_Logger.log(Level.INFO, "Handling incoming message on: " + resourceName);
		
		if (msg != null) {
			// TODO: In Part IV, parse and route messages
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		_Logger.log(Level.FINE, "Handling sensor message: " + resourceName);
		
		if (data != null) {
			// TODO: In Part IV, convert to JSON and publish upstream
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		_Logger.log(Level.FINE, "Handling system performance message: " + resourceName);
		
		if (data != null) {
			// TODO: In Part IV, convert to JSON and publish upstream
			return true;
		}
		
		return false;
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
		
		if (this.enableMqttClient && this.mqttClient != null) {
			this.mqttClient.connectClient();
			
			// Subscribe to CDA sensor data topic
			this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, ConfigConst.DEFAULT_QOS);
			
			// Subscribe to CDA system performance data topic
			this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, ConfigConst.DEFAULT_QOS);
			
			// Subscribe to CDA actuator response topic
			this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, ConfigConst.DEFAULT_QOS);
			
			_Logger.log(Level.INFO, "MQTT client started and subscribed to CDA topics.");
		}
		
		// TODO: Start other managers (CoAP server, cloud client, persistence client, etc.)
		
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
			this.mqttClient.disconnectClient();
			
			_Logger.log(Level.INFO, "MQTT client stopped.");
		}
		
		// TODO: Stop other managers (CoAP server, cloud client, persistence client, etc.)
		
		_Logger.log(Level.INFO, "DeviceDataManager stopped.");
	}
	
	// private methods
	
	/**
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager()} and {@link #stopManager()} methods.
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
		
		// TODO: Initialize other connections (CoAP, Cloud, SMTP, Persistence)
		
		_Logger.log(Level.INFO, "DeviceDataManager connections initialized.");
	}
}