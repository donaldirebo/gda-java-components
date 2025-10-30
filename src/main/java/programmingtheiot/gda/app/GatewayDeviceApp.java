package programmingtheiot.gda.app;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.*;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * Main GDA application.
 * 
 */
public class GatewayDeviceApp
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(GatewayDeviceApp.class.getName());
	
	public static final long DEFAULT_TEST_RUNTIME = 60000L;
	
	// private var's
	
	private String configFile = ConfigConst.DEFAULT_CONFIG_FILE_NAME;
	private SystemPerformanceManager sysPerfMgr = null;
	private DeviceDataManager deviceDataManager = null;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public GatewayDeviceApp()
	{
		super();
		
		_Logger.info("Initializing GDA...");
		
		this.sysPerfMgr = new SystemPerformanceManager();
		this.deviceDataManager = new DeviceDataManager();
	}
	
	/**
	 * Constructor with arguments.
	 * 
	 * @param args Command line arguments
	 */
	public GatewayDeviceApp(String[] args)
	{
		super();
		
		_Logger.info("Initializing GDA...");
		
		this.sysPerfMgr = new SystemPerformanceManager();
		this.deviceDataManager = new DeviceDataManager();
		
		Map<String, String> argMap = parseArgs(args);
		if (argMap.containsKey(ConfigConst.CONFIG_FILE_KEY)) {
			System.setProperty(ConfigConst.CONFIG_FILE_KEY, argMap.get(ConfigConst.CONFIG_FILE_KEY));
		}
	}
	
	
	// static
	
	/**
	 * Main application entry point.
	 * 
	 * @param args
	 */
	public static void main(String[] args)
	{
		GatewayDeviceApp gwApp = new GatewayDeviceApp(args);
		
		gwApp.startApp();
		
		try {
			Thread.sleep(65000L);
		} catch (InterruptedException e) {
			// ignore
		}
		
		gwApp.stopApp(0);
	}
	
	/**
	 * Parse any arguments passed in on app startup.
	 * <p>
	 * This method should be written to check if any valid command line args are provided,
	 * including the name of the config file. Once parsed, call {@link #initConfig(String)}
	 * with the name of the config file, or null if the default should be used.
	 * <p>
	 * If any command line args conflict with the config file, the config file
	 * in-memory content should be overridden with the command line argument(s).
	 * 
	 * @param args The non-null and non-empty args array.
	 */
	private static Map<String, String> parseArgs(String[] args)
	{
		Map<String, String> map = new HashMap<>();
		
		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; ++i) {
				String arg = args[i];
				
				if (arg.equals(ConfigConst.CONFIG_FILE_KEY)) {
					if (i + 1 < args.length) {
						map.put(ConfigConst.CONFIG_FILE_KEY, args[i + 1]);
						++i;
					}
				}
			}
		}
		
		return map;
	}
	
	/**
	 * Starts the application.
	 * 
	 */
	public void startApp()
	{
		_Logger.info("Starting GDA...");
		
		try {
			this.sysPerfMgr.startManager();
			_Logger.info("SystemPerformanceManager started.");
			
			this.deviceDataManager.startManager();
			_Logger.info("DeviceDataManager started.");
			
			_Logger.info("GDA started successfully.");
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start GDA. Exiting.", e);
			stopApp(-1);
		}
	}
	
	/**
	 * Stops the application.
	 * 
	 * @param code The exit code to pass to {@link System.exit()}
	 */
	public void stopApp(int code)
	{
		_Logger.info("Stopping GDA...");
		
		try {
			this.deviceDataManager.stopManager();
			_Logger.info("DeviceDataManager stopped.");
			
			this.sysPerfMgr.stopManager();
			_Logger.log(Level.INFO, "GDA stopped successfully with exit code {0}.", code);
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to cleanly stop GDA. Exiting.", e);
		}
		
		// Only exit if code is non-zero (error condition)
		if (code != 0) {
			System.exit(code);
		}
	}
	
	
	// private methods
	
}