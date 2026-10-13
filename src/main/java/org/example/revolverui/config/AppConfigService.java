package org.example.revolverui.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AppConfigService {
    public static final String PARALLELISM = "APP_PARALLELISM";
    public static final String CHECK_INTERVAL_MS = "APP_CHECK_INTERVAL_MS";
    public static final String EXECUTOR_MAP = "LOGIN_TO_EXECUTOR_MAP";
    public static final String ENABLED = "APP_ENABLED";

    private final ConfigDao dao;
    private final ObjectMapper mapper = new ObjectMapper();

    public AppConfigService(ConfigDao dao) {
        this.dao = dao;
    }

    public Optional<String> get(String key) {
        try {
            return dao.get(key);
        } catch (Exception e) {
            return Optional.empty(); // БД недоступна — упадём на env/дефолты
        }
    }

    public int getInt(String key, int def) {
        String raw = get(key).orElse(null);
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void put(String key, String value) {
        dao.put(key, value);
    }

    public Map<String, String> getAll() {
        try {
            return dao.getAll();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Карта логин -> ExecutorId. Приоритет: БД -> env LOGIN_TO_EXECUTOR_* -> дефолты. */
    public Map<String, String> getExecutorMap() {
        String json = get(EXECUTOR_MAP).orElse(null);
        if (json != null) {
            try {
                return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
            } catch (Exception ignored) { }
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            if (e.getKey().startsWith("LOGIN_TO_EXECUTOR_")) {
                result.put(e.getKey().replace("LOGIN_TO_EXECUTOR_", ""), e.getValue());
            }
        }
        if (result.isEmpty()) {
            result.put("ikornilov", "319");
            result.put("employee-1", "329");
        }
        return result;
    }
}