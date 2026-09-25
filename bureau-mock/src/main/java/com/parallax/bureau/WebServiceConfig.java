package com.parallax.bureau;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;

/** Spring Web Services wiring: the SOAP servlet at /ws/*, the WSDL, and payload validation. */
@EnableWs
@Configuration
public class WebServiceConfig implements WsConfigurer {

    static final String NAMESPACE = "urn:parallax:bureau:v1";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean
    public XsdSchema bureauSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/bureau.xsd"));
    }

    @Bean(name = "bureau")
    public DefaultWsdl11Definition bureau(XsdSchema bureauSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("BureauPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace(NAMESPACE);
        definition.setSchema(bureauSchema);
        return definition;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        PayloadValidatingInterceptor validating = new PayloadValidatingInterceptor();
        validating.setSchema(new ClassPathResource("xsd/bureau.xsd"));
        validating.setValidateRequest(true);
        validating.setValidateResponse(true);
        try {
            // This interceptor is not a Spring bean, so compile its schema validator by hand.
            validating.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise the bureau schema validator", e);
        }
        interceptors.add(validating);
    }
}
