package com.parallax.bureau;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.test.server.ResponseActions;
import org.springframework.xml.transform.StringSource;

import java.util.Map;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

@SpringBootTest
class BureauEndpointTest {

    private static final Map<String, String> NS = Map.of("br", "urn:parallax:bureau:v1");

    @Autowired
    private ApplicationContext applicationContext;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(applicationContext);
    }

    private static String request(String ssn, String pullType) {
        return "<br:CreditReportRequest xmlns:br=\"urn:parallax:bureau:v1\">"
                + "<br:Ssn>" + ssn + "</br:Ssn>"
                + "<br:FirstName>Ishaan</br:FirstName>"
                + "<br:LastName>Kapoor</br:LastName>"
                + "<br:DateOfBirth>1996-04-18</br:DateOfBirth>"
                + "<br:Address>48 Elm Street, Columbus OH</br:Address>"
                + "<br:PullType>" + pullType + "</br:PullType>"
                + "</br:CreditReportRequest>";
    }

    private ResponseActions send(String ssn) {
        return client.sendRequest(withPayload(new StringSource(request(ssn, "HARD"))));
    }

    private static String path(String field) {
        return "/br:CreditReportResponse/br:" + field;
    }

    @Test
    void primeProfile() {
        send("912345678")
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("PRIME"))
                .andExpect(xpath(path("RevolvingUtilization"), NS).evaluatesTo("0.080"))
                .andExpect(xpath(path("Inquiries6M"), NS).evaluatesTo(0))
                .andExpect(xpath(path("Delinquencies24M"), NS).evaluatesTo(0))
                .andExpect(xpath(path("OpenTradelines"), NS).evaluatesTo(12))
                .andExpect(xpath(path("FileAgeMonths"), NS).evaluatesTo(156))
                .andExpect(xpath(path("SsnIssuanceYear"), NS).evaluatesTo(1997))
                .andExpect(xpath(path("DeceasedIndicator"), NS).evaluatesTo("false"))
                .andExpect(xpath(path("FileAddress"), NS).evaluatesTo("48 Elm Street, Columbus OH"))
                .andExpect(xpath(path("PullType"), NS).evaluatesTo("HARD"));
    }

    @Test
    void nearPrimeProfile() {
        send("931234567")
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("NEAR_PRIME"))
                .andExpect(xpath(path("RevolvingUtilization"), NS).evaluatesTo("0.550"))
                .andExpect(xpath(path("Inquiries6M"), NS).evaluatesTo(3))
                .andExpect(xpath(path("Delinquencies24M"), NS).evaluatesTo(0))
                .andExpect(xpath(path("OpenTradelines"), NS).evaluatesTo(5))
                .andExpect(xpath(path("FileAgeMonths"), NS).evaluatesTo(40));
    }

    @Test
    void subprimeProfile() {
        send("961234567")
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("SUBPRIME"))
                .andExpect(xpath(path("RevolvingUtilization"), NS).evaluatesTo("0.820"))
                .andExpect(xpath(path("Inquiries6M"), NS).evaluatesTo(5))
                .andExpect(xpath(path("Delinquencies24M"), NS).evaluatesTo(2))
                .andExpect(xpath(path("OpenTradelines"), NS).evaluatesTo(4))
                .andExpect(xpath(path("FileAgeMonths"), NS).evaluatesTo(30));
    }

    @Test
    void thinFileProfile() {
        send("981234567")
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("THIN_FILE"))
                .andExpect(xpath(path("RevolvingUtilization"), NS).evaluatesTo("0.200"))
                .andExpect(xpath(path("Inquiries6M"), NS).evaluatesTo(1))
                .andExpect(xpath(path("Delinquencies24M"), NS).evaluatesTo(0))
                .andExpect(xpath(path("OpenTradelines"), NS).evaluatesTo(1))
                .andExpect(xpath(path("FileAgeMonths"), NS).evaluatesTo(10));
    }

    @Test
    void addressMismatchScenario() {
        send("937123456")
                .andExpect(xpath(path("FileAddress"), NS).evaluatesTo("14 Old Mill Rd, Dayton OH"))
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("NEAR_PRIME"));
    }

    @Test
    void ssnBeforeDobScenario() {
        // Third digit 8 → SSN_BEFORE_DOB (SPEC §8). DOB 1996 → issuance year 1996 − 3 = 1993.
        send("918345678")
                .andExpect(xpath(path("SsnIssuanceYear"), NS).evaluatesTo(1993))
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("PRIME"));
    }

    @Test
    void deceasedScenario() {
        // Third digit 9 → DECEASED (SPEC §8).
        send("919345678")
                .andExpect(xpath(path("DeceasedIndicator"), NS).evaluatesTo("true"))
                .andExpect(xpath(path("ProfileLabel"), NS).evaluatesTo("PRIME"));
    }

    @Test
    void invalidSsnFailsSchemaValidation() {
        client.sendRequest(withPayload(new StringSource(request("812345678", "HARD"))))
                .andExpect(clientOrSenderFault());
    }
}
