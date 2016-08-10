package org.filatov.springforest.ws.service.endpoint.mapping;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.xml.ws.Holder;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.ws.context.MessageContext;

class JaxWsMethodEndpointExecutor implements JaxWsMethodEndpoint<JaxWsMethodEndpointExecutor.EndpointSettings> {

	private static final Log logger = LogFactory.getLog(JaxWsMethodEndpointExecutor.class);

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

	public static class EndpointSettings {

		private final Unmarshaller requestMarshaller;
		private final Marshaller responseMarshaller;

		public EndpointSettings(Unmarshaller requestMarshaller, Marshaller responseMarshaller) {
			this.requestMarshaller = requestMarshaller;
			this.responseMarshaller = responseMarshaller;
		}

		public EndpointSettings(Unmarshaller requestMarshaller) {
			this(requestMarshaller, asResponseMarshaller(requestMarshaller));
		}

		private static Marshaller asResponseMarshaller(Unmarshaller requestMarshaller) {
			if (Marshaller.class.isAssignableFrom(requestMarshaller.getClass())) {
				return (Marshaller) requestMarshaller;
			} else {
				throw new RuntimeException("Response marshaller can not be assigned from request marshaller.");
			}
		}
	}

	private static class EndpointDescriptor {

		private final Class<?> requestType;
		private final Class<?> responseType;

		public EndpointDescriptor(Class<?> requestType, Class<?> responseType) {
			this.requestType = requestType;
			this.responseType = responseType;
		}
	}

	private static interface WsRequestWrapper {
		WsRequestWrapper DIRECT = new WsRequestWrapper() {
			public Object value(Object container) throws Exception {
				return container;
			}
		};
		WsRequestWrapper OUTPUT = new WsRequestWrapper() {
			public Object value(Object container) throws Exception {
				return new Holder<Object>();
			}
		};

		Object value(Object container) throws Exception;
	}

	private static interface WsResponseWrapper {

		static interface WsResponseWrapperSetter {
			WsResponseWrapperSetter NOP = new WsResponseWrapperSetter() {
				public void set(Object arguments) {
				}
			};

			public void set(Object arguments);
		}

		WsResponseWrapper DIRECT = new WsResponseWrapper() {
			public Object value(Object arguments, Object result) throws Exception {
				return result;
			}
		};

		Object value(Object arguments, Object result) throws Exception;
	}

	private final Method method;
	private final EndpointAddress address;
	private final WsRequestWrapper requestWrapper;
	private final WsResponseWrapper responseWrapper;

	public JaxWsMethodEndpointExecutor(EndpointAddress address, Method method) {
		this(address, method, generateDescriptor(method));
	}

	private JaxWsMethodEndpointExecutor(EndpointAddress address, Method method, EndpointDescriptor descriptor) {
		this.address = address;
		this.method = method;
		this.requestWrapper = generateRequestWrapper(method.getParameters(), descriptor);
		this.responseWrapper = generateResponseWrapper(method.getParameters(), descriptor, method);
	}

	private static WsResponseWrapper generateResponseWrapper(Parameter[] parameters, EndpointDescriptor descriptor,
			Method method) {
		try {
			if (null != descriptor.responseType) {
				Class<?> resultType = descriptor.responseType;
				final Object resultObject = resultType.newInstance();
				Map<String, Method> setterMap = new HashMap<String, Method>();
				{
					Method[] setters = resultType.getDeclaredMethods();
					for (int i = 0; i < setters.length; i++) {
						setterMap.put(setters[i].getName(), setters[i]);
					}
				}
				ArrayList<WsResponseWrapper.WsResponseWrapperSetter> wrappersetters = new ArrayList<WsResponseWrapper.WsResponseWrapperSetter>();
				for (int i = 0; i < parameters.length; i++) {
					WebParam webParam = parameters[i].getAnnotation(WebParam.class);
					if (null == webParam) {
						continue;
					}
					WebParam.Mode mode = webParam.mode();
					if (null != mode && (mode.equals(WebParam.Mode.OUT) || mode.equals(WebParam.Mode.INOUT))) {
						final int paramIndex = i;
						final Method setter = setterMap.get("set" + capitalize(webParam.name()));
						wrappersetters.add(new WsResponseWrapper.WsResponseWrapperSetter() {
							public void set(Object arguments) {
								try {
									if (arguments instanceof Object[]
											&& (((Object[]) arguments)[paramIndex] instanceof Holder)) {
										setter.invoke(resultObject,
												((Holder) ((Object[]) arguments)[paramIndex]).value);
									}
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							}
						});
					}
				}
				final WsResponseWrapper.WsResponseWrapperSetter[] setters = wrappersetters
						.toArray(new WsResponseWrapper.WsResponseWrapperSetter[wrappersetters.size()]);
				WsResponseWrapper.WsResponseWrapperSetter returnSetter = WsResponseWrapper.WsResponseWrapperSetter.NOP;
				WebResult returnWebParam = method.getAnnotation(WebResult.class);
				if (null != returnWebParam) {
					final Method setter = setterMap.get("set" + capitalize(returnWebParam.name()));
					returnSetter = new WsResponseWrapper.WsResponseWrapperSetter() {
						public void set(Object result) {
							try {
								setter.invoke(resultObject, result);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
					};
				}
				final WsResponseWrapper.WsResponseWrapperSetter resultSetter = returnSetter;
				return new WsResponseWrapper() {
					public Object value(Object arguments, Object result) throws Exception {
						for (int i = 0; i < setters.length; i++) {
							setters[i].set(arguments);
						}
						resultSetter.set(result);
						return resultObject;
					}
				};
			} else {
				return WsResponseWrapper.DIRECT;
			}
		} catch (Exception e) {
			throw new RuntimeException("Can't prepare argument wrappers", e);
		}
	}

	private static WsRequestWrapper generateRequestWrapper(Parameter[] parameters, EndpointDescriptor descriptor) {
		try {
			if (null != descriptor.requestType) {
				final WsRequestWrapper[] wrappers = new WsRequestWrapper[parameters.length];
				for (int i = 0; i < parameters.length; i++) {
					WebParam webParam = parameters[i].getAnnotation(WebParam.class);
					WebParam.Mode mode = webParam.mode();
					if (null != mode && mode.equals(WebParam.Mode.OUT)) {
						wrappers[i] = WsRequestWrapper.OUTPUT;
					} else {
						final Method getter = descriptor.requestType
								.getDeclaredMethod("get" + capitalize(webParam.name()));
						if (null == mode | mode.equals(WebParam.Mode.IN)) {
							wrappers[i] = new WsRequestWrapper() {
								public Object value(Object container) throws Exception {
									return getter.invoke(container);
								}
							};
						} else if (mode.equals(WebParam.Mode.INOUT)) {
							wrappers[i] = new WsRequestWrapper() {

								public Object value(Object container) throws Exception {
									return new Holder(getter.invoke(container));
								}
							};
						}
					}
				}
				return new WsRequestWrapper() {
					public Object value(Object container) throws Exception {
						Object[] argValues = new Object[wrappers.length];
						for (int i = 0; i < wrappers.length; i++) {
							argValues[i] = wrappers[i].value(container);
						}
						return argValues;
					}
				};
			} else

			{
				return WsRequestWrapper.DIRECT;
			}
		} catch (Exception e) {
			throw new RuntimeException("Can't prepare argument wrappers", e);
		}
	}

	private static String capitalize(String str) {
		if (null == str || 0 == str.length()) {
			return str;
		}
		int strLen = str.length();
		StringBuffer buffer = new StringBuffer(strLen);
		for (int i = 0; i < strLen; i++) {
			if (0 == i) {
				buffer.append(Character.toTitleCase(str.charAt(i)));
			} else {
				buffer.append(str.charAt(i));
			}
		}
		return buffer.toString();
	}

	private static EndpointDescriptor generateDescriptor(Method method) {

		RequestWrapper requestWrapper = method.getDeclaredAnnotation(RequestWrapper.class);
		ResponseWrapper responseWrapper = method.getDeclaredAnnotation(ResponseWrapper.class);

		Class<?> requestClass = null;
		if (requestWrapper != null && requestWrapper.className() != null) {
			try {
				requestClass = Class.forName(requestWrapper.className());
			} catch (ClassNotFoundException e) {
				if (logger.isErrorEnabled()) {
					logger.error("Can't get class for endpoint: " + method.getDeclaringClass(), e);
				}
			}
		}

		Class<?> responseClass = null;
		if (responseWrapper != null && responseWrapper.className() != null) {
			try {
				responseClass = Class.forName(responseWrapper.className());
			} catch (ClassNotFoundException e) {
				throw new RuntimeException("Can't get class for endpoint: " + method.getDeclaringClass(), e);
			}
		}

		return new EndpointDescriptor(requestClass, responseClass);
	}

	@Override
	public String toString() {
		return "JaxWsMethodEndpoint [method=" + method + "]";
	}

	public void execute(MessageContext messageContext, EndpointSettings settings) throws Exception {
		// System.err.println(messageContext.getPropertyNames());
		// System.err.println(messageContext);
		Object beanObject = address.applicationContext.getBean(address.name, address.type);

		Object container = settings.requestMarshaller.unmarshal(messageContext.getRequest().getPayloadSource());

		// System.err.println("Method arguments: " +
		// method.getParameterCount());

		Object result = null;
		Object arguments = requestWrapper.value(container);
		if (arguments instanceof Object[]) {
			// System.err.println("Wrapped arguments: " + ((Object[])
			// arguments).length);
			for (int i = 0; i < ((Object[]) arguments).length; i++) {
				// System.err.println("Wrapped argument " + i + ": " +
				// ((Object[]) arguments)[i]);
			}
			result = method.invoke(beanObject, ((Object[]) arguments));
		} else {
			// System.err.println("Wrapped argument: " + arguments.toString());
			result = method.invoke(beanObject, arguments);
		}
		// System.out.println("Result: " + result);
		Object wrappedResult = responseWrapper.value(arguments, result);
		// System.out.println("WrappedResult: " + result);
		if (wrappedResult != null) {
			settings.responseMarshaller.marshal(wrappedResult, messageContext.getResponse().getPayloadResult());
		} else {
			messageContext.getResponse().getPayloadResult();
		}
	}

}