package org.filatov.springforest.ws.service.endpoint.mapping;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointAdapter;
import org.springframework.xml.transform.TransformerObjectSupport;

public class JaxWsMethodEndpointAdapter extends TransformerObjectSupport implements EndpointAdapter {

	public boolean supports(Object endpoint) {
		return JaxWsMethodEndpoint.class.isAssignableFrom(endpoint.getClass());
	}

	public void invoke(MessageContext messageContext, Object endpoint) throws Exception {
		if (JaxWsMethodEndpoint.class.isAssignableFrom(endpoint.getClass())) {
			JaxWsMethodEndpoint methodEndpoint = (JaxWsMethodEndpoint) endpoint;
			if (logger.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Invoking endpoint: ");
				sb.append(methodEndpoint);
				sb.append(" with message ");
				sb.append(messageContext);
				logger.debug(sb.toString());
			}
			methodEndpoint.execute(messageContext);
		} else {
			throw new RuntimeException("Unsupported endpoint: " + endpoint);
		}
	}

}
