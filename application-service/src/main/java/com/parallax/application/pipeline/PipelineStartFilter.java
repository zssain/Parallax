package com.parallax.application.pipeline;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Stamps each request with its start nanos so VALIDATE can be measured to controller entry (SPEC §3). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PipelineStartFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(PipelineRecorder.START_ATTRIBUTE, System.nanoTime());
        chain.doFilter(request, response);
    }
}
