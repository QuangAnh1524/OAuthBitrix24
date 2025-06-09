package com.example.aascapibitrix24.service;

import com.example.aascapibitrix24.entity.TokenEntity;
import com.example.aascapibitrix24.repository.TokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class TokenService {
    private final TokenRepository tokenRepository;

    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Value("${bitrix24.client.id:}")
    private String clientId;

    @Value("${bitrix24.client.secret:}")
    private String clientSecret;

    @Value("${bitrix24.oauth.server:https://oauth.bitrix.info}")
    private String oauthServer;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void saveTokenFromInstallEvent(Map<String, Object> authData) {
        try {
            TokenEntity tokenEntity = new TokenEntity();
            tokenEntity.setAccessToken((String) authData.get("access_token"));
            tokenEntity.setExpiresIn((Integer) authData.get("expires_in"));
            tokenEntity.setDomain((String) authData.get("domain"));
            tokenEntity.setMemberId((String) authData.get("member_id"));
            tokenEntity.setRefreshToken((String) authData.get("refresh_token"));
            tokenEntity.setClientEndpoint((String) authData.get("client_endpoint"));
            tokenEntity.setCreatedAt(LocalDateTime.now());
            tokenEntity.setUpdatedAt(LocalDateTime.now());

            // Build client endpoint từ domain
            String domain = (String) authData.get("DOMAIN");
            if (domain != null && !domain.startsWith("http")) {
                tokenEntity.setClientEndpoint("https://" + domain + "/rest/");
            } else if (domain != null) {
                tokenEntity.setClientEndpoint(domain + "/rest/");
            }

            tokenEntity.setCreatedAt(LocalDateTime.now());
            tokenEntity.setUpdatedAt(LocalDateTime.now());

            tokenRepository.save(tokenEntity);
            log.info("Token saved successfully for domain: {} and member: {}",
                    tokenEntity.getDomain(), tokenEntity.getMemberId());
        } catch (Exception e) {
            log.error("Error when saving token: ", e);
            throw new RuntimeException("Failed to save token: " + e.getMessage());
        }
    }

    public TokenEntity getCurrentToken() {
        return tokenRepository.findTopByOrderByCreatedAtDesc();
    }

    public TokenEntity refreshToken() throws Exception {
        TokenEntity token = getCurrentToken();
        if (token == null) {
            log.error("No token found to refresh");
            throw new RuntimeException("No token found to refresh");
        }

        log.info("Attempting to refresh token for domain: {} member: {}", token.getDomain(), token.getMemberId());
        log.info("Current refresh token: {}", token.getRefreshToken());

        String url = oauthServer + "/oauth/token/";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", token.getRefreshToken());

        log.info("Refresh token request URL: {}", url);
        log.info("Client ID: {}", clientId);
        log.info("Client Secret: {}", clientSecret != null ? "***SET***" : "NOT SET");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Refresh token response status: {}", response.getStatusCode());
            log.info("Refresh token response body: {}", response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Refresh token failed with status: {}", response.getStatusCode());
                throw new RuntimeException("Refresh token failed: " + response.getBody());
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            // Kiểm tra response có error không
            if (responseBody.containsKey("error")) {
                String error = (String) responseBody.get("error");
                String errorDescription = (String) responseBody.get("error_description");
                log.error("OAuth error: {} - {}", error, errorDescription);
                throw new RuntimeException("OAuth error: " + error + " - " + errorDescription);
            }

            if (!responseBody.containsKey("access_token") || !responseBody.containsKey("refresh_token")) {
                log.error("Invalid refresh token response: missing required fields");
                throw new RuntimeException("Invalid refresh token response");
            }

            // Cập nhật token
            token.setAccessToken((String) responseBody.get("access_token"));
            token.setRefreshToken((String) responseBody.get("refresh_token"));
            token.setExpiresIn((Integer) responseBody.get("expires_in"));
            token.setUpdatedAt(LocalDateTime.now());

            tokenRepository.save(token);
            log.info("Token refreshed successfully");
            return token;

        } catch (Exception e) {
            log.error("Refresh token failed for domain: {}, member: {}. Error: {}",
                    token.getDomain(), token.getMemberId(), e.getMessage());
            throw e;
        }
    }

    public void saveOrUpdateTokenFromInstallEvent(Map<String, Object> authData) {
        try {
            Object domain = (String) authData.get("DOMAIN");
            String memberId = (String) authData.get("member_id");

            // Tìm token hiện tại
            TokenEntity existingToken = tokenRepository.findTopByOrderByCreatedAtDesc();

            TokenEntity tokenEntity;
            if (existingToken != null && existingToken.getDomain() != null && existingToken.getDomain().equals(domain) && memberId.equals(existingToken.getMemberId())) {

                // Update token hiện tại
                tokenEntity = existingToken;
                log.info("Updating existing token for domain: {} member: {}", domain, memberId);
            } else {
                // Tạo token mới
                tokenEntity = new TokenEntity();
                tokenEntity.setCreatedAt(LocalDateTime.now());
                log.info("Creating new token for domain: {} member: {}", domain, memberId);
            }

            // Cập nhật thông tin token
            tokenEntity.setAccessToken((String) authData.get("AUTH_ID"));
            tokenEntity.setRefreshToken((String) authData.get("REFRESH_ID"));
            tokenEntity.setExpiresIn(Integer.parseInt((String) authData.get("AUTH_EXPIRES")));
            tokenEntity.setDomain((String) domain);
            tokenEntity.setMemberId(memberId);

            // Build client endpoint từ domain
            if (domain != null && !((String) domain).startsWith("http")) {
                tokenEntity.setClientEndpoint("https://" + domain + "/rest/");
            } else if (domain != null) {
                tokenEntity.setClientEndpoint(domain + "/rest/");
            }

            tokenEntity.setUpdatedAt(LocalDateTime.now());

            tokenRepository.save(tokenEntity);
            log.info("Token saved/updated successfully. New access_token: {}", tokenEntity.getAccessToken());

        } catch (Exception e) {
            log.error("Error when saving/updating token: ", e);
            throw new RuntimeException("Failed to save/update token: " + e.getMessage());
        }
    }
}