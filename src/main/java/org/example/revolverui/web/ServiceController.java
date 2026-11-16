package org.example.revolverui.web;

import org.example.revolverui.config.AppConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class ServiceController {

    private final AppConfigService config;

    public ServiceController(AppConfigService config) {
        this.config = config;
    }

    @GetMapping("/services")
    public String page(Model model) {
        model.addAttribute("services", config.getServiceMap());
        return "services";
    }
}