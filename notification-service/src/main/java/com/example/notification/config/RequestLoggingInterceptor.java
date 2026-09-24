package com.example.notification.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// Logs every incoming request's endpoint, resolved handler and authenticated user to the app's own logs
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String handlerInfo = handler instanceof HandlerMethod hm ? hm.getShortLogMessage() : handler.toString();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";

        log.info("Incoming request: {} {}{} -> {} (user={})",
                request.getMethod(), uri, query != null ? "?" + query : "", handlerInfo, user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        log.info("Completed request: {} {} -> status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
    }
}
