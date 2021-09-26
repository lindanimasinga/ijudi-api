package io.curiousoft.ijudi.ordermanagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

@Component
public class AppVersionChecker implements HandlerInterceptor {

    private int minSupportedVersion;

    public AppVersionChecker(@Value("${service.supportedApp.min}") String versionSupportedString) {
        this.minSupportedVersion = versionNumberAsInt(versionSupportedString);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception  {
        int appVersion = versionNumberAsInt(request.getHeader("app-version"));
        if(!allowUrl(request.getServletPath()) && appVersion < minSupportedVersion) {
            throw new ServletException("Please download a new version of iZinga from the app store.");
        }
        return true;
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
            case "/":
            case "/store":
            case "/device" :
            case "/promotion": return true;
            default: return false;

        }
    }
}
