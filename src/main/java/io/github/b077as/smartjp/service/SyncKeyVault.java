package io.github.b077as.smartjp.service;

import java.util.Optional;

/**
 * Where the desktop app keeps the server's access key: out of the database and out of the logs. The real one is the operating
 * system's keystore (SystemSyncKeyVault); a test uses a plain object. Methods may throw Jolt's CredentialStoreException.
 */
public interface SyncKeyVault {

    Optional<String> get();

    void put(String key);

    void delete();
}
