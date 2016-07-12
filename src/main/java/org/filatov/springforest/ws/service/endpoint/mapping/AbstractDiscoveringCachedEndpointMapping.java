package org.filatov.springforest.ws.service.endpoint.mapping;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public abstract class AbstractDiscoveringCachedEndpointMapping<T, E> extends AbstractCachedEndpointMapping<T, E> {

	@Override
	protected void initApplicationContext() throws BeansException {
		super.initApplicationContext();

		List<String> beanNames = new ArrayList<String>();

		ApplicationContext applicationContext = getApplicationContext();

		if (logger.isDebugEnabled()) {
			logger.debug("Looking for endpoints in application context: " + applicationContext);
		}

		Iterable<String> namesIterator = discoverService(applicationContext);
		for (String name : namesIterator) {
			beanNames.add(name);
		}

		if (logger.isTraceEnabled()) {
			for (String name : beanNames) {
				logger.trace("Found endpoint: " + name);
			}
		}

		for (String name : beanNames) {
			Class<?> endpointClass = applicationContext.getType(name);
			registerService(applicationContext, name, endpointClass);
		}
	}

	protected abstract void registerService(ApplicationContext applicationContext, String name, Class<?> endpointClass);

	public abstract Iterable<String> discoverService(ApplicationContext applicationContext);
}
