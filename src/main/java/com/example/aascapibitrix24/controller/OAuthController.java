package com.example.aascapibitrix24.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/oauth")
@Slf4j
public class OAuthController {

    // Nhận sự kiện Install App
    @PostMapping("/install")
    public ResponseEntity<String> handleInstallEvent(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Nhận được sự kiện install: ", payload);

            String event = (String) payload.get("event");
            if ("ONAPPINSTALL".equals(event)) {
                Map<String, Object> authData = (Map<String, Object>) payload.get("auth");
                if (authData != null) {

                }
            }
        }
    }
}
