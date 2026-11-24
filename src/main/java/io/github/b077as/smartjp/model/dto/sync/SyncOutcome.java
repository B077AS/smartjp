package io.github.b077as.smartjp.model.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a merge or a replace did to the side that took the snapshot in: how many answers, lists and cards were added or changed
 * and how many lists and cards were removed (because the other side had deleted them). {@code backup} is the file the side saved
 * its old data to before a replace, or null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncOutcome {

    private long answers;
    private int lists;
    private int cards;
    private int removed;
    private String backup;
}
