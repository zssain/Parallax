package com.parallax.application.bureau;

import com.parallax.bureau.contract.CreditReportRequest;
import com.parallax.bureau.contract.CreditReportResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.io.StringWriter;

/** Thin SOAP client for the credit bureau (SPEC §8). Any transport error becomes {@link BureauUnavailableException}. */
@Component
public class BureauClient {

    private final WebServiceTemplate webServiceTemplate;
    private final JAXBContext jaxbContext;

    public BureauClient(WebServiceTemplate bureauWebServiceTemplate) {
        this.webServiceTemplate = bureauWebServiceTemplate;
        try {
            this.jaxbContext = JAXBContext.newInstance(CreditReportResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JAXB context for the bureau contract", e);
        }
    }

    /** Send the SOAP request and return the response, wrapping any failure as {@link BureauUnavailableException}. */
    @CircuitBreaker(name = "bureau")
    public CreditReportResponse pull(CreditReportRequest request) {
        try {
            return (CreditReportResponse) webServiceTemplate.marshalSendAndReceive(request);
        } catch (Exception e) {
            throw new BureauUnavailableException("Bureau pull failed", e);
        }
    }

    /** Pretty-printed XML of the response, as the UI shows it on the Decision detail screen. */
    public String marshalToXml(CreditReportResponse response) {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter writer = new StringWriter();
            marshaller.marshal(response, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to marshal the bureau response", e);
        }
    }
}
