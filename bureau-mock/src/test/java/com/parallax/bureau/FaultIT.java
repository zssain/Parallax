package com.parallax.bureau;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class FaultIT {

    private static final String SOAP_ENVELOPE =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "xmlns:br=\"urn:parallax:bureau:v1\"><soap:Body>"
                    + "<br:CreditReportRequest>"
                    + "<br:Ssn>912345678</br:Ssn>"
                    + "<br:FirstName>Ishaan</br:FirstName>"
                    + "<br:LastName>Kapoor</br:LastName>"
                    + "<br:DateOfBirth>1996-04-18</br:DateOfBirth>"
                    + "<br:Address>48 Elm Street, Columbus OH</br:Address>"
                    + "<br:PullType>HARD</br:PullType>"
                    + "</br:CreditReportRequest></soap:Body></soap:Envelope>";

    @Autowired
    private TestRestTemplate rest;

    @LocalServerPort
    private int port;

    private void setFault(String mode, int delayMs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"mode\":\"" + mode + "\",\"delayMs\":" + delayMs + "}";
        rest.postForEntity(url("/admin/fault"), new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> soapCall() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        return rest.postForEntity(url("/ws"), new HttpEntity<>(SOAP_ENVELOPE, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void downReturns503SoapFault() {
        setFault("DOWN", 0);
        try {
            ResponseEntity<String> response = soapCall();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).contains("faultstring");
        } finally {
            setFault("NONE", 0);
        }
    }

    @Test
    void slowDelaysTheCall() {
        setFault("SLOW", 300);
        try {
            long start = System.nanoTime();
            soapCall();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
        } finally {
            setFault("NONE", 0);
        }
    }

    @Test
    void noneReturns200() {
        setFault("NONE", 0);
        ResponseEntity<String> response = soapCall();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
