package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/app")
@Slf4j
public class AppController {

    private final TokenService tokenService;

    public AppController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Endpoint chính của app - đây là URL mà Bitrix24 sẽ gọi khi user click "Open Application"
     */
    @GetMapping("/")
    public ResponseEntity showApp(@RequestParam Map params, HttpServletRequest request) {
        log.info("=== APP MAIN PAGE REQUESTED ===");
        try {
            var token = tokenService.getCurrentToken();
            if (token == null) {
                log.warn("No token found, redirecting to install");
                return ResponseEntity.ok("No token found");
            }
            ClassPathResource resource = new ClassPathResource("static/app.html");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            content = content.replace("{{DOMAIN}}", token.getDomain())
                    .replace("{{MEMBER_ID}}", token.getMemberId())
                    .replace("{{API_BASE_URL}}", "https://" + request.getHeader("Host"));
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(content);
        } catch (Exception e) {
            log.error("Error loading app page: ", e);
            return ResponseEntity.status(500).body("Error loading app: " + e.getMessage());
        }
    }

    /**
     * Endpoint cho trường hợp chưa install app
     */
    private String createInstallRequiredPage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>App Not Installed</title>
                    <meta charset="UTF-8">
                </head>
                <body>
                    <div style="text-align: center; margin-top: 50px;">
                        <h1>App chưa được cài đặt</h1>
                        <p>Vui lòng cài đặt app trước khi sử dụng.</p>
                        <button onclick="window.location.reload()">Thử lại</button>
                    </div>
                </body>
                </html>
                """;
    }

    /**
     * Endpoint để serve static files nếu cần
     */
    @GetMapping("/assets/**")
    public ResponseEntity<String> getAssets(HttpServletRequest request) {
        // Serve CSS, JS files nếu cần
        return ResponseEntity.notFound().build();
    }
}