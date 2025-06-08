package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.entity.TokenEntity;
import com.example.aascapibitrix24.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
            try {
                // Kiểm tra xem có phải interface mode không
                if (params.containsKey("AUTH_ID")) {
                    log.info("Interface mode detected");

                    // Luôn lưu/cập nhật token mới, không skip
                    Map<String, Object> authData = new HashMap<>();
                    authData.put("AUTH_ID", params.get("AUTH_ID"));
                    authData.put("REFRESH_ID", params.get("REFRESH_ID"));
                    authData.put("AUTH_EXPIRES", params.get("AUTH_EXPIRES"));
                    authData.put("DOMAIN", params.get("DOMAIN"));
                    authData.put("member_id", params.get("member_id"));

                    // Luôn save/update token mới
                    tokenService.saveOrUpdateTokenFromInstallEvent(authData);
//            } else if ("ONAPPINSTALL".equals(params.get("event"))) {
//                log.info("Script Only mode detected");
//                authToken = params.get("auth[access_token]");
//                refreshToken = formParams.get("auth[refresh_token]");
//                memberId = formParams.get("auth[member_id]");
//                domain = formParams.get("auth[domain]");
//                expiresIn = formParams.get("auth[expires_in]");
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
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback() {
        log.info("=== CALLBACK ===");
        return ResponseEntity.ok("OK Script Only");
                                                 }

    @PostMapping("/callback")
    public ResponseEntity<String> handleScriptOnlyCallback(@RequestParam Map<String, String> formParams,
                                                           HttpServletRequest request) {
        log.info("=== CALLBACK SCRIPT ONLY MODE ===");
        log.info("Request received at: {}", LocalDateTime.now());
        log.info("Full request headers: {}", request.getHeaderNames());
        log.info("Raw query string: {}", request.getQueryString());
        log.info("Form params: {}", formParams);
        try {
            log.info("=== CALLBACK SCRIPT ONLY MODE ===");
            log.info("URL: {}", request.getRequestURL());
            log.info("Method: {}", request.getMethod());
            log.info("Content-Type: {}", request.getContentType());
            log.info("All form params:");
            formParams.forEach((key, value) -> log.info("  {} = {}", key, value));

            // Kiểm tra event ONAPPINSTALL
            String event = formParams.get("event");
            if (!"ONAPPINSTALL".equals(event)) {
                log.info("Event không phải ONAPPINSTALL: {}", event);
                return ResponseEntity.ok("OK");
            }

            log.info("Xử lý ONAPPINSTALL event");

            // Lấy thông tin auth từ Script Only format
            String authToken = formParams.get("auth[access_token]");
            String refreshToken = formParams.get("auth[refresh_token]");
            String memberId = formParams.get("auth[member_id]");
            String domain = formParams.get("auth[domain]");
            String expiresIn = formParams.get("auth[expires_in]");
            String clientEndpoint = formParams.get("auth[client_endpoint]");
            String status = formParams.get("auth[status]");

            log.info("Auth data extracted:");
            log.info("  access_token: {}", authToken != null ? authToken.substring(0, Math.min(10, authToken.length())) + "..." : "null");
            log.info("  refresh_token: {}", refreshToken != null ? refreshToken.substring(0, Math.min(10, refreshToken.length())) + "..." : "null");
            log.info("  member_id: {}", memberId);
            log.info("  domain: {}", domain);
            log.info("  expires_in: {}", expiresIn);
            log.info("  client_endpoint: {}", clientEndpoint);
            log.info("  status: {}", status);

            // Kiểm tra đủ thông tin không
            if (authToken == null || refreshToken == null || memberId == null || domain == null) {
                log.error("Thiếu thông tin auth cần thiết!");
                return ResponseEntity.ok("OK cho script only");
            }

            // Parse expires_in
            int expiresInSeconds = 3600; // mặc định 1 giờ
            if (expiresIn != null && !expiresIn.trim().isEmpty()) {
                try {
                    expiresInSeconds = Integer.parseInt(expiresIn);
                } catch (NumberFormatException e) {
                    log.warn("Không parse được expires_in: {}, dùng mặc định 3600s", expiresIn);
                }
            }

            // Tạo client_endpoint nếu chưa có
            if (clientEndpoint == null || clientEndpoint.trim().isEmpty()) {
                clientEndpoint = "https://" + domain + "/rest/";
            }

            // Tạo auth data để lưu
            Map<String, Object> authData = new HashMap<>();
            authData.put("access_token", authToken);
            authData.put("refresh_token", refreshToken);
            authData.put("member_id", memberId);
            authData.put("domain", domain);
            authData.put("client_endpoint", clientEndpoint);
            authData.put("expires_in", expiresInSeconds);

            // Lưu token
            log.info("Đang lưu Script Only token...");
            tokenService.saveTokenFromInstallEvent(authData);
            log.info("Lưu Script Only token thành công!");

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Lỗi khi xử lý Script Only callback: ", e);
            return ResponseEntity.status(500).body("ERROR: " + e.getMessage());
        }
    }
}