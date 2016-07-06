package org.filatov.springforest.ws.server.endpoint.mapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.mapping.AbstractAnnotationMethodEndpointMapping;
import org.springframework.ws.server.endpoint.support.PayloadRootUtils;

@Component
public class JaxWsAnnotationMethodEndpointMapping extends
		AbstractAnnotationMethodEndpointMapping<QName> {

	private static TransformerFactory transformerFactory;

	static {
		transformerFactory = TransformerFactory.newInstance();
	}

	@Override
	protected QName getLookupKeyForMessage(MessageContext messageContext)
			throws Exception {
		return PayloadRootUtils.getPayloadRootQName(messageContext.getRequest()
				.getPayloadSource(), transformerFactory);
	}

	@Override
	protected QName getLookupKeyForMethod(Method method) {
		Class<?> webService = null;
		Class<?>[] interfaces = method.getDeclaringClass().getInterfaces();
		for (int i = 0; i < interfaces.length; i++) {
			if (null != interfaces[i].getAnnotation(WebService.class)) {
				webService = interfaces[i];
			}
		}
		if (webService == null) {
			throw new RuntimeException(
					"@WebService was not found in hierarchy of: "
							+ method.getDeclaringClass().getCanonicalName());
		}
		WebMethod webMethod = method.getAnnotation(WebMethod.class);
		if (webMethod == null) {
			try {
				Method superMethod = webService.getMethod(method.getName(),
						method.getParameterTypes());
				System.out.println(superMethod);
				webMethod = superMethod.getAnnotation(WebMethod.class);
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			}
		}

		Annotation[] paramAnnotations = null;
		paramAnnotations = method.getParameterAnnotations()[0];
		WebParam input = null;
		while (null != paramAnnotations) {
			for (int i = 0; i < paramAnnotations.length; i++) {
				if (WebParam.class.isAssignableFrom(paramAnnotations[i]
						.getClass())) {
					input = (WebParam) paramAnnotations[i];
				}
			}
			if (input == null) {
				paramAnnotations = null;
				try {
					Method superMethod = webService.getMethod(method.getName(),
							method.getParameterTypes());
					paramAnnotations = superMethod.getParameterAnnotations()[0];
				} catch (NoSuchMethodException e) {
					e.printStackTrace();
				} catch (SecurityException e) {
					e.printStackTrace();
				}

			} else {
				break;
			}
		}
		if (webMethod != null && input != null) {
			QName qname;
			if (StringUtils.hasLength(input.partName())
					&& StringUtils.hasLength(input.targetNamespace())) {
				qname = new QName(input.targetNamespace(), input.partName());
			} else {
				qname = new QName(input.partName());
			}
			return qname;
		} else {
			return null;
		}
	}

	@Override
	protected Class<? extends Annotation> getEndpointAnnotationType() {
		return WebServiceEndpoint.class;
	}
}