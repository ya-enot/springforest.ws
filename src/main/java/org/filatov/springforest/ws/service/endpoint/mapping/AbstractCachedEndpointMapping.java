package org.filatov.springforest.ws.service.endpoint.mapping;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.transform.TransformerException;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.mapping.AbstractEndpointMapping;

public abstract class AbstractCachedEndpointMapping<T, E> extends AbstractEndpointMapping {

	private final Map<T, E> endpointMap = new ConcurrentHashMap<T, E>();

	@Override
	protected E getEndpointInternal(MessageContext messageContext) throws Exception {
		T key = getEnpointKey(messageContext);
		if (key == null) {
			return null;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up endpoint for [" + key + "]");
		}
		return lookupEndpoint(key);
	}

	private E lookupEndpoint(T key) {
		return endpointMap.get(key);
	}

	protected abstract T getEnpointKey(MessageContext messageContext) throws TransformerException;

	protected E registerEndpoint(T key, E endpoint) {
		return endpointMap.put(key, endpoint);
	}

}
