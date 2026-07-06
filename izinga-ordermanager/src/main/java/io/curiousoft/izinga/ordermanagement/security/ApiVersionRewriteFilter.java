package io.curiousoft.izinga.ordermanagement.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiVersionRewriteFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionRewriteFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();
        log.info("[v2filter] incoming path={} method={}", path, req.getMethod());
        if (path.startsWith("/v2/")) {
            String rewritten = path.substring(3);
            log.info("[v2filter] forwarding {} -> {}", path, rewritten);
            request.getRequestDispatcher(rewritten).forward(request, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}