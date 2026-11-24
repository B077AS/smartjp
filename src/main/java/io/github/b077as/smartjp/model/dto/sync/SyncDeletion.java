package io.github.b077as.smartjp.model.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** "This list (key = its lower-case name) or card (key = its id) was deleted at this time." */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncDeletion {

    private String kind;
    private String key;
    private String deletedAt;
}
