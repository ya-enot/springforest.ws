package org.filatov.springforest.ws.service.endpoint.mapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;
import org.springframework.ws.context.MessageContext;

class JaxWsMethodEndpointExecutor implements JaxWsMethodEndpoint {

	public static class EndpointAddress {

		private final ApplicationContext applicationContext;
		private final String name;
		private final Class<?> type;

		public EndpointAddress(ApplicationContext applicationContext, String name, Class<?> type) {
			this.applicationContext = applicationContext;
			this.name = name;
			this.type = type;
		}
	}

	private final Method method;
	private final EndpointAddress address;

	public JaxWsMethodEndpointExecutor(EndpointAddress address, Method method) {
		this.address = address;
		this.method = method;
	}

	@Override
	public String toString() {
		return "JaxWsMethodEndpoint [method=" + method + "]";
	}

	public void execute(MessageContext messageContext) throws Exception {
		System.err.println(messageContext);
		Object beanObject = address.applicationContext.getBean(address.name, address.type);
		Object[] args = new Object[method.getParameterTypes().length];
		Object result = method.invoke(beanObject, args);
		System.out.println(result);
		messageContext.getResponse().getPayloadResult();
	}

}