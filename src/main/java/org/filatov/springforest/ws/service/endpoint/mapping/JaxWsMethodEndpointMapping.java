package org.filatov.springforest.ws.service.endpoint.mapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.jws.WebMethod;
import javax.jws.WebParam;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;

import org.springframework.context.ApplicationContext;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.support.PayloadRootUtils;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapEnvelope;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.TransportConstants;
import org.w3c.dom.Node;

public class JaxWsMethodEndpointMapping
		extends AbstractDiscoveringCachedEndpointMapping<SoapAction, JaxWsMethodEndpoint> {

	Map<String, String> requestToActionMappings = new HashMap<String, String>();

	private static final String TRIM_CHARS = "\n\t' \"";
	private static final TransformerFactory transformerFactory;
	static {
		transformerFactory = TransformerFactory.newInstance();
	}

	@Override
	protected SoapAction getEnpointKey(MessageContext messageContext) throws TransformerException {

		WebServiceMessage message = messageContext.getRequest();
		if (SoapMessage.class.isAssignableFrom(message.getClass())) {
			String soapAction = ((SoapMessage) message).getSoapAction();
			soapAction = null != soapAction && !soapAction.equals(TransportConstants.EMPTY_SOAP_ACTION) ? //
					soapAction : null;
			if (null == soapAction) {
				SoapBody soapBody = ((SoapMessage) message).getSoapBody();
				String soapRequest = getSoapRequestFromSource(soapBody.getSource());
				if (null != soapRequest) {
					soapAction = requestToActionMappings.get(soapRequest);
				}
			}
			if (null != soapAction) {
				return new SoapAction(soapAction.replaceAll("^[" + TRIM_CHARS + "]|[" + TRIM_CHARS + "]$", ""));
			}
		}
		throw new RuntimeException("No endpoint found for " + messageContext);
	}

	private String getSoapRequestFromSource(Source source) {
		if (source instanceof DOMSource) {
			org.w3c.dom.Node root = ((DOMSource) source).getNode();
			Node child = root.getFirstChild();
			while (child != null && child.getNodeType() != Node.ELEMENT_NODE) {
				child = child.getNextSibling();
			}
			return null != child ? child.getLocalName() : null;
		} else {
			throw new RuntimeException("Supported only DOMSources");
		}
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
					String actionName = webMethod.action();
					if (null == actionName || actionName.isEmpty()) {
						actionName = method.getName();
					}
					if (logger.isTraceEnabled()) {
						StringBuilder sb = new StringBuilder();
						sb.append("Registering endpoint ");
						sb.append(name);
						sb.append(" method ");
						sb.append(actionName);
						logger.trace(sb.toString());
					}
					if (null != registerEndpoint(new SoapAction(actionName), new JaxWsMethodEndpointExecutor(
							new JaxWsMethodEndpointExecutor.EndpointAddress(applicationContext, name, endpointClass),
							method))) {
						throw new RuntimeException("Endpoint already registered for " + actionName);
					} else {
						Parameter[] params = method.getParameters();
						for (int j = 0; j < params.length; j++) {
							WebParam webParam = params[i].getAnnotation(WebParam.class);
							if (webParam != null) {
								String paramName = webParam.name();
								paramName = null != paramName ? paramName : params[i].getName();
								if (logger.isTraceEnabled()) {
									StringBuilder sb = new StringBuilder();
									sb.append("Mapping endpoint ");
									sb.append(name);
									sb.append(" method ");
									sb.append(actionName);
									sb.append(" by ");
									sb.append(paramName);
									logger.trace(sb.toString());
								}
								if (null != requestToActionMappings.put(paramName, actionName)) {
									throw new RuntimeException(
											"Endpoint already mapped by " + paramName + " to " + actionName);
								}
							}
						}
					}
				}
			}
		}
	}
}
