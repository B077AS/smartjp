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

import io.github.b077as.smartjp.model.dto.request.SaveListRequest;
import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.service.ListsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Website mode: the page's door onto the lists (the desktop app has ListsRpc instead). A save is only accepted as
 * application/json: a web page on another site cannot send that without the browser asking this server first (and this
 * server never says yes), so a stranger's page cannot save or delete lists here through the user's browser.
 */
@Slf4j
@RestController
@RequestMapping("/api/lists")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class ListsController {

    private final ListsService listsService;

    @GetMapping
    public ResponseEntity<?> all() {
        try {
            return ResponseEntity.ok(listsService.all());
        } catch (Exception e) {
            log.error("Failed to read the lists: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The lists could not be read.");
        }
    }

    /** 200 also when the save is refused: what came of it is in the answer's "result" (a refused save is not a server error). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> save(@RequestBody SaveListRequest request) {
        try {
            return ResponseEntity.ok(listsService.save(request));
        } catch (Exception e) {
            log.error("Failed to save a list: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The list could not be saved.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            listsService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete a list: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The list could not be deleted.");
        }
    }
}
