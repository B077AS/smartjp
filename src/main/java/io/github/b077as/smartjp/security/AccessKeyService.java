package io.github.b077as.smartjp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import io.github.b077as.smartjp.service.AppSettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * The website's one secret: a random 256-bit access key, made at its first start and kept in the database (so it can be shown in
 * the website's own Settings and copied into the desktop app). Whoever has it may use the API: a desktop app sends it in a header, a
 * browser trades it once for a cookie. There are no accounts. {@code smartjp.access-key} (environment SMARTJP_ACCESS_KEY) sets it
 * from outside instead, for a host that wants to manage it; it must then be long enough to be unguessable.
 * <p>
 * The cookie holds a SHA-256 of the key, not the key, and is HttpOnly and SameSite=Strict (and Secure over HTTPS). Keys are compared
 * as digests with {@link MessageDigest#isEqual}, which does not stop early at the first different byte.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccessKeyService {

    public static final String HEADER = "X-SmartJP-Key";
    public static final String COOKIE = "smartjp_key";
    private static final String SETTING = "access_key";
    private static final int MIN_FIXED_LENGTH = 20;
    private static final int ONE_YEAR = 365 * 24 * 60 * 60;

    private final AppSettingsService settings;
    private final String fixedKey;
    private final SecureRandom random = new SecureRandom();

    private volatile String key;
    private volatile String cookieValue;

    public AccessKeyService(AppSettingsService settings, @Value("${smartjp.access-key:}") String fixedKey) {
        this.settings = settings;
        this.fixedKey = fixedKey == null ? "" : fixedKey.strip();
    }

    @PostConstruct
    void load() {
        if (!fixedKey.isEmpty()) {
            if (fixedKey.length() < MIN_FIXED_LENGTH) {
                throw new IllegalStateException("smartjp.access-key (SMARTJP_ACCESS_KEY) must be at least " + MIN_FIXED_LENGTH + " characters: it is the only thing that protects your data.");
            }
            use(fixedKey);
            log.info("The website is locked with the access key from SMARTJP_ACCESS_KEY.");
            return;
        }
        String stored = settings.get(SETTING).orElse(null);
        if (stored == null || stored.isBlank()) {
            stored = newKey();
            settings.put(SETTING, stored);
            log.info("Made a new access key.");
        }
        use(stored);
        // Printed on purpose: the owner needs it once to unlock the website and to pair a desktop app, and this is the one place a
        // person who just started the server is already looking. It is also in Settings, Sync, once unlocked.
        log.info("Access key (enter it on the website, and in the desktop app's Settings, Sync): {}", stored);
    }

    public boolean isFixed() {
        return !fixedKey.isEmpty();
    }

    public String key() {
        return key;
    }

    /** True when the request carries the key in the header (the desktop app) or the cookie (a browser that unlocked). */
    public boolean isUnlocked(HttpServletRequest request) {
        if (matchesKey(request.getHeader(HEADER))) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE.equals(cookie.getName()) && matchesCookie(cookie.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean matchesKey(String candidate) {
        return candidate != null && MessageDigest.isEqual(sha256(candidate), sha256(key));
    }

    private boolean matchesCookie(String value) {
        return value != null && MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), cookieValue.getBytes(StandardCharsets.UTF_8));
    }

    /** The Set-Cookie line that unlocks a browser. */
    public String cookieHeader(boolean secure) {
        return COOKIE + "=" + cookieValue + "; Path=/; Max-Age=" + ONE_YEAR + "; HttpOnly; SameSite=Strict" + (secure ? "; Secure" : "");
    }

    /** A new key; the old one (and every browser that has it) stops working at once. Not possible for a key set from outside. */
    public synchronized boolean renew() {
        if (isFixed()) {
            return false;
        }
        String fresh = newKey();
        settings.put(SETTING, fresh);
        use(fresh);
        log.info("Made a new access key. The old one no longer works.");
        return true;
    }

    private void use(String value) {
        this.cookieValue = HexFormat.of().formatHex(sha256(value));
        this.key = value;
    }

    private String newKey() {
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java has SHA-256
        }
    }
}
