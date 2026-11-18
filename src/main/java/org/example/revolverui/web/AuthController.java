package org.example.revolverui.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AuthController {

    @GetMapping("/api/auth/me")
    public Map<String, Object> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth != null ? auth.getName() : "anonymous";
        List<String> authorities = auth != null
                ? auth.getAuthorities().stream().map(Object::toString).toList()
                : List.of();
        return Map.of("name", name, "authorities", authorities);
    }
}