package com.intentos.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiAuthenticationInterceptor implements HandlerInterceptor {
    private final boolean required;

    public ApiAuthenticationInterceptor(@Value("${intentos.auth.required:false}") boolean required) {
        this.required = required;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE);
        if (userId instanceof String authenticatedUserId) {
            request.setAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE, authenticatedUserId);
            if (isUnsafeMethod(request.getMethod())) {
                Object expected = session.getAttribute(PayPalAuthController.CSRF_SESSION_ATTRIBUTE);
                String provided = request.getHeader("X-CSRF-Token");
                if (!(expected instanceof String expectedToken) || provided == null || !MessageDigest.isEqual(
                        expectedToken.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"CSRF_TOKEN_INVALID\"}");
                    return false;
                }
            }
            return true;
        }
        if (!required) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"PAYPAL_LOGIN_REQUIRED\"}");
        return false;
    }

    private static boolean isUnsafeMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }
}
