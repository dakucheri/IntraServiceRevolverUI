package org.example.revolverui.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AppConfigService {
    public static final String PARALLELISM = "APP_PARALLELISM";
    public static final String CHECK_INTERVAL_MS = "APP_CHECK_INTERVAL_MS";
    public static final String EXECUTOR_MAP = "LOGIN_TO_EXECUTOR_MAP";
    public static final String ENABLED = "APP_ENABLED";
    public static final String GROUP1_SERVICE_IDS = "GROUP1_SERVICE_IDS";
    public static final String GROUP2_SERVICE_IDS = "GROUP2_SERVICE_IDS";
    public static final String DEFAULT_GROUP1_SERVICES = "65,64,63,62,61,50,49,46,48,45,42,41,44,43,32,22,21,20,17";
    public static final String DEFAULT_GROUP2_SERVICES = "47,19,65";
    public static final String MODE = "APP_MODE";
    public static final String LINE2_EMPLOYEES = "LINE2_EMPLOYEES";

    public String getMode() {
        String m = System.getenv(MODE);
        return (m == null || m.isBlank()) ? "LINE1" : m;
    }

    /** Логин -> {executorId, enabled}. */
    public Map<String, Map<String, String>> getLine2Employees() {
        String json = get(LINE2_EMPLOYEES).orElse("{}");
        try {
            return mapper.readValue(json,
                    new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (Exception e) {
            logger.error("Не удалось разобрать LINE2_EMPLOYEES", e);
            return new LinkedHashMap<>();
        }
    }

    private final ConfigDao dao;
    private final ObjectMapper mapper = new ObjectMapper();

    private final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(AppConfigService.class);

    public String getServiceIds(String key, String defaultValue) {
        return get(key).filter(v -> !v.isBlank()).orElse(defaultValue);
    }

    public AppConfigService(ConfigDao dao) {
        this.dao = dao;
    }

    public Optional<String> get(String key) {
        try {
            return dao.get(key);
        } catch (Exception e) {
            return Optional.empty();
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
    public static final String SERVICE_MAP = "SERVICE_MAP";

    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** Карта название сервиса -> ID Сервиса. Приоритет: БД -> env SERVICE_TO_ID_* -> дефолты. */
    public Map<String, String> getServiceMap() {
        // 1. БД — правки через UI имеют высший приоритет
        String json = get(SERVICE_MAP).orElse(null);
        if (json != null) {
            try {
                return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
            } catch (Exception ignored) { }
        }

        // 2. env-переменные SERVICE_TO_ID_<НАЗВАНИЕ>
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            if (e.getKey().startsWith("SERVICE_TO_ID_")) {
                result.put(e.getKey().replace("SERVICE_TO_ID_", ""), e.getValue());
            }
        }

        // 3. Дефолты (подставь свои реальные пары)
        if (result.isEmpty()) {
            result.put("Доступы", "65");
            result.put("Прочее", "17");
        }
        return result;
    }
}