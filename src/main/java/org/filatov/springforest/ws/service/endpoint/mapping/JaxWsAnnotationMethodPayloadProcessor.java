package org.filatov.springforest.ws.service.endpoint.mapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.xml.ws.Holder;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;

import org.filatov.springforest.ws.service.endpoint.mapping.util.JaxWsAnnotationHelper;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.server.endpoint.adapter.method.MethodArgumentResolver;
import org.springframework.ws.server.endpoint.adapter.method.MethodReturnValueHandler;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Component
public class JaxWsAnnotationMethodPayloadProcessor extends JaxWsAnnotationMethodEndpointAdapter {

	@Override
	protected boolean supportsInternal(MethodEndpoint methodEndpoint) {
		boolean supportsInternal = false;
		try {
			supportsInternal = null != JaxWsAnnotationHelper.getMethodAnnotation(methodEndpoint.getMethod(),
					RequestWrapper.class) && supportsParameters(getParameters(methodEndpoint))
					&& supportsReturnType(getReturnType(methodEndpoint));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return supportsInternal;
	}

	private boolean supportsParameters(MethodParameter[] methodParameters) {
		for (MethodParameter methodParameter : methodParameters) {
			boolean supported = false;
			for (MethodArgumentResolver methodArgumentResolver : getMethodArgumentResolvers()) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing if argument resolver [" + methodArgumentResolver + "] supports ["
							+ methodParameter.getGenericParameterType() + "]");
				}
				if (methodArgumentResolver.supportsParameter(methodParameter)) {
					supported = true;
					break;
				}
			}
			if (!supported) {
				return false;
			}
		}
		return true;
	}

	private boolean supportsReturnType(MethodParameter methodReturnType) {
		if (Void.TYPE.equals(methodReturnType.getParameterType())) {
			return true;
		}
		for (MethodReturnValueHandler methodReturnValueHandler : getMethodReturnValueHandlers()) {
			if (methodReturnValueHandler.supportsReturnType(methodReturnType)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected Object[] getMethodArguments(MessageContext messageContext, MethodEndpoint methodEndpoint)
			throws Exception {

		MethodParameter[] parameters = getParameters(methodEndpoint);

		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			for (MethodArgumentResolver methodArgumentResolver : getMethodArgumentResolvers()) {
				if (methodArgumentResolver.supportsParameter(parameters[i])) {
					args[i] = methodArgumentResolver.resolveArgument(messageContext, parameters[i]);
					break;
				}
			}
		}

		Method method = methodEndpoint.getMethod();
		RequestWrapper requestWrapper = JaxWsAnnotationHelper.getMethodAnnotation(method, RequestWrapper.class);
		if (null != requestWrapper) {
			if (0 == args.length) {
				throw new IllegalArgumentException("Empty argument array can not be unwrapped by " + requestWrapper);
			}
			if (null == args[0]) {
				throw new IllegalArgumentException("Null can not be unwrapped by " + requestWrapper);
			}
			Object wrappedParam = args[0];
			Class<?> wrapperType = wrappedParam.getClass();
			Parameter[] params = method.getParameters();
			Object[] paramValues = new Object[params.length];
			for (int i = 0; i < params.length; i++) {
				WebParam webParam = JaxWsAnnotationHelper.getParameterAnnotation(method, i, WebParam.class);
				if (null == webParam.mode() || webParam.mode().equals(WebParam.Mode.IN)
						|| webParam.mode().equals(WebParam.Mode.INOUT)) {
					if (null == webParam) {
						throw new IllegalArgumentException(
								"WebParam annotation not found in " + method + " for param " + i);
					}
					String name = webParam.name();
					Method getter = wrapperType.getMethod("get" + capitalize(name));
					if (webParam.mode().equals(WebParam.Mode.INOUT)) {
						if (params[i].getType().equals(Holder.class)) {
							Holder holder = Holder.class.newInstance();
							holder.value = getter.invoke(wrappedParam);
							paramValues[i] = holder;
						}
					} else {
						paramValues[i] = getter.invoke(wrappedParam);
					}
				} else if (webParam.mode().equals(WebParam.Mode.OUT)) {
					if (params[i].getType().equals(Holder.class)) {
						paramValues[i] = Holder.class.newInstance();
					}
				}
			}
			args = paramValues;
		}
		return args;
	}

	private MethodParameter getReturnType(MethodEndpoint methodEndpoint) throws ClassNotFoundException {
		Method method = methodEndpoint.getMethod();
		ResponseWrapper responseWrapper = JaxWsAnnotationHelper.getMethodAnnotation(method, ResponseWrapper.class);
		if (null != responseWrapper) {
			final Class<?> responseType = Class.forName(responseWrapper.className());
			return new MethodParameter(methodEndpoint.getMethod(), -1) {

				private Annotation responsePayload = new ResponsePayload() {
					public Class<? extends Annotation> annotationType() {
						return ResponsePayload.class;
					}
				};

				@Override
				public <T extends Annotation> T getMethodAnnotation(Class<T> annotationType) {
					if (annotationType.equals(ResponsePayload.class)) {
						return (T) responsePayload;
					}
					return super.getMethodAnnotation(annotationType);
				}

				@Override
				public Class<?> getParameterType() {
					return responseType;
				}

			};
		}
		return methodEndpoint.getReturnType();
	}

	private MethodParameter[] getParameters(MethodEndpoint methodEndpoint) throws ClassNotFoundException {
		RequestWrapper requestWrapper = JaxWsAnnotationHelper.getMethodAnnotation(methodEndpoint.getMethod(),
				RequestWrapper.class);
		if (null != requestWrapper) {
			final Class<?> parameterType = Class.forName(requestWrapper.className());
			return new MethodParameter[] { new MethodParameter(methodEndpoint.getMethod(), 0) {
				private Annotation[] parameterAnnotations;

				public Annotation[] getParameterAnnotations() {
					if (null == this.parameterAnnotations) {
						Annotation[] result = new Annotation[] { new RequestPayload() {
							public Class<? extends Annotation> annotationType() {
								return RequestPayload.class;
							}
						} };
						this.parameterAnnotations = result;
					}
					return this.parameterAnnotations;
				}

				public Class<?> getParameterType() {
					return parameterType;
				}
			} };
		} else {
			return methodEndpoint.getMethodParameters();
		}
	}

	private String capitalize(String str) {
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

	private Map<String, List<Method>> mapMethods(Method[] methods) {
		Map<String, List<Method>> map = new HashMap<String, List<Method>>();
		for (int i = 0; i < methods.length; i++) {
			List<Method> list = map.get(methods[i].getName());
			if (null == list) {
				list = new ArrayList<Method>();
				map.put(methods[i].getName(), list);
			}
			list.add(methods[i]);
		}
		return map;
	}

	@Override
	protected void handleMethodReturnValue(MessageContext messageContext, Object[] arguments, Object returnValue,
			MethodEndpoint methodEndpoint) throws Exception {
		Method method = methodEndpoint.getMethod();
		ResponseWrapper responseWrapper = JaxWsAnnotationHelper.getMethodAnnotation(method, ResponseWrapper.class);
		MethodParameter returnType = getReturnType(methodEndpoint);
		if (!Void.TYPE.equals(returnType.getParameterType())) {
			if (null != responseWrapper) {
				Class<?> wrapperType = Class.forName(responseWrapper.className());
				Object wrapperObject = wrapperType.newInstance();
				if (!Void.TYPE.equals(method.getReturnType())) {
					WebResult webResult = JaxWsAnnotationHelper.getMethodAnnotation(method, WebResult.class);
					Method setter = wrapperType.getMethod("set" + capitalize(webResult.name()), method.getReturnType());
					setter.invoke(wrapperObject, returnValue);
				}
				Parameter[] parameters = method.getParameters();
				Map<String, List<Method>> methodMap = mapMethods(wrapperType.getMethods());
				for (int i = 0; i < parameters.length; i++) {
					WebParam webParam = JaxWsAnnotationHelper.getParameterAnnotation(method, i, WebParam.class);
					if (webParam.mode().equals(WebParam.Mode.OUT) || webParam.mode().equals(WebParam.Mode.INOUT)) {
						if (!parameters[i].getType().equals(Holder.class)) {
							throw new IllegalArgumentException("Output values should be placed at holders");
						}
						Object argValue = ((Holder<?>) arguments[i]).value;
						List<Method> setters = methodMap.get("set" + capitalize(webParam.name()));
						for (Method setter : setters) {
							Class<?>[] types = setter.getParameterTypes();
							if (types.length != 1) {
								continue;
							}
							if (types[0].isAssignableFrom(argValue.getClass())) {
								setter.invoke(wrapperObject, argValue);
								break;
							}
						}
					}
				}
				returnValue = wrapperObject;
			}
			for (MethodReturnValueHandler methodReturnValueHandler : getMethodReturnValueHandlers()) {
				if (methodReturnValueHandler.supportsReturnType(returnType)) {
					methodReturnValueHandler.handleReturnValue(messageContext, returnType, returnValue);
					return;
				}
			}
			throw new IllegalStateException(
					"Return value [" + returnValue + "] not resolved by any MethodReturnValueHandler");
		}
	}

}