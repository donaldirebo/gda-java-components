package programmingtheiot.gda.connection;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * A simple CoapResource that supports GET requests.
 * Returns a basic status response.
 */
public class ReadableCoapResource extends CoapResource
{
	public ReadableCoapResource(String name)
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
}