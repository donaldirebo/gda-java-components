package programmingtheiot.gda.connection;

import java.io.File;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.common.SimpleCertManagementUtil;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * MQTT client connector for Gateway Device Application.
 * Handles connection to MQTT broker, publishing, and subscribing.
 * Uses MqttAsyncClient for non-blocking operations.
 * Supports both local MQTT broker and cloud gateway configurations.
 */
public class MqttClientConnector implements IPubSubClient, MqttCallbackExtended {
    
    private static final Logger _Logger = Logger.getLogger(MqttClientConnector.class.getName());
    
    // NOTE: MQTT client updated to use async client vs sync client
    private MqttAsyncClient mqttClient = null;
    private MqttConnectOptions connOpts;
    private MemoryPersistence persistence;
    private IDataMessageListener dataMsgListener;
    private IConnectionListener connListener = null;
    
    // NEW: Flag to indicate if using cloud gateway configuration
    private boolean useCloudGatewayConfig = false;
    
    private String host = ConfigConst.DEFAULT_HOST;
    private int port = ConfigConst.DEFAULT_MQTT_PORT;
    private int brokerKeepAlive = ConfigConst.DEFAULT_KEEP_ALIVE;
    private String clientID;
    private String brokerAddr;
    private String protocol = ConfigConst.DEFAULT_MQTT_PROTOCOL;
    
    // TLS/Credentials support
    private String pemFileName = null;
    private boolean enableEncryption = false;
    private boolean useCleanSession = false;
    private boolean enableAutoReconnect = true;
    private boolean useAsyncClient = true;
    
    /**
     * Default constructor - uses local MQTT gateway configuration
     */
    public MqttClientConnector() {
        this(false);
    }
    
    /**
     * Constructor with cloud gateway option
     * @param useCloudGatewayConfig If true, use Cloud.GatewayService config section
     */
    public MqttClientConnector(boolean useCloudGatewayConfig) {
        this(useCloudGatewayConfig ? ConfigConst.CLOUD_GATEWAY_SERVICE : null);
    }
    
    /**
     * Constructor with custom configuration section name
     * @param cloudGatewayConfigSectionName Config section name to use, or null for default MQTT config
     */
    public MqttClientConnector(String cloudGatewayConfigSectionName) {
        super();
        
        if (cloudGatewayConfigSectionName != null && cloudGatewayConfigSectionName.trim().length() > 0) {
            this.useCloudGatewayConfig = true;
            initClientParameters(cloudGatewayConfigSectionName);
        } else {
            this.useCloudGatewayConfig = false;
            initClientParameters(ConfigConst.MQTT_GATEWAY_SERVICE);
        }
    }
    
    /**
     * Initialize client parameters from configuration file
     */
    private void initClientParameters(String configSectionName) {
        ConfigUtil configUtil = ConfigUtil.getInstance();
        
        this.host = configUtil.getProperty(
            configSectionName, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
        this.port = configUtil.getInteger(
            configSectionName, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_MQTT_PORT);
        this.brokerKeepAlive = configUtil.getInteger(
            configSectionName, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
        this.enableEncryption = configUtil.getBoolean(
            configSectionName, ConfigConst.ENABLE_CRYPT_KEY);
        this.pemFileName = configUtil.getProperty(
            configSectionName, ConfigConst.CERT_FILE_KEY);
        
        // Load async client preference from config
        this.useAsyncClient = configUtil.getBoolean(
            configSectionName, ConfigConst.USE_ASYNC_CLIENT_KEY);
        
        // Load client ID from configuration or generate
        this.clientID = configUtil.getProperty(
            ConfigConst.GATEWAY_DEVICE, ConfigConst.DEVICE_LOCATION_ID_KEY, 
            MqttAsyncClient.generateClientId());
        
        // Initialize MQTT connection options
        this.persistence = new MemoryPersistence();
        this.connOpts = new MqttConnectOptions();
        
        this.connOpts.setKeepAliveInterval(this.brokerKeepAlive);
        this.connOpts.setCleanSession(this.useCleanSession);
        this.connOpts.setAutomaticReconnect(this.enableAutoReconnect);
        
        // If encryption is enabled, try to load and apply the cert(s)
        if (this.enableEncryption) {
            initSecureConnectionParameters(configSectionName);
        }
        
        // If there's a credential file, try to load and apply them
        if (configUtil.hasProperty(configSectionName, ConfigConst.CRED_FILE_KEY)) {
            initCredentialConnectionParameters(configSectionName);
        }
        
        // Construct the broker address URL
        this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;
        
        _Logger.info("MQTT Broker Address: " + this.brokerAddr);
        _Logger.info("MQTT Client ID: " + this.clientID);
        _Logger.info("Using Cloud Gateway Config: " + this.useCloudGatewayConfig);
    }
    
    /**
     * Initialize credential connection parameters
     */
    private void initCredentialConnectionParameters(String configSectionName) {
        ConfigUtil configUtil = ConfigUtil.getInstance();
        
        try {
            _Logger.info("Checking if credentials file exists and is loadable...");
            
            Properties props = configUtil.getCredentials(configSectionName);
            
            if (props != null) {
                this.connOpts.setUserName(props.getProperty(ConfigConst.USER_NAME_TOKEN_KEY, ""));
                this.connOpts.setPassword(props.getProperty(ConfigConst.USER_AUTH_TOKEN_KEY, "").toCharArray());
                
                _Logger.info("Credentials now set.");
            } else {
                _Logger.warning("No credentials are set.");
            }
        } catch (Exception e) {
            _Logger.log(Level.WARNING, "Credential file non-existent. Disabling auth requirement.");
        }
    }
    
    /**
     * Initialize secure connection parameters (TLS)
     */
    private void initSecureConnectionParameters(String configSectionName) {
        ConfigUtil configUtil = ConfigUtil.getInstance();
        
        try {
            _Logger.info("Configuring TLS...");
            
            if (this.pemFileName != null) {
                File file = new File(this.pemFileName);
                
                if (file.exists()) {
                    _Logger.info("PEM file valid. Using secure connection: " + this.pemFileName);
                } else {
                    this.enableEncryption = false;
                    _Logger.log(Level.WARNING, "PEM file invalid. Using insecure connection: " + this.pemFileName, new Exception());
                    return;
                }
            }
            
            SSLSocketFactory sslFactory = SimpleCertManagementUtil.getInstance().loadCertificate(this.pemFileName);
            this.connOpts.setSocketFactory(sslFactory);
            
            // Override current config parameters
            this.port = configUtil.getInteger(
                configSectionName, ConfigConst.SECURE_PORT_KEY, ConfigConst.DEFAULT_MQTT_SECURE_PORT);
            this.protocol = ConfigConst.DEFAULT_MQTT_SECURE_PROTOCOL;
            
            _Logger.info("TLS enabled.");
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to initialize secure MQTT connection. Using insecure connection.", e);
            this.enableEncryption = false;
        }
    }
    
    /**
     * Connects to the MQTT broker using MqttAsyncClient
     */
    @Override
    public boolean connectClient() {
        try {
            if (this.mqttClient == null) {
                this.mqttClient = new MqttAsyncClient(this.brokerAddr, this.clientID, this.persistence);
                this.mqttClient.setCallback(this);
            }
            
            if (!this.mqttClient.isConnected()) {
                _Logger.info("MQTT client connecting to broker: " + this.brokerAddr);
                this.mqttClient.connect(this.connOpts);
                return true;
            } else {
                _Logger.warning("MQTT client already connected to broker: " + this.brokerAddr);
            }
        } catch (MqttException e) {
            _Logger.log(Level.SEVERE, "Failed to connect MQTT client to broker: " + this.brokerAddr, e);
        }
        
        return false;
    }
    
    /**
     * Disconnects from the MQTT broker
     */
    @Override
    public boolean disconnectClient() {
        try {
            if (this.mqttClient != null) {
                if (this.mqttClient.isConnected()) {
                    _Logger.info("Disconnecting MQTT client from broker: " + this.brokerAddr);
                    this.mqttClient.disconnect();
                    return true;
                } else {
                    _Logger.warning("MQTT client not connected to broker: " + this.brokerAddr);
                }
            }
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to disconnect MQTT client from broker: " + this.brokerAddr, e);
        }
        
        return false;
    }
    
    // ========== Protected Methods for Subclass/Package Access ==========
    
    /**
     * Protected publish method using String topic name
     * Allows subclasses to publish with custom topic naming conventions
     */
    protected boolean publishMessage(String topicName, byte[] payload, int qos) {
        if (topicName == null) {
            _Logger.warning("Topic is null. Unable to publish message: " + this.brokerAddr);
            return false;
        }
        
        if (payload == null || payload.length == 0) {
            _Logger.warning("Message is null or empty. Unable to publish message: " + this.brokerAddr);
            return false;
        }
        
        if (qos < 0 || qos > 2) {
            _Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
            qos = ConfigConst.DEFAULT_QOS;
        }
        
        try {
            MqttMessage mqttMsg = new MqttMessage();
            mqttMsg.setQos(qos);
            mqttMsg.setPayload(payload);
            
            this.mqttClient.publish(topicName, mqttMsg);
            
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to publish message to topic: " + topicName, e);
        }
        
        return false;
    }
    
    /**
     * Protected subscribe method using String topic name
     */
    protected boolean subscribeToTopic(String topicName, int qos) {
        return subscribeToTopic(topicName, qos, null);
    }
    
    /**
     * Protected subscribe method with message listener
     * Allows subclasses to subscribe with custom topic naming conventions
     */
    protected boolean subscribeToTopic(String topicName, int qos, IMqttMessageListener listener) {
        if (topicName == null) {
            _Logger.warning("Topic is null. Unable to subscribe to topic: " + this.brokerAddr);
            return false;
        }
        
        if (qos < 0 || qos > 2) {
            _Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
            qos = ConfigConst.DEFAULT_QOS;
        }
        
        try {
            if (listener != null) {
                this.mqttClient.subscribe(topicName, qos, listener);
                _Logger.info("Successfully subscribed to topic with listener: " + topicName);
            } else {
                this.mqttClient.subscribe(topicName, qos);
                _Logger.info("Successfully subscribed to topic: " + topicName);
            }
            
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to subscribe to topic: " + topicName, e);
        }
        
        return false;
    }
    
    /**
     * Protected unsubscribe method using String topic name
     * Allows subclasses to unsubscribe with custom topic naming conventions
     */
    protected boolean unsubscribeFromTopic(String topicName) {
        if (topicName == null) {
            _Logger.warning("Topic is null. Unable to unsubscribe from topic: " + this.brokerAddr);
            return false;
        }
        
        try {
            this.mqttClient.unsubscribe(topicName);
            _Logger.info("Successfully unsubscribed from topic: " + topicName);
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to unsubscribe from topic: " + topicName, e);
        }
        
        return false;
    }
    
    // ========== Public Interface Methods ==========
    
    /**
     * Publishes a message to a topic (public interface)
     */
    @Override
    public boolean publishMessage(ResourceNameEnum topicName, String msg, int qos) {
        if (topicName == null) {
            _Logger.warning("Resource is null. Unable to publish message: " + this.brokerAddr);
            return false;
        }
        
        if (msg == null || msg.length() == 0) {
            _Logger.warning("Message is null or empty. Unable to publish message: " + this.brokerAddr);
            return false;
        }
        
        return publishMessage(topicName.getResourceName(), msg.getBytes(), qos);
    }
    
    /**
     * Subscribes to a topic (public interface)
     */
    @Override
    public boolean subscribeToTopic(ResourceNameEnum topicName, int qos) {
        if (topicName == null) {
            _Logger.warning("Resource is null. Unable to subscribe to topic: " + this.brokerAddr);
            return false;
        }
        
        return subscribeToTopic(topicName.getResourceName(), qos);
    }
    
    /**
     * Unsubscribes from a topic (public interface)
     */
    @Override
    public boolean unsubscribeFromTopic(ResourceNameEnum topicName) {
        if (topicName == null) {
            _Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);
            return false;
        }
        
        return unsubscribeFromTopic(topicName.getResourceName());
    }
    
    /**
     * Checks if the client is currently connected to the broker
     */
    public boolean isConnected() {
        return (this.mqttClient != null && this.mqttClient.isConnected());
    }
    
    /**
     * Sets the data message listener
     */
    @Override
    public boolean setDataMessageListener(IDataMessageListener listener) {
        if (listener != null) {
            this.dataMsgListener = listener;
            return true;
        }
        return false;
    }
    
    /**
     * Sets the connection listener for connect/disconnect notifications
     */
    @Override
    public boolean setConnectionListener(IConnectionListener listener) {
        if (listener != null) {
            _Logger.info("Setting connection listener.");
            this.connListener = listener;
            return true;
        } else {
            _Logger.warning("No connection listener specified. Ignoring.");
        }
        return false;
    }
    
    // ========== MqttCallbackExtended Methods ==========
    
    /**
     * Called when connection is complete (initial connection or reconnection)
     * Only subscribes to local CDA topics if NOT using cloud gateway config
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        _Logger.info("MQTT connection successful (is reconnect = " + reconnect + "). Broker: " + serverURI);
        
        int qos = 1;
        
        // Only subscribe to local CDA topics if NOT using cloud gateway configuration
        if (!this.useCloudGatewayConfig) {
            try {
                // Subscribe to actuator response messages with dedicated listener
                _Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE.getResourceName());
                this.subscribeToTopic(
                    ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE.getResourceName(),
                    qos,
                    new ActuatorResponseMessageListener(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, this.dataMsgListener));
                
                // Subscribe to sensor messages with dedicated listener
                _Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceName());
                this.subscribeToTopic(
                    ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceName(),
                    qos,
                    new SensorDataMessageListener(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, this.dataMsgListener));
                
                // Subscribe to system performance messages with dedicated listener
                _Logger.info("Subscribing to topic: " + ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName());
                this.subscribeToTopic(
                    ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName(),
                    qos,
                    new SystemPerformanceDataMessageListener(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, this.dataMsgListener));
            } catch (Exception e) {
                _Logger.log(Level.WARNING, "Failed to subscribe to CDA topics.", e);
            }
        }
        
        // Notify connection listener that connection is complete
        // This is important for CloudClientConnector to know when MQTT connection is ready
        if (this.connListener != null) {
            this.connListener.onConnect();
        }
    }
    
    /**
     * Called when connection to broker is lost
     */
    @Override
    public void connectionLost(Throwable t) {
        _Logger.log(Level.WARNING, "Lost connection to MQTT broker: " + this.brokerAddr, t);
    }
    
    /**
     * Called when message delivery is complete
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        _Logger.info("Delivered MQTT message with ID: " + token.getMessageId());
    }
    
    /**
     * Called when a message arrives from a subscribed topic (for topics without specific listeners)
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        _Logger.info("MQTT message arrived on topic: '" + topic + "'");
    }
    
    // ========== Inner Classes for Topic-Specific Message Listeners ==========
    
    /**
     * Inner class to handle ActuatorData response messages
     */
    private class ActuatorResponseMessageListener implements IMqttMessageListener {
        private ResourceNameEnum resource = null;
        private IDataMessageListener dataMsgListener = null;
        
        ActuatorResponseMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener) {
            this.resource = resource;
            this.dataMsgListener = dataMsgListener;
        }
        
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            try {
                ActuatorData actuatorData = DataUtil.getInstance().jsonToActuatorData(
                    new String(message.getPayload()));
                
                _Logger.info("Received ActuatorData response: " + actuatorData.getValue());
                
                if (this.dataMsgListener != null) {
                    this.dataMsgListener.handleActuatorCommandResponse(this.resource, actuatorData);
                }
            } catch (Exception e) {
                _Logger.log(Level.WARNING, "Failed to convert message payload to ActuatorData.", e);
            }
        }
    }
    
    /**
     * Inner class to handle SensorData messages
     */
    private class SensorDataMessageListener implements IMqttMessageListener {
        private ResourceNameEnum resource = null;
        private IDataMessageListener dataMsgListener = null;
        
        SensorDataMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener) {
            this.resource = resource;
            this.dataMsgListener = dataMsgListener;
        }
        
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            try {
                SensorData sensorData = DataUtil.getInstance().jsonToSensorData(
                    new String(message.getPayload()));
                
                _Logger.info("Received SensorData: " + sensorData.getValue());
                
                if (this.dataMsgListener != null) {
                    this.dataMsgListener.handleSensorMessage(this.resource, sensorData);
                }
            } catch (Exception e) {
                _Logger.log(Level.WARNING, "Failed to convert message payload to SensorData.", e);
            }
        }
    }
    
    /**
     * Inner class to handle SystemPerformanceData messages
     */
    private class SystemPerformanceDataMessageListener implements IMqttMessageListener {
        private ResourceNameEnum resource = null;
        private IDataMessageListener dataMsgListener = null;
        
        SystemPerformanceDataMessageListener(ResourceNameEnum resource, IDataMessageListener dataMsgListener) {
            this.resource = resource;
            this.dataMsgListener = dataMsgListener;
        }
        
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            try {
                SystemPerformanceData sysPerfData = DataUtil.getInstance().jsonToSystemPerformanceData(
                    new String(message.getPayload()));
                
                _Logger.info("Received SystemPerformanceData: " + sysPerfData.getCpuUtilization());
                
                if (this.dataMsgListener != null) {
                    this.dataMsgListener.handleSystemPerformanceMessage(this.resource, sysPerfData);
                }
            } catch (Exception e) {
                _Logger.log(Level.WARNING, "Failed to convert message payload to SystemPerformanceData.", e);
            }
        }
    }
}