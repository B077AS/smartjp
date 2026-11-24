package io.github.b077as.smartjp.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.b077as.smartjp.model.dto.request.UnlockRequest;
import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.model.dto.response.SessionResponse;
import io.github.b077as.smartjp.security.AccessKeyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Website mode: how a browser gets past the access key. It sends the key once and gets a cookie (HttpOnly, SameSite=Strict) that
 * the filter accepts from then on. The only part of /api that works without the key.
 */
@RestController
@RequestMapping("/api/session")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SessionController {

    private final AccessKeyService keys;

    /** Does this browser already have the key? The page asks once at start. */
    @GetMapping
    public ResponseEntity<?> status(HttpServletRequest request) {
        return ResponseEntity.ok(new SessionResponse(keys.isUnlocked(request)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> unlock(@RequestBody UnlockRequest body, HttpServletRequest request) {
        if (body == null || !keys.matchesKey(body.getKey() == null ? null : body.getKey().strip())) {
            return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "bad_key", "That is not this server's access key.");
        }
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, keys.cookieHeader(request.isSecure())).body(new SessionResponse(true));
    }
}
