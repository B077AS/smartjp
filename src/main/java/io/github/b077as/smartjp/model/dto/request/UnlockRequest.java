package io.github.b077as.smartjp.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The access key as typed on the website's unlock page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockRequest {

    private String key;
}
