package org.filatov.springforest.ws.service.endpoint.mapping;

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
		{
			Class<?>[] interfaces = method.getDeclaringClass().getInterfaces();
			for (int i = 0; i < interfaces.length; i++) {
				if (null != interfaces[i].getAnnotation(WebService.class)) {
					webService = interfaces[i];
					break;
				}
			}
		}
		if (webService != null) {
			WebMethod webMethod = method.getAnnotation(WebMethod.class);
			if (webMethod == null) {
				try {
					Method superMethod = webService.getMethod(method.getName(),
							method.getParameterTypes());
					webMethod = superMethod.getAnnotation(WebMethod.class);
				} catch (Throwable th) {
					if (logger.isWarnEnabled()) {
						logger.warn(
								"@WebMethod annotation was not found on method or method declaration: "
										+ method, th);
					}
				}
			}
			if (webMethod != null) {
				WebParam inputType = null;
				{
					Annotation[][] paramsAnnotations = method
							.getParameterAnnotations();
					if (paramsAnnotations.length == 1) {
						Annotation[] paramAnnotations = paramsAnnotations[0];
						while (null != paramAnnotations) {
							for (int i = 0; i < paramAnnotations.length; i++) {
								if (WebParam.class
										.isAssignableFrom(paramAnnotations[i]
												.getClass())) {
									inputType = (WebParam) paramAnnotations[i];
								}
							}
							if (inputType == null) {
								paramAnnotations = null;
								try {
									Method superMethod = webService.getMethod(
											method.getName(),
											method.getParameterTypes());
									paramAnnotations = superMethod
											.getParameterAnnotations()[0];
								} catch (Throwable th) {
									if (logger.isWarnEnabled()) {
										logger.warn(
												"@WebParam annotation was not found on method arguments or method declaration first argument: "
														+ method, th);
									}
								}
							} else {
								break;
							}
						}
					} else {
						if (logger.isWarnEnabled()) {
							logger.warn("@WebMethod annotated method or method declaration should have one @WebParam annotated argument: "
									+ method);
						}
					}
				}
				if (inputType != null) {
					QName qname;
					if (StringUtils.hasLength(inputType.partName())
							&& StringUtils.hasLength(inputType
									.targetNamespace())) {
						qname = new QName(inputType.targetNamespace(),
								inputType.partName());
					} else {
						qname = new QName(inputType.partName());
					}
					return qname;
				}
			}
		} else {
			if (logger.isErrorEnabled()) {
				logger.error("@WebService annotation was not found in hierarchy of: "
						+ method.getDeclaringClass());
			}
		}
		return null;
	}

	@Override
	protected Class<? extends Annotation> getEndpointAnnotationType() {
		return WebServiceEndpoint.class;
	}
}