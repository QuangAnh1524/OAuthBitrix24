package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth")
@Slf4j
public class OAuthController {

    private final TokenService tokenService;

    public OAuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/")
    public String handleRoot() {
        return "Server is running";
    }

    @GetMapping("/install")
    public ResponseEntity<String> handleToken() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/test-app")
    public ResponseEntity<String> testApp() {
        log.info("=== Test App Endpoint Called ===");
        return new ResponseEntity<>("Test App Page", HttpStatus.OK);
    }

    @PostMapping("/install")
    public ResponseEntity<String> handleInstall(@RequestParam Map<String, String> params, HttpServletRequest request) {
        log.info("=== INSTALL REQUEST RECEIVED ===");
        log.info("Request URL: {}", request.getRequestURL());
        log.info("Query String: {}", request.getQueryString());
        log.info("Form params: {}", params);

        try {

            // Kiểm tra xem có phải interface mode không
            if (params.containsKey("AUTH_ID")) {
                log.info("Interface mode detected");

                // Luôn lưu/cập nhật token mới
                Map<String, Object> authData = new HashMap<>();
                authData.put("AUTH_ID", params.get("AUTH_ID"));
                authData.put("REFRESH_ID", params.get("REFRESH_ID"));
                authData.put("AUTH_EXPIRES", params.get("AUTH_EXPIRES"));
                authData.put("DOMAIN", params.get("DOMAIN"));
                authData.put("member_id", params.get("member_id"));

                // Luôn save/update token mới
                tokenService.saveOrUpdateTokenFromInstallEvent(authData);
            } else if ("ONAPPINSTALL".equals(params.get("event"))) {
                log.info("Script Only mode detected");

                Map<String, Object> authData = new HashMap<>();
                authData.put("AUTH_ID", params.get("auth[access_token]"));
                authData.put("REFRESH_ID", params.get("auth[refresh_token]"));
                authData.put("member_id", params.get("auth[member_id]"));
                authData.put("DOMAIN", params.get("auth[domain]"));
                authData.put("AUTH_EXPIRES", params.get("auth[expires_in]"));
//            System.out.println(authData);
                tokenService.saveOrUpdateTokenFromInstallEvent(authData);
            } else {
                log.warn("Invalid install request. Params: {}", params);
                return ResponseEntity.badRequest().body("Invalid install request");
            }

            // Redirect sang /app/
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/app/")
                    .body("Redirecting to application");

        } catch (Exception e) {
            log.error("Error handling install: ", e);
            return ResponseEntity.status(500).body("ERROR: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            var token = tokenService.getCurrentToken();
            if (token != null) {
                return ResponseEntity.ok(Map.of(
                        "status", "OK",
                        "member_id", token.getMemberId(),
                        "domain", token.getDomain(),
                        "created_at", token.getCreatedAt()
                ));
            } else {
                return ResponseEntity.ok(Map.of("status", "NO_TOKEN"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

}