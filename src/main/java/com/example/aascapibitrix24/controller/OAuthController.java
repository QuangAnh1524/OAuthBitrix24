package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
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

    @GetMapping("/install")
    public ResponseEntity<String> handleToken() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping(value = "/install", consumes = {"application/x-www-form-urlencoded", "application/json"})
    public ResponseEntity<String> handleInstall(@RequestParam Map<String, String> formParams, HttpServletRequest request) {
        try {
            log.info("Content-Type: {}", request.getContentType());
            log.info("Form params: {}", formParams);

            String authToken = formParams.get("AUTH_ID");
            String refreshToken = formParams.get("REFRESH_ID");
            String memberId = formParams.get("member_id");
            String domain = formParams.get("DOMAIN");
            String expiresIn = formParams.get("AUTH_EXPIRES");

            if (authToken != null && refreshToken != null && memberId != null && domain != null) {
                String clientEndpoint = "https://" + domain + "/rest/";
                Map<String, Object> authData = Map.of(
                        "access_token", authToken,
                        "refresh_token", refreshToken,
                        "member_id", memberId,
                        "domain", domain,
                        "client_endpoint", clientEndpoint,
                        "expires_in", Integer.parseInt(expiresIn)
                );

                tokenService.saveTokenFromInstallEvent(authData);
                return ResponseEntity.ok("INSTALL_FINISH");
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Lỗi xử lý install: ", e);
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

    @GetMapping("/")
    public ResponseEntity<String> handleRoot() {
        return ResponseEntity.ok("Server is running");
    }
}