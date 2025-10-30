// Update DeviceDataManager.java with Redis persistence integration

package programmingtheiot.gda.app;

import java.util.logging.Logger;
import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * DeviceDataManager coordinates all manager implementations
 * and is responsible for managing their lifecycle and message routing.
 */
public class DeviceDataManager implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger = 
		Logger.getLogger(DeviceDataManager.class.getName());
	
	
	// private var's
	
	private SystemPerformanceManager sysPerfManager = null;
	private RedisPersistenceAdapter redisClient = null;
	
	
	// constructors
	
	/**
	 * Default constructor.
	 */
	public DeviceDataManager()
	{
		super();
		
		this.sysPerfManager = new SystemPerformanceManager();
		this.sysPerfManager.setDataMessageListener(this);
		
		// Initialize Redis persistence adapter
		this.redisClient = new RedisPersistenceAdapter();
	}
	
	
	// public methods
	
	/**
	 * Starts all manager instances.
	 * 
	 * @return boolean - True if all managers started successfully, false otherwise.
	 */
	public boolean startManager()
	{
		_Logger.info("Starting DeviceDataManager...");
		
		boolean success = true;
		
		// Connect Redis client
		if (this.redisClient != null) {
			if (!this.redisClient.connectClient()) {
				_Logger.warning("Failed to connect Redis client");
				success = false;
			} else {
				_Logger.info("Redis client connected successfully");
			}
		}
		
		// Start SystemPerformanceManager
		if (this.sysPerfManager != null) {
			if (!this.sysPerfManager.startManager()) {
				_Logger.warning("Failed to start SystemPerformanceManager");
				success = false;
			}
		}
		
		return success;
	}
	
	/**
	 * Stops all manager instances.
	 * 
	 * @return boolean - True if all managers stopped successfully, false otherwise.
	 */
	public boolean stopManager()
	{
		_Logger.info("Stopping DeviceDataManager...");
		
		boolean success = true;
		
		// Stop SystemPerformanceManager
		if (this.sysPerfManager != null) {
			if (!this.sysPerfManager.stopManager()) {
				_Logger.warning("Failed to stop SystemPerformanceManager");
				success = false;
			}
		}
		
		// Disconnect Redis client
		if (this.redisClient != null) {
			if (!this.redisClient.disconnectClient()) {
				_Logger.warning("Failed to disconnect Redis client");
				success = false;
			} else {
				_Logger.info("Redis client disconnected successfully");
			}
		}
		
		return success;
	}
	
	
	// IDataMessageListener methods
	
	@Override
	public void handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		_Logger.info("DeviceDataManager received actuator response: " + resourceName);
		
		if (data != null) {
			_Logger.fine("Actuator data: " + data.toString());
			
			// Write to Redis if client is active
			if (this.redisClient != null && this.redisClient.isConnected()) {
				this.redisClient.writeData(resourceName, data);
				_Logger.fine("Actuator response persisted to Redis");
			}
		}
	}
	
	@Override
	public void handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		_Logger.info("DeviceDataManager received sensor message: " + resourceName);
		
		if (data != null) {
			_Logger.fine("Sensor data: " + data.toString());
			
			// Write to Redis if client is active
			if (this.redisClient != null && this.redisClient.isConnected()) {
				this.redisClient.writeData(resourceName, data);
				_Logger.fine("Sensor data persisted to Redis");
			}
		}
	}
	
	@Override
	public void handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		_Logger.info("DeviceDataManager received system performance message: " + resourceName);
		
		if (data != null) {
			_Logger.fine("System performance data: " + data.toString());
			
			// Write to Redis if client is active
			if (this.redisClient != null && this.redisClient.isConnected()) {
				this.redisClient.writeData(resourceName, data);
				_Logger.fine("System performance data persisted to Redis");
			}
		}
	}
}