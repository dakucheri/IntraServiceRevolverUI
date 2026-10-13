package org.example.revolverui.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogBuffer buffer;

    public LogController(LogBuffer buffer) {
        this.buffer = buffer;
    }

    @GetMapping
    public Map<String, Object> logs(@RequestParam(defaultValue = "0") long sinceId) {
        List<String> lines = buffer.getLogs(sinceId);
        String last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
        Map<String, Object> result = new HashMap<>();
        result.put("lines", lines);
        result.put("lastId", last == null ? sinceId
                : Long.parseLong(last.substring(0, last.indexOf('\t'))));
        return result;
    }
}