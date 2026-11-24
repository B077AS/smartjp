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

import io.github.b077as.smartjp.model.dto.response.AccessKeyResponse;
import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import io.github.b077as.smartjp.security.AccessKeyService;
import io.github.b077as.smartjp.service.SyncService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Website mode: this server as the other side of a sync. A desktop app starts the sync (this side never calls out), reading the
 * summary and the snapshot and sending its own to merge or to replace this side. The website's own Settings reads and renews the
 * access key here. Everything is behind the access key (AccessKeyFilter) and takes JSON only, like the rest of /api.
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final AccessKeyService keys;

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        try {
            return ResponseEntity.ok(syncService.summary());
        } catch (Exception e) {
            log.error("Failed to read the sync summary: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The summary could not be read.");
        }
    }

    @GetMapping("/snapshot")
    public ResponseEntity<?> snapshot() {
        try {
            return ResponseEntity.ok(syncService.snapshot());
        } catch (Exception e) {
            log.error("Failed to read the sync snapshot: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The data could not be read.");
        }
    }

    /** The desktop sends what it has; this side takes in what it is missing and answers what that was. */
    @PostMapping(value = "/merge", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> merge(@RequestBody SyncSnapshot snapshot) {
        try {
            return ResponseEntity.ok(syncService.mergeIn(snapshot));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "bad_snapshot", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to merge a sync: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The data could not be merged.");
        }
    }

    /** The desktop overwrites this side. Refused unless all four parts are there, so a broken request can never empty the server. */
    @PostMapping(value = "/replace", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> replace(@RequestBody SyncSnapshot snapshot) {
        if (!SyncService.isComplete(snapshot)) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "bad_snapshot", "A snapshot to replace with must list answers, lists, cards and deletions (they may be empty).");
        }
        try {
            return ResponseEntity.ok(syncService.replaceWith(snapshot));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "bad_snapshot", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to replace the data in a sync: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The data could not be replaced.");
        }
    }

    @GetMapping("/key")
    public ResponseEntity<?> key() {
        return ResponseEntity.ok(new AccessKeyResponse(keys.key(), keys.isFixed()));
    }

    /** A new key. The browser that asked keeps working (it gets the new cookie); every other browser and desktop app must enter the new key. */
    @PostMapping("/key/renew")
    public ResponseEntity<?> renewKey(HttpServletRequest request) {
        if (!keys.renew()) {
            return ErrorResponse.of(HttpStatus.CONFLICT, "fixed_key", "This key is set from outside (SMARTJP_ACCESS_KEY) and cannot be renewed here.");
        }
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, keys.cookieHeader(request.isSecure())).body(new AccessKeyResponse(keys.key(), false));
    }
}
