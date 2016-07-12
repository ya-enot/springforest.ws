package org.filatov.springforest.ws.service.endpoint.mapping;

import org.springframework.ws.context.MessageContext;

public interface JaxWsMethodEndpoint {

	void execute(MessageContext messageContext) throws Exception;

}
