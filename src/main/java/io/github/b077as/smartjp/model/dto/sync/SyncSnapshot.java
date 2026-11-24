package io.github.b077as.smartjp.model.dto.sync;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything one side holds that the other may want: what two sides exchange to merge, or what one side is replaced with.
 * Rows travel, never the database file (a live SQLite file can be copied half-written). Gson reads these classes, so a missing
 * list arrives as null and the store treats it as empty.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncSnapshot {

    private List<SyncList> lists;
    private List<SyncCard> customCards;
    private List<SyncAnswer> answers;
    private List<SyncDeletion> deletions;
}
