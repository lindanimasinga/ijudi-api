package io.curiousoft.izinga.ordermanagement.security;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
public class ApiVersionRewriteFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();
        if (path.startsWith("/v2/")) {
            request.getRequestDispatcher(path.substring(3)).forward(request, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}