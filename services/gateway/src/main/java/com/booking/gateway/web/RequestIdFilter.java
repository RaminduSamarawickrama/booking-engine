package com.booking.gateway.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id the services log and echo back. A client-supplied id is kept
 * only if it is safe to log; otherwise a new one replaces it before the request is routed.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = supplied != null && SAFE.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        response.setHeader(HEADER, id);
        MDC.put("requestId", id);
        try {
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(java.util.List.of(id))
                            : super.getHeaders(name);
                }

                @Override
                public Enumeration<String> getHeaderNames() {
                    java.util.Set<String> names = new java.util.LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                    names.add(HEADER);
                    return Collections.enumeration(names);
                }
            }, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
