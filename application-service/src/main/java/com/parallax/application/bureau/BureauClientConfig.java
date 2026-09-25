package com.parallax.application.bureau;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.ClientHttpRequestMessageSender;

import java.time.Duration;

/** Beans for the SOAP bureau client: a JAXB marshaller and a {@link WebServiceTemplate} with timeouts. */
@Configuration
public class BureauClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Bean
    public Jaxb2Marshaller bureauMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.parallax.bureau.contract");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate bureauWebServiceTemplate(Jaxb2Marshaller bureauMarshaller,
                                                       @Value("${parallax.bureau.url:http://localhost:8082/ws}")
                                                       String bureauUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());

        SaajSoapMessageFactory messageFactory = new SaajSoapMessageFactory();
        messageFactory.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate(messageFactory);
        template.setMarshaller(bureauMarshaller);
        template.setUnmarshaller(bureauMarshaller);
        template.setDefaultUri(bureauUrl);
        template.setMessageSender(new ClientHttpRequestMessageSender(factory));
        return template;
    }
}
