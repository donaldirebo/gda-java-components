package programmingtheiot.gda.connection;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A CoapResource that supports both GET and PUT requests.
 * Accepts payload data via PUT and returns resource data via GET.
 */
public class WritableCoapResource extends CoapResource
{
	private static final Logger _Logger = Logger.getLogger(WritableCoapResource.class.getName());
	
	public WritableCoapResource(String name)
	{
		super(name);
		setObservable(true);
	}
	
	@Override
	public void handleGET(CoapExchange exchange)
	{
		// Return a simple 2.05 Content response with resource name
		String response = "{\"resource\":\"" + this.getName() + "\",\"status\":\"ok\"}";
		exchange.respond(ResponseCode.CONTENT, response);
	}
	
	@Override
	public void handlePUT(CoapExchange exchange)
	{
		try {
			// Get the payload
			byte[] payload = exchange.getRequestPayload();
			String payloadText = new String(payload);
			
			_Logger.info("PUT request received for resource: " + this.getName());
			_Logger.info("PUT payload: " + payloadText);
			
			// Return a success response
			String response = "{\"resource\":\"" + this.getName() + "\",\"status\":\"updated\",\"received\":true}";
			exchange.respond(ResponseCode.CHANGED, response);
			
			_Logger.info("PUT response sent with code: 2.04 (CHANGED)");
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error handling PUT request", e);
			exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, "Error processing PUT request");
		}
	}
}