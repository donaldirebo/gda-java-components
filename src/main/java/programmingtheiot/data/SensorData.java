/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */
package programmingtheiot.data;

import java.io.Serializable;
import programmingtheiot.common.ConfigConst;

/**
 * SensorData class for storing sensor telemetry data.
 */
public class SensorData extends BaseIotData implements Serializable
{
	// private var's
	private float value = ConfigConst.DEFAULT_VAL;
    
	// constructors
	
	public SensorData()
	{
		super();
	}
	
	public SensorData(int sensorType)
	{
		super();
		this.setTypeID(sensorType);
	}
	
	// public methods
	
	public float getValue()
	{
		return this.value;
	}
	
	public void setValue(float val)
	{
		this.value = val;
	}
	
	/**
	 * Returns a string representation of this instance.
	 * 
	 * @return String The string representing this instance, returned in CSV 'key=value' format.
	 */
	public String toString()
	{
		StringBuilder sb = new StringBuilder(super.toString());
		
		sb.append(',');
		sb.append(ConfigConst.VALUE_PROP).append('=').append(this.getValue());
		
		return sb.toString();
	}
	
	// protected methods
	
	protected void handleUpdateData(BaseIotData data)
	{
		if (data instanceof SensorData) {
			SensorData sData = (SensorData) data;
			this.setValue(sData.getValue());
		}
	}
}
