package com.intentos.auth;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/auth")
public class PayPalAuthController {
    public static final String USER_ID_SESSION_ATTRIBUTE = "paypalUserId";
    public static final String CSRF_SESSION_ATTRIBUTE = "intentOsCsrfToken";
    private static final String STATE_SESSION_ATTRIBUTE = "paypalLoginState";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String clientId;
    private final String clientSecret;
    private final String environment;
    private final String redirectUri;
    private final String frontendUrl;
    private final RestClient restClient;

    public PayPalAuthController(
            @Value("${paypal.login.client-id:${paypal.client-id:}}") String clientId,
            @Value("${paypal.login.client-secret:${paypal.client-secret:}}") String clientSecret,
            @Value("${paypal.environment:sandbox}") String environment,
            @Value("${paypal.login.redirect-uri:http://localhost:8080/api/auth/paypal/callback}") String redirectUri,
            @Value("${intentos.frontend-url:http://localhost:5173}") String frontendUrl,
            RestClient.Builder restClientBuilder) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.environment = environment;
        this.redirectUri = redirectUri;
        this.frontendUrl = frontendUrl;
        this.restClient = restClientBuilder.build();
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(USER_ID_SESSION_ATTRIBUTE) == null) {
            return Map.of("authenticated", false, "loginConfigured", isConfigured());
        }
        Object csrfToken = session.getAttribute(CSRF_SESSION_ATTRIBUTE);
        if (!(csrfToken instanceof String)) {
            csrfToken = randomState();
            session.setAttribute(CSRF_SESSION_ATTRIBUTE, csrfToken);
        }
        return Map.of("authenticated", true,
                "loginConfigured", isConfigured(),
                "userId", session.getAttribute(USER_ID_SESSION_ATTRIBUTE),
                "name", session.getAttribute("paypalUserName"),
                "email", session.getAttribute("paypalUserEmail"),
                "csrfToken", csrfToken);
    }

    @GetMapping("/paypal")
    public ResponseEntity<Void> beginLogin(HttpServletRequest request) {
        requireConfigured();
        String state = randomState();
        request.getSession(true).setAttribute(STATE_SESSION_ATTRIBUTE, state);
        String webHost = isLive() ? "https://www.paypal.com" : "https://www.sandbox.paypal.com";
        String authorizationUrl = UriComponentsBuilder.fromUriString(webHost + "/connect")
                .queryParam("flowEntry", "static")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(authorizationUrl)).build();
    }

    @GetMapping("/paypal/callback")
    public ResponseEntity<Void> completeLogin(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object expectedState = session == null ? null : session.getAttribute(STATE_SESSION_ATTRIBUTE);
        if (state == null || !(expectedState instanceof String expected) || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayPal login state is missing or invalid");
        }

        if (error != null) {
            session.removeAttribute(STATE_SESSION_ATTRIBUTE);
            String errorCode = "access_denied".equals(error) ? "access_denied" : "login_failed";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(UriComponentsBuilder.fromUriString(frontendUrl)
                            .queryParam("paypalLoginError", errorCode)
                            .build().toUri())
                    .build();
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayPal did not return an authorization code");
        }

        JsonNode profile;
        try {
            profile = fetchProfile(code);
        } catch (RestClientException exception) {
            return loginErrorRedirect("login_failed");
        }
        if (profile == null) return loginErrorRedirect("login_failed");
        String userId = profile.path("user_id").asText(profile.path("sub").asText());
        if (userId.isBlank()) return loginErrorRedirect("login_failed");

        request.changeSessionId();
        session = request.getSession(true);
        session.removeAttribute(STATE_SESSION_ATTRIBUTE);
        session.setAttribute(USER_ID_SESSION_ATTRIBUTE, userId);
        session.setAttribute(CSRF_SESSION_ATTRIBUTE, randomState());
        session.setAttribute("paypalUserName", profile.path("name").asText("PayPal user"));
        session.setAttribute("paypalUserEmail", profile.path("email").asText(""));
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(frontendUrl)).build();
    }

    private ResponseEntity<Void> loginErrorRedirect(String errorCode) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(UriComponentsBuilder.fromUriString(frontendUrl)
                        .queryParam("paypalLoginError", errorCode)
                        .build().toUri())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    private JsonNode fetchProfile(String code) {
        requireConfigured();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        JsonNode token = restClient.post()
                .uri(apiHost() + "/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JsonNode.class);
        if (token == null || token.path("access_token").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return a login access token");
        }
        return restClient.get()
                .uri(apiHost() + "/v1/identity/openidconnect/userinfo?schema=paypalv1.1")
                .headers(headers -> headers.setBearerAuth(token.path("access_token").asText()))
                .retrieve().body(JsonNode.class);
    }

    private boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayPal Login is not configured");
    }

    private boolean isLive() {
        return "live".equalsIgnoreCase(environment);
    }

    private String apiHost() {
        return isLive() ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    private static String randomState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
