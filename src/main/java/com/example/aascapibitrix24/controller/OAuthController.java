package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/oauth")
@Slf4j
public class OAuthController {

    private final TokenService tokenService;

    public OAuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    // Nhận sự kiện Install App
    @PostMapping("/install")
    public ResponseEntity<String> handleInstallEvent(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Nhận được sự kiện install: {}", payload);

            String event = (String) payload.get("event");
            if ("ONAPPINSTALL".equals(event)) {
                Map<String, Object> authData = (Map<String, Object>) payload.get("auth");
                if (authData != null) {
                    tokenService.saveTokenFromInstallEvent(authData);
                    return ResponseEntity.ok("Cài đặt thành công và lưu thành công token!");
                }
            }
            return ResponseEntity.ok("Đã nhận sự kiện: " + event);
        } catch (Exception e) {
            log.error("Lỗi xử lý sự kiện install", e.getMessage());
            return ResponseEntity.status(500).body("Lỗi:" + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            var token = tokenService.getCurrentToken();
            if (token != null) {
                return ResponseEntity.ok(Map.of("status", "OK",
                        "member_id", token.getMemberId(),
                        "domain", token.getDomain(),
                        "created_at", token.getCreatedAt()));
            } else {
                return ResponseEntity.ok(Map.of("status", "NO_TOKEN"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
