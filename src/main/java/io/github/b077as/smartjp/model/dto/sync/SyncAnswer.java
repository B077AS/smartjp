package io.github.b077as.smartjp.model.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One logged answer as it travels between the two sides; the id is what makes the same event arrive only once. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncAnswer {

    private String id;
    private String cardId;
    private boolean correct;
    private long ms;
    private String mode;
    private String answeredAt;
}
