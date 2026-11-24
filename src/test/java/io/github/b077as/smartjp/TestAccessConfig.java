package io.github.b077as.smartjp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.b077as.smartjp.security.AccessKeyService;

/**
 * The website refuses /api without its access key, so every MockMvc request in the tests carries the real key of the test's own
 * application (made at its start) unless a test sets the header itself (AccessKeyTest does, to be refused).
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // not in the second, web-less context that SyncEndToEndTest starts as the "desktop"
class TestAccessConfig {

    @Bean
    MockMvcBuilderCustomizer sendTheAccessKey(AccessKeyService keys) {
        return builder -> builder.defaultRequest(get("/").header(AccessKeyService.HEADER, keys.key()));
    }
}
