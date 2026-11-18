package org.example.revolverui.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
public class AuthController {

    @GetMapping("/api/auth/me")
    public AuthMeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthMeResponse res = new AuthMeResponse();
        if (auth != null) {
            res.name = auth.getName();
            res.authorities = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .collect(Collectors.toList());
        }
        return res;
    }

    static class AuthMeResponse {
        String name;
        java.util.List<String> authorities;
    }
}