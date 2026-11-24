package io.github.b077as.smartjp.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built front end from the class path (static/; "npm run build" in frontend/ writes it into this module's resources).
 * <ul>
 *   <li>The Vue app is a single-page app with its own router, and its pages are reached by typing or clicking an address
 *       (a bookmark, a reload). So an address that is not a file and not /api answers
 *       with index.html and the router takes over. No list of pages is kept here: the router is the only place that knows
 *       them. A missing FILE (an address whose last part has a dot, like /missing.js) is a plain 404: the browser must never
 *       get a page where it asked for a script.</li>
 *   <li>Vite puts a hash in every file name under /assets, so those files never change and may be cached for a year (the
 *       1 MB kana fonts are among them). Everything else is revalidated each time (see SecurityHeadersFilter).</li>
 * </ul>
 * Spring Boot's own default handling of static files is switched off (spring.web.resources.add-mappings=false) so this is
 * the only place that decides.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebConfig implements WebMvcConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";

    /** The site root has an empty path, which Spring's resource handling never resolves: send it to the front page. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC_LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource file = location.createRelative(resourcePath);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        String lastPart = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                        boolean isPage = !resourcePath.startsWith("api/") && !lastPart.contains(".");
                        return isPage ? location.createRelative("index.html") : null;
                    }
                });
    }
}
