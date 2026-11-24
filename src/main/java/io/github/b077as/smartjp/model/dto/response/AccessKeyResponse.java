package io.github.b077as.smartjp.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The website's access key, for its own Settings (website mode only). {@code fixed} means it was set from outside (SMARTJP_ACCESS_KEY) and cannot be renewed here. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessKeyResponse {

    private String key;
    private boolean fixed;
}
