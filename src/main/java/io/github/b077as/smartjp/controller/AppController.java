package io.github.b077as.smartjp.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.service.AppInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Website mode: the About tab's facts (the desktop app has AppRpc). */
@Slf4j
@RestController
@RequestMapping("/api/app")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class AppController {

    private final AppInfoService appInfoService;

    @GetMapping
    public ResponseEntity<?> info() {
        try {
            return ResponseEntity.ok(appInfoService.info());
        } catch (Exception e) {
            log.error("Failed to read the application info: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "app_error", "The application info could not be read.");
        }
    }
}
