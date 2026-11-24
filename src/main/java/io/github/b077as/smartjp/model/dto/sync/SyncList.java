package io.github.b077as.smartjp.model.dto.sync;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A saved list as it travels between the two sides. Its name is its identity (the same name, ignoring case, is the same list). Times are ISO instants. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncList {

    private String name;
    private List<String> cards;
    private String createdAt;
    private String savedAt;
}
