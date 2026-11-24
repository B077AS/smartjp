package io.github.b077as.smartjp.model.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A custom card as it travels between the two sides. It keeps its id on both, because saved lists refer to it by that id. Times are ISO instants. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncCard {

    private String id;
    private String front;
    private String answer;
    private String createdAt;
    private String savedAt;
}
