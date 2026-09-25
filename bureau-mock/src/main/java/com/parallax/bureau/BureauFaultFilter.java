package com.parallax.bureau;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Dev-only filter on /ws/*: injects a bureau outage (DOWN → 503 SOAP fault) or latency (SLOW). */
@Component
@Profile("dev")
public class BureauFaultFilter extends OncePerRequestFilter {

    private static final String SOAP_FAULT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:Server</faultcode>
                  <faultstring>Bureau unavailable (injected)</faultstring>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """;

    private final FaultStore store;

    public BureauFaultFilter(FaultStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/ws");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FaultState fault = store.get();
        if ("DOWN".equals(fault.mode())) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("text/xml; charset=UTF-8");
            response.getWriter().write(SOAP_FAULT);
            return;
        }
        if ("SLOW".equals(fault.mode()) && fault.delayMs() > 0) {
            try {
                Thread.sleep(fault.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        chain.doFilter(request, response);
    }
}
