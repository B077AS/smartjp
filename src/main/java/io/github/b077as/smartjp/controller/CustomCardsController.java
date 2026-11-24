package io.github.b077as.smartjp.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.b077as.smartjp.model.dto.request.SaveCustomCardRequest;
import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.service.CustomCardsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Website mode: the page's door onto the cards the user wrote (the desktop app has CustomCardsRpc). Same rules as ListsController: JSON only. */
@Slf4j
@RestController
@RequestMapping("/api/custom-cards")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class CustomCardsController {

    private final CustomCardsService customCardsService;

    @GetMapping
    public ResponseEntity<?> all() {
        try {
            return ResponseEntity.ok(customCardsService.all());
        } catch (Exception e) {
            log.error("Failed to read the custom cards: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The cards could not be read.");
        }
    }

    /** 200 also when the save is refused: what came of it is in the answer's "result". */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> save(@RequestBody SaveCustomCardRequest request) {
        try {
            return ResponseEntity.ok(customCardsService.save(request));
        } catch (Exception e) {
            log.error("Failed to save a custom card: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The card could not be saved.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            customCardsService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete a custom card: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The card could not be deleted.");
        }
    }
}
