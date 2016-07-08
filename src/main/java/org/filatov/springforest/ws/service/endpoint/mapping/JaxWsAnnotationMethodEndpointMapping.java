package org.filatov.springforest.ws.service.endpoint.mapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerFactory;
import javax.xml.ws.RequestWrapper;

import org.filatov.springforest.ws.service.endpoint.mapping.util.JaxWsAnnotationHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.mapping.AbstractAnnotationMethodEndpointMapping;
import org.springframework.ws.server.endpoint.support.PayloadRootUtils;

@Component
public class JaxWsAnnotationMethodEndpointMapping extends AbstractAnnotationMethodEndpointMapping<QName> {

	private static TransformerFactory transformerFactory;
	static {
		transformerFactory = TransformerFactory.newInstance();
	}

	private final class Binding {

		final String nameSpaceURI;
		final String localPart;

		public Binding(String nameSpaceURI, String localPart) {
			super();
			this.nameSpaceURI = nameSpaceURI;
			this.localPart = localPart;
		}
	}

	@Override
	protected QName getLookupKeyForMessage(MessageContext messageContext) throws Exception {
		return PayloadRootUtils.getPayloadRootQName(messageContext.getRequest().getPayloadSource(), transformerFactory);
	}

	@Override
	protected QName getLookupKeyForMethod(Method method) {
		Class<?> webService = null;
		{
			if (null != method.getDeclaringClass().getAnnotation(WebService.class)) {
				webService = method.getDeclaringClass();
			} else {
				Class<?>[] interfaces = method.getDeclaringClass().getInterfaces();
				for (int i = 0; i < interfaces.length; i++) {
					if (null != interfaces[i].getAnnotation(WebService.class)) {
						webService = interfaces[i];
						break;
					}
				}
			}
		}

		if (null != webService) {
			WebMethod webMethod = JaxWsAnnotationHelper.getMethodAnnotation(method, WebMethod.class);
			if (null != webMethod) {
				Binding methodBinding = null;
				{
					RequestWrapper requestWrapper = JaxWsAnnotationHelper.getMethodAnnotation(method,
							RequestWrapper.class);
					if (null != requestWrapper) {
						methodBinding = new Binding(requestWrapper.targetNamespace(), requestWrapper.localName());
					} else if (method.getParameters().length == 1) {
						WebParam webParam = JaxWsAnnotationHelper.getParameterAnnotation(method, 0, WebParam.class);
						if (null != webParam) {
							methodBinding = new Binding(webParam.targetNamespace(), webParam.partName());
						} else {
							if (logger.isWarnEnabled()) {
								logger.warn(
										"@WebParam annotation was not found on method arguments or method declaration first argument: "
												+ method);
							}
						}
					} else {
						if (logger.isWarnEnabled()) {
							logger.warn(
									"@WebMethod annotated method or method declaration should have one @WebParam annotated argument: "
											+ method);
						}
					}
				}
				if (null != methodBinding) {
					QName qname;
					if (StringUtils.hasLength(methodBinding.nameSpaceURI)
							&& StringUtils.hasLength(methodBinding.nameSpaceURI)) {
						qname = new QName(methodBinding.nameSpaceURI, methodBinding.localPart);
					} else {
						qname = new QName(methodBinding.localPart);
					}
					return qname;
				}
			}
		} else {
			if (logger.isErrorEnabled()) {
				logger.error("@WebService annotation was not found in hierarchy of: " + method.getDeclaringClass());
			}
		}
		return null;
	}

	@Override
	protected Class<? extends Annotation> getEndpointAnnotationType() {
		return WebServiceEndpoint.class;
	}
}