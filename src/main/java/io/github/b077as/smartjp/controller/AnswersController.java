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

import io.github.b077as.smartjp.model.dto.request.RecordAnswerRequest;
import io.github.b077as.smartjp.model.dto.response.ErrorResponse;
import io.github.b077as.smartjp.service.AnswersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Website mode: the page's door onto the answer log (the desktop app has AnswersRpc). JSON only, like ListsController. */
@Slf4j
@RestController
@RequestMapping("/api/answers")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class AnswersController {

    private final AnswersService answersService;

    /** How often each card was answered and how often correctly. */
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        try {
            return ResponseEntity.ok(answersService.stats());
        } catch (Exception e) {
            log.error("Failed to read the answer totals: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The answers could not be read.");
        }
    }

    /** 200 also when the answer is refused: what came of it is in the answer's "result". */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> record(@RequestBody RecordAnswerRequest request) {
        try {
            return ResponseEntity.ok(answersService.record(request));
        } catch (Exception e) {
            log.error("Failed to log an answer: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error", "The answer could not be saved.");
        }
    }
}
