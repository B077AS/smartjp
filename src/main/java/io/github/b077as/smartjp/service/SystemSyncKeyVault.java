package io.github.b077as.smartjp.service;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.credentials.CredentialStore;

/**
 * The access key in the operating system's keystore (Windows Credential Manager, Linux Secret Service) through jolt-credentials.
 * It protects against other users of the machine and against someone reading the disk, not against a program running as the same
 * user (Jolt's own note). Opening it fails with CredentialStoreUnavailableException where there is no keystore (a Linux without a
 * Secret Service); the desktop service turns that into the result "no_keystore".
 */
@Component
@Profile("desktop")
public class SystemSyncKeyVault implements SyncKeyVault {

    private static final String SERVICE = "io.github.b077as.smartjp";
    private static final String ACCOUNT = "sync-access-key";

    private CredentialStore store;

    @Override
    public Optional<String> get() {
        return store().get(ACCOUNT);
    }

    @Override
    public void put(String key) {
        store().put(ACCOUNT, key);
    }

    @Override
    public void delete() {
        store().delete(ACCOUNT);
    }

    private synchronized CredentialStore store() {
        if (store == null) {
            store = CredentialStore.open(SERVICE);
        }
        return store;
    }
}
