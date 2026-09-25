# Prompt 05 of 23 — SOAP credit bureau (contract and mock)

## Context

Parallax pulls a credit report for every application from a credit bureau over **SOAP/XML** (the job description asks for SOAP, XML and JSON). Real bureaus are out of scope, so `bureau-mock` simulates one, driven by synthetic SSN digits, with fault injection for the resilience demo. `bureau-contract` holds the XSD and the generated JAXB classes shared by the mock (server) and application-service (client, Prompt 07). The UI's Decision detail screen shows the raw SOAP response, so the element names below match the prototype exactly.

## Session rules

1. `git log --oneline -3`, `./mvnw -q -B verify`. Red → stop.
2. Read `AGENTS.md`, `docs/SPEC.md` §8, `docs/DECISIONS.md`.
3. Verify plugin and dependency versions resolve before use; log them.
4. Build exactly this. Real output only.

## Build

### 1. bureau-contract

`src/main/resources/xsd/bureau.xsd`, `targetNamespace="urn:parallax:bureau:v1"`, `elementFormDefault="qualified"`, prefix `br` in examples.

- `CreditReportRequest`: `Ssn` (string, pattern `9[0-9]{8}`), `FirstName`, `LastName` (string 1–60), `DateOfBirth` (xs:date), `Address` (string 1–200), `PullType` (enum HARD | SOFT).
- `CreditReportResponse`: `PullId`, `PullType`, `FileAddress`, `SsnIssuanceYear` (int), `DeceasedIndicator` (boolean), `OpenTradelines` (int), `Inquiries6M` (int), `Delinquencies24M` (int), `RevolvingUtilization` (decimal, fractionDigits 3), `FileAgeMonths` (int), `ProfileLabel` (string; synthetic demo metadata: PRIME | NEAR\_PRIME | SUBPRIME | THIN\_FILE).
- Generate Jakarta XML Binding classes with `org.codehaus.mojo:jaxb2-maven-plugin` 3.x (goal `xjc`) into package `com.parallax.bureau.contract`. Dependencies: `jakarta.xml.bind:jakarta.xml.bind-api` and `org.glassfish.jaxb:jaxb-runtime` (versions managed by Spring Boot). The XSD stays on the classpath at `xsd/bureau.xsd`.

### 2. bureau-mock (Spring Boot, port 8082)

Dependencies: `spring-boot-starter-web-services`, `wsdl4j`, `bureau-contract`, actuator.

- `WebServiceConfig`: `MessageDispatcherServlet` at `/ws/*` with `transformWsdlLocations=true`; `DefaultWsdl11Definition` bean named `bureau` (portType `BureauPort`, locationUri `/ws`, targetNamespace as above, schema from `xsd/bureau.xsd`) so the WSDL is at `/ws/bureau.wsdl`; `PayloadValidatingInterceptor` with the XSD, `validateRequest=true`, `validateResponse=true`.
- `BureauEndpoint` (`@Endpoint`, `@PayloadRoot(namespace, "CreditReportRequest")`):
  - Profile from the SSN's **second** digit and scenario from its **third** digit, exactly as SPEC §8 (put the mapping in `SyntheticProfiles`, unit-tested on its own).
  - Profile values: PRIME 0.080/0/0/12/156; NEAR\_PRIME 0.550/3/0/5/40; SUBPRIME 0.820/5/2/4/30; THIN\_FILE 0.200/1/0/1/10 (utilization / inquiries / delinquencies / tradelines / file age months).
  - Scenario: ADDRESS\_MISMATCH → FileAddress “14 Old Mill Rd, Dayton OH”; SSN\_BEFORE\_DOB → SsnIssuanceYear = birth year − 3; DECEASED → DeceasedIndicator true. Otherwise FileAddress = request Address, SsnIssuanceYear = birth year + 1, DeceasedIndicator false.
  - PullId = “BP-” + first 8 hex characters, uppercase, of SHA-256(ssn + pullType + counter), counter an `AtomicLong`. PullType echoed. ProfileLabel = profile name.
- Fault injection (**dev profile only**): `FaultController` at `/admin/fault`: `GET` returns `{mode, delayMs}`; `POST` accepts `{"mode":"NONE"|"DOWN"|"SLOW","delayMs":int}`. State in an `AtomicReference<FaultState>`. A `OncePerRequestFilter` on `/ws/*`: DOWN → respond HTTP **503** with a SOAP 1.1 Server fault envelope (faultstring “Bureau unavailable (injected)”) without calling the endpoint; SLOW → sleep delayMs, then continue. Default NONE. `application-dev.yml` enables the `dev` profile beans; the default profile does not expose `/admin`.
- Logging: log only pullId, profile and scenario. Never the SSN, name, DOB or address.

### 3. Tests

- `SyntheticProfilesTest`: every digit 0–9 for position 2 and 3 maps as SPEC §8.
- `BureauEndpointTest` with `MockWebServiceClient`: one test per profile asserting every response field; one per scenario (ADDRESS\_MISMATCH, SSN\_BEFORE\_DOB, DECEASED); invalid SSN `812345678` → Client fault (schema validation).
- `FaultIT` (`@SpringBootTest(webEnvironment=RANDOM_PORT)`, profile dev): POST DOWN via `TestRestTemplate`, then POST a SOAP envelope to `/ws` → status 503 and body contains `faultstring`; POST SLOW 300 → the call takes ≥ 300 ms; POST NONE → 200.
- `WsdlIT`: `GET /ws/bureau.wsdl` returns 200 and contains `CreditReportRequest`.

### 4. Docs

README “Bureau mock” section: the SSN digit table from SPEC §8 and this sample request:

```bash
curl -s -H 'Content-Type: text/xml' http://localhost:8082/ws -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:br="urn:parallax:bureau:v1"><soap:Body><br:CreditReportRequest><br:Ssn>912345678</br:Ssn><br:FirstName>Ishaan</br:FirstName><br:LastName>Kapoor</br:LastName><br:DateOfBirth>1996-04-18</br:DateOfBirth><br:Address>48 Elm Street, Columbus OH</br:Address><br:PullType>HARD</br:PullType></br:CreditReportRequest></soap:Body></soap:Envelope>'
```

## Definition of Done (real output)

1. `./mvnw -B verify` green (paste Reactor Summary).
2. `./mvnw -pl bureau-mock spring-boot:run -Dspring-boot.run.profiles=dev` in the background; run the curl above and paste the response (expect RevolvingUtilization 0.080, OpenTradelines 12, ProfileLabel PRIME). Then `curl -s -XPOST localhost:8082/admin/fault -H 'Content-Type: application/json' -d '{"mode":"DOWN","delayMs":0}'`, repeat the SOAP curl with `-o /dev/null -w '%{http_code}'` (expect 503), reset to NONE, stop the app.
3. Tick 05. Commit `PX-5: SOAP bureau contract and mock with fault injection`. REPORT.

## Do not

Add persistence, authentication or anything beyond this list.
