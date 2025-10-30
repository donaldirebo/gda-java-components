package programmingtheiot.gda.connection;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.IConnectionListener;

/**
 * MQTT client connector for Gateway Device Application.
 * Handles connection to MQTT broker, publishing, and subscribing.
 */
public class MqttClientConnector implements IPubSubClient, MqttCallbackExtended {
    
    private static final Logger _Logger = Logger.getLogger(MqttClientConnector.class.getName());
    
    private MqttClient mqttClient;
    private MqttConnectOptions connOpts;
    private MemoryPersistence persistence;
    private IDataMessageListener dataMsgListener;
    private IConnectionListener connListener;
    
    private String host = ConfigConst.DEFAULT_HOST;
    private int port = ConfigConst.DEFAULT_MQTT_PORT;
    private int brokerKeepAlive = ConfigConst.DEFAULT_KEEP_ALIVE;
    private String clientID;
    private String brokerAddr;
    private String protocol = ConfigConst.DEFAULT_MQTT_PROTOCOL;
    
    /**
     * Constructor - initializes MQTT client configuration from PiotConfig.props
     */
    public MqttClientConnector() {
        ConfigUtil configUtil = ConfigUtil.getInstance();
        
        this.host = configUtil.getProperty(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
        this.port = configUtil.getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_MQTT_PORT);
        this.brokerKeepAlive = configUtil.getInteger(ConfigConst.MQTT_GATEWAY_SERVICE, ConfigConst.KEEP_ALIVE_KEY, ConfigConst.DEFAULT_KEEP_ALIVE);
        
        this.clientID = MqttClient.generateClientId();
        this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;
        
        this.persistence = new MemoryPersistence();
        this.connOpts = new MqttConnectOptions();
        
        this.connOpts.setKeepAliveInterval(this.brokerKeepAlive);
        this.connOpts.setCleanSession(false);
        this.connOpts.setAutomaticReconnect(true);
        
        _Logger.info("MQTT Broker Address: " + this.brokerAddr);
        _Logger.info("MQTT Client ID: " + this.clientID);
    }
    
    /**
     * Connects to the MQTT broker
     */
    @Override
    public boolean connectClient() {
        try {
            if (this.mqttClient == null) {
                this.mqttClient = new MqttClient(this.brokerAddr, this.clientID, this.persistence);
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
            _Logger.log(Level.SEVERE, "Failed to connect MQTT client to broker.", e);
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
    
    /**
     * Publishes a message to a topic
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
        
        if (qos < 0 || qos > 2) {
            qos = ConfigConst.DEFAULT_QOS;
        }
        
        try {
            byte[] payload = msg.getBytes();
            MqttMessage mqttMsg = new MqttMessage(payload);
            mqttMsg.setQos(qos);
            this.mqttClient.publish(topicName.getResourceName(), mqttMsg);
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to publish message to topic: " + topicName, e);
        }
        
        return false;
    }
    
    /**
     * Subscribes to a topic
     */
    @Override
    public boolean subscribeToTopic(ResourceNameEnum topicName, int qos) {
        if (topicName == null) {
            _Logger.warning("Resource is null. Unable to subscribe to topic: " + this.brokerAddr);
            return false;
        }
        
        if (qos < 0 || qos > 2) {
            qos = ConfigConst.DEFAULT_QOS;
        }
        
        try {
            this.mqttClient.subscribe(topicName.getResourceName(), qos);
            _Logger.info("Successfully subscribed to topic: " + topicName.getResourceName());
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to subscribe to topic: " + topicName, e);
        }
        
        return false;
    }
    
    /**
     * Unsubscribes from a topic
     */
    @Override
    public boolean unsubscribeFromTopic(ResourceNameEnum topicName) {
        if (topicName == null) {
            _Logger.warning("Resource is null. Unable to unsubscribe from topic: " + this.brokerAddr);
            return false;
        }
        
        try {
            this.mqttClient.unsubscribe(topicName.getResourceName());
            _Logger.info("Successfully unsubscribed from topic: " + topicName.getResourceName());
            return true;
        } catch (Exception e) {
            _Logger.log(Level.SEVERE, "Failed to unsubscribe from topic: " + topicName, e);
        }
        
        return false;
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
     * Sets the connection listener
     */
    @Override
    public boolean setConnectionListener(IConnectionListener listener) {
        if (listener != null) {
            this.connListener = listener;
            return true;
        }
        return false;
    }
    
    // ========== MqttCallbackExtended Methods ==========
    
    /**
     * Called when connection is complete (initial connection or reconnection)
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        _Logger.info("Successfully connected to MQTT broker: " + serverURI);
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
     * Called when a message arrives from a subscribed topic
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        _Logger.info("MQTT message arrived on topic: '" + topic + "'");
        
        // Forward message to data message listener if set
        if (this.dataMsgListener != null) {
            String payload = new String(message.getPayload());
            // For now, just log that we would forward it
            _Logger.info("Message QoS: " + message.getQos());
        }
    }
}