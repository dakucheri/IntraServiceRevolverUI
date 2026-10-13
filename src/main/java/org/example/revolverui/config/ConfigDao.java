package org.example.revolverui.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ConfigDao {
    private final JdbcTemplate jdbc;

    public ConfigDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        jdbc.query("SELECT config_key, config_value FROM app_config", rs -> {
            result.put(rs.getString(1), rs.getString(2));
        });
        return result;
    }

    public void put(String key, String value) {
        jdbc.update("""
                INSERT INTO app_config (config_key, config_value) VALUES (?, ?)
                ON CONFLICT (config_key)
                DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = now()
                """, key, value);
    }


    public Optional<String> get(String key) {
        List<String> rows = jdbc.queryForList(
                "SELECT config_value FROM app_config WHERE config_key = ?", String.class, key);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}