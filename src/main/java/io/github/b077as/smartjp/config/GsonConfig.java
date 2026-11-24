package io.github.b077as.smartjp.config;

import java.util.Date;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/**
 * The project's only JSON library (Jackson is excluded from the build). Spring uses this bean for every HTTP body, and
 * Jolt uses it to read and write what crosses the bridge to the page (@JoltRpc). Null fields are written, like the hub does.
 */
@Configuration
public class GsonConfig {

    @Bean
    public Gson gson() {
        return new GsonBuilder()
                .serializeNulls()
                // Spring's own error bodies carry a java.util.Date: ISO text, not Gson's locale style
                .registerTypeAdapter(Date.class, (JsonSerializer<Date>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toInstant().toString()))
                .create();
    }
}
