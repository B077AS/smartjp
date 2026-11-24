package io.github.b077as.smartjp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import io.github.b077as.smartjp.model.dto.summary.AppInfoSummary;
import lombok.RequiredArgsConstructor;

/** The facts of this running copy for the About tab. Nothing here is secret, but it is still behind the website's access key like the rest of /api. */
@Service
@RequiredArgsConstructor
public class AppInfoService {

    /** Filled in by Maven (application.properties: smartjp.version=@project.version@); a run that never went through Maven keeps the raw placeholder. */
    @Value("${smartjp.version:dev}")
    private String version;

    private final Environment environment;

    public AppInfoSummary info() {
        boolean released = version != null && !version.isBlank() && !version.startsWith("@");
        return new AppInfoSummary(
                released ? version : "dev",
                System.getProperty("os.name", "unknown"),
                System.getProperty("java.version", "unknown"),
                environment.acceptsProfiles(Profiles.of("desktop")) ? "desktop" : "website");
    }
}
