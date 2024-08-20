package io.curiousoft.izinga.ordermanagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Component
public class APIUnderMaintenanceChecker implements HandlerInterceptor {

    private boolean isUnderMaintenance;

    public APIUnderMaintenanceChecker(@Value("${service.maintenance.enabled}") boolean isUnderMaintenance) {
        this.isUnderMaintenance = isUnderMaintenance;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception  {
        int appVersion = versionNumberAsInt(request.getHeader("app-version"));
        if(!isOptionsMethod(request.getMethod()) && !allowUrl(request.getServletPath()) && isUnderMaintenance) {
            throw new ServletException("iZinga is undergoing maintenance. Please try again later.");
        }
        return true;
    }

    private boolean isOptionsMethod(String method) {
        return "options".equalsIgnoreCase(method);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
    }

    private int versionNumberAsInt(String versionSupportedString) {
        if(versionSupportedString == null) return 0;

        versionSupportedString = Arrays.stream(versionSupportedString.split("\\.")).reduce((a,b) -> a+b).get();
        return Integer.parseInt(versionSupportedString);
    }

    private boolean allowUrl(String path) {
        switch (path) {
            case "/order": return false;
            default: return true;

        }
    }
}
