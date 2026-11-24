package io.github.b077as.smartjp.rpc;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.request.SaveListRequest;
import io.github.b077as.smartjp.model.dto.response.SaveListResponse;
import io.github.b077as.smartjp.model.dto.summary.CardListSummary;
import io.github.b077as.smartjp.service.ListsService;
import lombok.RequiredArgsConstructor;

/**
 * Desktop app: the page's door onto the lists, as {@code jolt.call("lists.all")}, {@code "lists.save"} and
 * {@code "lists.delete"}. The website has ListsController (the same three actions over HTTP). Jolt writes the TypeScript
 * types of these methods and of the records into frontend/src/jolt-api.d.ts (start with -Djolt.rpc.typescript=...).
 */
@Component
@Profile("desktop")
@JoltRpc("lists")
@RequiredArgsConstructor
public class ListsRpc {

    private final ListsService listsService;

    @JoltRpc
    public List<CardListSummary> all() {
        return listsService.all();
    }

    @JoltRpc
    public SaveListResponse save(SaveListRequest request) {
        return listsService.save(request);
    }

    @JoltRpc
    public void delete(String id) {
        listsService.delete(id);
    }
}
