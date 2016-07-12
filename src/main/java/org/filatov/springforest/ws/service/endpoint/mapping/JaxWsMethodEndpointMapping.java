package org.filatov.springforest.ws.service.endpoint.mapping;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.jws.WebMethod;

import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.support.PayloadRootUtils;
import org.springframework.ws.soap.SoapMessage;

public class JaxWsMethodEndpointMapping
		extends AbstractDiscoveringCachedEndpointMapping<SoapAction, JaxWsMethodEndpoint> {

	private static final String TRIM_CHARS = "\n\t' \"";
	private static TransformerFactory transformerFactory;
	static {
		transformerFactory = TransformerFactory.newInstance();
	}

	@Override
	protected SoapAction getEnpointKey(MessageContext messageContext) throws TransformerException {

		WebServiceMessage message = messageContext.getRequest();
		if (SoapMessage.class.isAssignableFrom(message.getClass())) {
			return new SoapAction(((SoapMessage) message).getSoapAction()
					.replaceAll("^[" + TRIM_CHARS + "]|[" + TRIM_CHARS + "]$", ""));
		}
		return null;
	}

	@Override
	public Iterable<String> discoverService(ApplicationContext applicationContext) {
		// TODO Search for beans implementing @WebService annotated interfaces
		final String[] endpoints = applicationContext.getBeanNamesForAnnotation(WebServiceEndpoint.class);
		return new Iterable<String>() {
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					int i = 0;

					public boolean hasNext() {
						return i < endpoints.length;
					}

					public String next() {
						try {
							return endpoints[i++];
						} catch (ArrayIndexOutOfBoundsException ex) {
							throw new NoSuchElementException(ex.getMessage());
						}
					}
				};
			}
		};
	}

	@Override
	protected void registerService(ApplicationContext applicationContext, String name, Class<?> endpointClass) {
		if (logger.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Registering endpoint ");
			sb.append(name);
			sb.append(" of type ");
			sb.append(endpointClass.getName());
			logger.trace(sb.toString());
		}
		Class<?>[] interfaces = endpointClass.getInterfaces();
		for (int i = 0; i < interfaces.length; i++) {
			Class<?> serviceClass = interfaces[i];
			Method[] methods = serviceClass.getMethods();
			for (int m = 0; m < methods.length; m++) {
				Method method = methods[m];
				WebMethod webMethod = method.getAnnotation(WebMethod.class);
				if (null != webMethod) {
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append("Registering endpoint ");
						sb.append(name);
						sb.append(" method ");
						sb.append(webMethod.action());
						logger.trace(sb.toString());
					}
					if (null != registerEndpoint(new SoapAction(webMethod.action()), new JaxWsMethodEndpointExecutor(
							new JaxWsMethodEndpointExecutor.EndpointAddress(applicationContext, name, endpointClass),
							method))) {
						throw new RuntimeException("Endpoint already registered for " + webMethod.action());
					}
				}
			}
		}
	}
}
