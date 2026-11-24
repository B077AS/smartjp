package io.github.b077as.smartjp.model.dto.request;

import java.util.List;

/** Save these cards under this name. Deliberately without @NonNull: a missing name must reach the service and be answered "empty_name". */
public record SaveListRequest(String name, List<String> cards) {
}
