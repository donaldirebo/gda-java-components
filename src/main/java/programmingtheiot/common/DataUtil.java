package programmingtheiot.common;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Utility class for converting data objects to/from JSON format.
 * Handles ActuatorData, SensorData, and SystemPerformanceData serialization.
 */
public class DataUtil
{
	// static
	
	private static final Logger _Logger = 
		Logger.getLogger(DataUtil.class.getName());
	
	private static final Gson _GsonInstance = 
		new GsonBuilder().setPrettyPrinting().create();
	
	
	// member var's
	
	private boolean encodeToUtf8 = false;
	
	
	// constructors
	
	/**
	 * Initialize DataUtil instance.
	 */
	public DataUtil()
	{
		this(false);
	}
	
	/**
	 * Initialize DataUtil instance.
	 * 
	 * @param encodeToUtf8 Whether to encode JSON to UTF-8
	 */
	public DataUtil(boolean encodeToUtf8)
	{
		this.encodeToUtf8 = encodeToUtf8;
		_Logger.info("Created DataUtil instance.");
	}
	
	
	// public methods - ActuatorData
	
	/**
	 * Convert ActuatorData object to JSON string.
	 * 
	 * @param data The ActuatorData object to convert
	 * @return JSON string representation, or empty string if data is null
	 */
	public String actuatorDataToJson(ActuatorData data)
	{
		if (data == null) {
			_Logger.fine("ActuatorData is null. Returning empty string.");
			return "";
		}
		
		return _generateJsonData(data);
	}
	
	/**
	 * Convert JSON string to ActuatorData object.
	 * 
	 * @param jsonData JSON string to convert
	 * @return Converted ActuatorData object, or null if conversion fails
	 */
	public ActuatorData jsonToActuatorData(String jsonData)
	{
		if (jsonData == null || jsonData.isEmpty()) {
			_Logger.warning("JSON data is empty or null. Returning null.");
			return null;
		}
		
		try {
			ActuatorData ad = _GsonInstance.fromJson(jsonData, ActuatorData.class);
			_Logger.fine("Successfully converted JSON to ActuatorData.");
			return ad;
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to convert JSON to ActuatorData: " + e.getMessage(), e);
			return null;
		}
	}
	
	
	// public methods - SensorData
	
	/**
	 * Convert SensorData object to JSON string.
	 * 
	 * @param data The SensorData object to convert
	 * @return JSON string representation, or empty string if data is null
	 */
	public String sensorDataToJson(SensorData data)
	{
		if (data == null) {
			_Logger.fine("SensorData is null. Returning empty string.");
			return "";
		}
		
		return _generateJsonData(data);
	}
	
	/**
	 * Convert JSON string to SensorData object.
	 * 
	 * @param jsonData JSON string to convert
	 * @return Converted SensorData object, or null if conversion fails
	 */
	public SensorData jsonToSensorData(String jsonData)
	{
		if (jsonData == null || jsonData.isEmpty()) {
			_Logger.warning("JSON data is empty or null. Returning null.");
			return null;
		}
		
		try {
			SensorData sd = _GsonInstance.fromJson(jsonData, SensorData.class);
			_Logger.fine("Successfully converted JSON to SensorData.");
			return sd;
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to convert JSON to SensorData: " + e.getMessage(), e);
			return null;
		}
	}
	
	
	// public methods - SystemPerformanceData
	
	/**
	 * Convert SystemPerformanceData object to JSON string.
	 * 
	 * @param data The SystemPerformanceData object to convert
	 * @return JSON string representation, or empty string if data is null
	 */
	public String systemPerformanceDataToJson(SystemPerformanceData data)
	{
		if (data == null) {
			_Logger.fine("SystemPerformanceData is null. Returning empty string.");
			return "";
		}
		
		return _generateJsonData(data);
	}
	
	/**
	 * Convert JSON string to SystemPerformanceData object.
	 * 
	 * @param jsonData JSON string to convert
	 * @return Converted SystemPerformanceData object, or null if conversion fails
	 */
	public SystemPerformanceData jsonToSystemPerformanceData(String jsonData)
	{
		if (jsonData == null || jsonData.isEmpty()) {
			_Logger.warning("JSON data is empty or null. Returning null.");
			return null;
		}
		
		try {
			SystemPerformanceData spd = _GsonInstance.fromJson(jsonData, SystemPerformanceData.class);
			_Logger.fine("Successfully converted JSON to SystemPerformanceData.");
			return spd;
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to convert JSON to SystemPerformanceData: " + e.getMessage(), e);
			return null;
		}
	}
	
	
	// public methods - SystemStateData (Optional - Lab 5)
	
	/**
	 * Convert SystemStateData object to JSON string.
	 * NOTE: SystemStateData is optional for Lab 5.
	 * 
	 * @param data The SystemStateData object to convert
	 * @return Empty string (stub implementation)
	 */
	public String systemStateDataToJson(Object data)
	{
		if (data == null) {
			_Logger.fine("SystemStateData is null. Returning empty string.");
			return "";
		}
		_Logger.fine("SystemStateData JSON conversion not implemented (optional for Lab 5).");
		return "";
	}
	
	/**
	 * Convert JSON string to SystemStateData object.
	 * NOTE: SystemStateData is optional for Lab 5.
	 * 
	 * @param jsonData JSON string to convert
	 * @return Null (stub implementation)
	 */
	public Object jsonToSystemStateData(String jsonData)
	{
		if (jsonData == null || jsonData.isEmpty()) {
			_Logger.fine("JSON data is empty. Returning null.");
			return null;
		}
		_Logger.fine("SystemStateData JSON conversion not implemented (optional for Lab 5).");
		return null;
	}
	
	
	// private methods
	
	/**
	 * Convert object to JSON string representation using Gson.
	 * 
	 * @param obj Object to convert
	 * @return JSON string
	 */
	private String _generateJsonData(Object obj)
	{
		String jsonData = null;
		
		try {
			jsonData = _GsonInstance.toJson(obj);
			
			if (encodeToUtf8) {
				return new String(jsonData.getBytes("UTF-8"), "UTF-8");
			}
			
			return jsonData;
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to generate JSON data: " + e.getMessage(), e);
			return "";
		}
	}
}cd