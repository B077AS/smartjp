package io.github.b077as.smartjp.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.b077as.smartjp.model.dto.request.SaveAppearanceRequest;
import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.service.AppearanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Website mode: the page's door onto how this installation looks (the desktop app has AppearanceRpc). JSON only, like ListsController. */
@Slf4j
@RestController
@RequestMapping("/api/appearance")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class AppearanceController {

    private final AppearanceService appearanceService;

    @GetMapping
    public ResponseEntity<?> get() {
        try {
            return ResponseEntity.ok(appearanceService.get());
        } catch (Exception e) {
            log.error("Failed to read the appearance: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The appearance could not be read.");
        }
    }

    /** 200 also when the colour is refused: what came of it is in the answer's "result". */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> save(@RequestBody SaveAppearanceRequest request) {
        try {
            return ResponseEntity.ok(appearanceService.save(request));
        } catch (Exception e) {
            log.error("Failed to save the appearance: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The appearance could not be saved.");
        }
    }
}
