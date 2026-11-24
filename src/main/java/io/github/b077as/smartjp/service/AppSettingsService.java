package io.github.b077as.smartjp.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.b077as.smartjp.model.db.AppSetting;
import io.github.b077as.smartjp.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;

/** Small named settings of this installation (see AppSetting): read, write, remove. */
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private final AppSettingRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findById(key).map(AppSetting::getValue);
    }

    @Transactional
    public void put(String key, String value) {
        repository.save(new AppSetting(key, value));
    }

    @Transactional
    public void remove(String key) {
        repository.deleteById(key);
    }
}
