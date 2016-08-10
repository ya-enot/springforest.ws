package org.filatov.springforest.ws.service.endpoint.mapping;

import org.springframework.ws.context.MessageContext;

public interface JaxWsMethodEndpoint<T> {

	void execute(MessageContext messageContext, T settings) throws Exception;

}
