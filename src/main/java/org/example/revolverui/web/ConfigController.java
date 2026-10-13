package org.example.revolverui.web;

import org.example.revolverui.config.ConfigDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ConfigController {
    private final ConfigDao dao;

    public ConfigController(ConfigDao dao) {
        this.dao = dao;
    }

    @GetMapping("/api/config")
    public Map<String, String> get() {
        return dao.getAll();
    }

    @PostMapping("/api/config")
    public Map<String, String> put(@RequestBody Map<String, String> updates) {
        updates.forEach(dao::put);
        return dao.getAll();
    }
}