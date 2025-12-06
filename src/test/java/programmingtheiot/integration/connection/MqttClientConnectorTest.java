package programmingtheiot.integration.connection;

import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.MqttClientConnector;

/**
 * Integration test for MqttClientConnector
 */
public class MqttClientConnectorTest implements IDataMessageListener {
    
    private static final Logger _Logger = Logger.getLogger(MqttClientConnectorTest.class.getName());
    
    private MqttClientConnector mqttClient;
    
    @Before
    public void setUp() {
        this.mqttClient = new MqttClientConnector();
        this.mqttClient.setDataMessageListener(this);
    }
    
    @After
    public void tearDown() {
        // cleanup
    }
    
    @Test
    public void testConnectAndDisconnect() {
        assertTrue(this.mqttClient.connectClient());
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
        
        assertTrue(this.mqttClient.isConnected());
        
        assertTrue(this.mqttClient.disconnectClient());
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
    }
    
    @Test
    public void testActuatorCommandResponseSubscription() {
        int qos = 0;
        
        assertTrue(this.mqttClient.connectClient());
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
        
        ActuatorData ad = new ActuatorData();
        ad.setValue((float) 12.3);
        ad.setAsResponse();
        
        String adJson = DataUtil.getInstance().actuatorDataToJson(ad);
        
        _Logger.info("DEBUG: ActuatorData value before serialization: " + ad.getValue());
        _Logger.info("DEBUG: JSON payload: " + adJson);
        
        assertTrue(this.mqttClient.publishMessage(
            ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, adJson, qos));
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
        
        assertTrue(this.mqttClient.disconnectClient());
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }
    }
    
    // ========== IDataMessageListener Implementation ==========
    
    @Override
    public boolean handleActuatorCommandResponse(ResourceNameEnum resource, ActuatorData data) {
        _Logger.info("Test received ActuatorData response: " + data.getValue());
        return true;
    }
    
    @Override
    public boolean handleSensorMessage(ResourceNameEnum resource, SensorData data) {
        _Logger.info("Test received SensorData: " + data.getValue());
        return true;
    }
    
    @Override
    public boolean handleSystemPerformanceMessage(ResourceNameEnum resource, SystemPerformanceData data) {
        _Logger.info("Test received SystemPerformanceData: CPU=" + data.getCpuUtilization());
        return true;
    }
    
    @Override
    public void setActuatorDataListener(String resourceName, IActuatorDataListener listener) {
        _Logger.info("setActuatorDataListener called with resourceName: " + resourceName);
    }
    
    @Override
    public boolean handleIncomingMessage(ResourceNameEnum resourceName, String jsonMessage) {
        _Logger.info("handleIncomingMessage called with resource: " + resourceName);
        return true;
    }
    
    @Override
    public boolean handleActuatorCommandRequest(ResourceNameEnum resource, ActuatorData data) {
        _Logger.info("handleActuatorCommandRequest called: " + data.getValue());
        return true;
    }
}