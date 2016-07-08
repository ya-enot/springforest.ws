package org.filatov.springforest.ws.service.endpoint.mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.server.endpoint.adapter.AbstractMethodEndpointAdapter;
import org.springframework.ws.server.endpoint.adapter.method.MethodArgumentResolver;
import org.springframework.ws.server.endpoint.adapter.method.MethodReturnValueHandler;
import org.springframework.ws.server.endpoint.adapter.method.jaxb.JaxbElementPayloadMethodProcessor;
import org.springframework.ws.server.endpoint.adapter.method.jaxb.XmlRootElementPayloadMethodProcessor;

@Component
public class JaxWsAnnotationMethodEndpointAdapter extends AbstractMethodEndpointAdapter
		implements BeanClassLoaderAware, InitializingBean {

	private static final String JAXB2_CLASS_NAME = "javax.xml.bind.Binder";

	private List<MethodArgumentResolver> methodArgumentResolvers;

	private List<MethodReturnValueHandler> methodReturnValueHandlers;

	private ClassLoader classLoader;

	/**
	 * Returns the list of {@code MethodArgumentResolver}s to use.
	 */
	public List<MethodArgumentResolver> getMethodArgumentResolvers() {
		return methodArgumentResolvers;
	}

	/**
	 * Sets the list of {@code MethodArgumentResolver}s to use.
	 */
	public void setMethodArgumentResolvers(List<MethodArgumentResolver> methodArgumentResolvers) {
		this.methodArgumentResolvers = methodArgumentResolvers;
	}

	/**
	 * Returns the list of {@code MethodReturnValueHandler}s to use.
	 */
	public List<MethodReturnValueHandler> getMethodReturnValueHandlers() {
		return methodReturnValueHandlers;
	}

	/**
	 * Sets the list of {@code MethodReturnValueHandler}s to use.
	 */
	public void setMethodReturnValueHandlers(List<MethodReturnValueHandler> methodReturnValueHandlers) {
		this.methodReturnValueHandlers = methodReturnValueHandlers;
	}

	private ClassLoader getClassLoader() {
		return null != this.classLoader ? this.classLoader : getClass().getClassLoader();
	}

	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public void afterPropertiesSet() throws Exception {
		initDefaultStrategies();
	}

	/** Initialize the default implementations for the adapter's strategies. */
	protected void initDefaultStrategies() {
		initMethodArgumentResolvers();
		initMethodReturnValueHandlers();
	}

	private void initMethodArgumentResolvers() {
		if (CollectionUtils.isEmpty(methodArgumentResolvers)) {
			List<MethodArgumentResolver> methodArgumentResolvers = new ArrayList<MethodArgumentResolver>();
			methodArgumentResolvers.add(new XmlRootElementPayloadMethodProcessor());
			methodArgumentResolvers.add(new JaxbElementPayloadMethodProcessor());
			setMethodArgumentResolvers(methodArgumentResolvers);
		}
	}

	/**
	 * Certain (SOAP-specific) {@code MethodArgumentResolver}s have to be
	 * instantiated by class name, in order to not introduce a cyclic
	 * dependency.
	 */
	@SuppressWarnings("unchecked")
	private void addMethodArgumentResolver(String className, List<MethodArgumentResolver> methodArgumentResolvers) {
		try {
			Class<MethodArgumentResolver> methodArgumentResolverClass = (Class<MethodArgumentResolver>) ClassUtils
					.forName(className, getClassLoader());
			methodArgumentResolvers.add(BeanUtils.instantiate(methodArgumentResolverClass));
		} catch (ClassNotFoundException e) {
			logger.warn("Could not find \"" + className + "\" on the classpath");
		}
	}

	private void initMethodReturnValueHandlers() {
		if (CollectionUtils.isEmpty(methodReturnValueHandlers)) {
			List<MethodReturnValueHandler> methodReturnValueHandlers = new ArrayList<MethodReturnValueHandler>();
			methodReturnValueHandlers.add(new XmlRootElementPayloadMethodProcessor());
			methodReturnValueHandlers.add(new JaxbElementPayloadMethodProcessor());
			setMethodReturnValueHandlers(methodReturnValueHandlers);
		}
	}

	private boolean isPresent(String className) {
		return ClassUtils.isPresent(className, getClassLoader());
	}

	@Override
	protected boolean supportsInternal(MethodEndpoint methodEndpoint) {
		return supportsParameters(methodEndpoint.getMethodParameters())
				&& supportsReturnType(methodEndpoint.getReturnType());
	}

	private boolean supportsParameters(MethodParameter[] methodParameters) {
		for (MethodParameter methodParameter : methodParameters) {
			boolean supported = false;
			for (MethodArgumentResolver methodArgumentResolver : methodArgumentResolvers) {
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
		for (MethodReturnValueHandler methodReturnValueHandler : methodReturnValueHandlers) {
			if (methodReturnValueHandler.supportsReturnType(methodReturnType)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected final void invokeInternal(MessageContext messageContext, MethodEndpoint methodEndpoint) throws Exception {
		Object[] args = getMethodArguments(messageContext, methodEndpoint);

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking [" + methodEndpoint + "] with arguments " + Arrays.asList(args));
		}

		Object returnValue = methodEndpoint.invoke(args);

		if (logger.isTraceEnabled()) {
			logger.trace("Method [" + methodEndpoint + "] returned [" + returnValue + "]");
		}

		handleMethodReturnValue(messageContext, args, returnValue, methodEndpoint);
	}

	/**
	 * Returns the argument array for the given method endpoint.
	 *
	 * <p>
	 * This implementation iterates over the set
	 * {@linkplain #setMethodArgumentResolvers(List) argument resolvers} to
	 * resolve each argument.
	 *
	 * @param messageContext
	 *            the current message context
	 * @param methodEndpoint
	 *            the method endpoint to get arguments for
	 * @return the arguments
	 * @throws Exception
	 *             in case of errors
	 */
	protected Object[] getMethodArguments(MessageContext messageContext, MethodEndpoint methodEndpoint)
			throws Exception {
		MethodParameter[] parameters = methodEndpoint.getMethodParameters();
		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			for (MethodArgumentResolver methodArgumentResolver : methodArgumentResolvers) {
				if (methodArgumentResolver.supportsParameter(parameters[i])) {
					args[i] = methodArgumentResolver.resolveArgument(messageContext, parameters[i]);
					break;
				}
			}
		}
		return args;
	}

	/**
	 * Handle the return value for the given method endpoint.
	 *
	 * <p>
	 * This implementation iterates over the set
	 * {@linkplain #setMethodReturnValueHandlers(java.util.List)} return value
	 * handlers} to resolve the return value.
	 *
	 * @param messageContext
	 *            the current message context
	 * @param returnValue
	 *            the return value
	 * @param methodEndpoint
	 *            the method endpoint to get arguments for
	 * @throws Exception
	 *             in case of errors
	 */
	protected void handleMethodReturnValue(MessageContext messageContext, Object returnValue,
			MethodEndpoint methodEndpoint) throws Exception {
		MethodParameter returnType = methodEndpoint.getReturnType();
		if (!Void.TYPE.equals(returnType.getParameterType())) {
			for (MethodReturnValueHandler methodReturnValueHandler : methodReturnValueHandlers) {
				if (methodReturnValueHandler.supportsReturnType(returnType)) {
					methodReturnValueHandler.handleReturnValue(messageContext, returnType, returnValue);
					return;
				}
			}
			throw new IllegalStateException(
					"Return value [" + returnValue + "] not resolved by any MethodReturnValueHandler");
		}
	}

	protected void handleMethodReturnValue(MessageContext messageContext, Object[] arguments, Object returnValue,
			MethodEndpoint methodEndpoint) throws Exception {
		handleMethodReturnValue(messageContext, returnValue, methodEndpoint);
	}
}
