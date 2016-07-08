package org.filatov.springforest.ws.service.endpoint.mapping.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JaxWsAnnotationHelper {

	protected static final Log logger = LogFactory.getLog(JaxWsAnnotationHelper.class);

	public static <T extends Annotation> T getAnnotation(Class<?> base, Class<T> annotationClass) {
		T annotation = null;
		annotation = base.getAnnotation(annotationClass);
		if (null != annotation) {
			return annotation;
		}
		Class<?>[] implementees = base.getInterfaces();
		for (int i = 0; null == annotation && i < implementees.length; i++) {
			annotation = implementees[i].getAnnotation(annotationClass);
		}
		return annotation;
	}

	public static <T extends Annotation> T getMethodAnnotation(Executable method, Class<T> annotationClass) {
		T annotation = null;
		annotation = method.getAnnotation(annotationClass);
		if (null != annotation) {
			return annotation;
		}
		Method[] overridees = getOverridees(method);
		for (int i = 0; null == annotation && i < overridees.length; i++) {
			annotation = overridees[i].getAnnotation(annotationClass);
		}
		return annotation;
	}

	public static <T extends Annotation> T getParameterAnnotation(Executable method, Integer index,
			Class<T> annotationClass) {
		T annotation = null;
		annotation = method.getParameters()[index].getAnnotation(annotationClass);
		if (null != annotation) {
			return annotation;
		}
		Method[] overridees = getOverridees(method);
		for (int i = 0; null == annotation && i < overridees.length; i++) {
			annotation = overridees[i].getParameters()[index].getAnnotation(annotationClass);
		}
		return annotation;
	}

	public static Method[] getOverridees(Executable method) {

		Class<?>[] implementees = method.getDeclaringClass().getInterfaces();
		ArrayList<Method> overridees = new ArrayList<Method>();

		for (int i = 0; i < implementees.length; i++) {
			try {
				overridees.add(implementees[i].getMethod(method.getName(), method.getParameterTypes()));
			} catch (NoSuchMethodException ex) {
				logger.debug("Method " + method + " not found in " + implementees[i], ex);
			}
		}

		return overridees.toArray(new Method[overridees.size()]);
	}
}
