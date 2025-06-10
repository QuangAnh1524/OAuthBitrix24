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
            tokenEntity.setRefreshToken((String) authData.get("refresh_token"));
            tokenEntity.setExpiresIn((Integer) authData.get("expires_in"));
            tokenEntity.setExpiresAt(LocalDateTime.now().plusSeconds((Integer) authData.get("expires_in"))); // Lưu thời điểm hết hạn
            tokenEntity.setDomain((String) authData.get("domain"));
            tokenEntity.setMemberId((String) authData.get("member_id"));
            tokenEntity.setClientEndpoint((String) authData.get("client_endpoint"));
            tokenEntity.setCreatedAt(LocalDateTime.now());
            tokenEntity.setUpdatedAt(LocalDateTime.now());

            String domain = (String) authData.get("DOMAIN");
            if (domain != null && !domain.startsWith("http")) {
                tokenEntity.setClientEndpoint("https://" + domain + "/rest/");
            } else if (domain != null) {
                tokenEntity.setClientEndpoint(domain + "/rest/");
            }

            tokenRepository.save(tokenEntity);
            log.info("Token saved successfully for domain: {} and member: {}", tokenEntity.getDomain(), tokenEntity.getMemberId());
        } catch (Exception e) {
            log.error("Error when saving token: ", e);
            throw new RuntimeException("Failed to save token: " + e.getMessage());
        }
    }

    public TokenEntity getCurrentToken() throws Exception {
        TokenEntity token = tokenRepository.findTopByOrderByCreatedAtDesc();
        if (token == null) {
        log.error("No token found");
        throw new RuntimeException("No valid token found");
    }

    // Kiểm tra token có hết hạn không
        if (isTokenExpired(token)) {
        log.info("Token expired for domain: {}. Refreshing token...", token.getDomain());
        return refreshToken();
    }

        return token;
}

    private boolean isTokenExpired(TokenEntity token) {
        if (token.getExpiresAt() == null) {
            return true;
        }
        // Kiểm tra trước 5 phút để tránh rủi ro
        return LocalDateTime.now().isAfter(token.getExpiresAt().minusMinutes(5));
    }

    public TokenEntity refreshToken() throws Exception {
        TokenEntity token = tokenRepository.findTopByOrderByCreatedAtDesc();
        if (token == null) {
            log.error("No token found to refresh");
            throw new RuntimeException("No token found to refresh");
        }

        log.info("Attempting to refresh token for domain: {} member: {}", token.getDomain(), token.getMemberId());

        String url = oauthServer + "/oauth/token/";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", token.getRefreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            if (responseBody.containsKey("error")) {
                log.error("OAuth error: {} - {}", responseBody.get("error"), responseBody.get("error_description"));
                throw new RuntimeException("OAuth error: " + responseBody.get("error"));
            }

            token.setAccessToken((String) responseBody.get("access_token"));
            token.setRefreshToken((String) responseBody.get("refresh_token"));
            token.setExpiresIn((Integer) responseBody.get("expires_in"));
            token.setExpiresAt(LocalDateTime.now().plusSeconds((Integer) responseBody.get("expires_in")));
            token.setUpdatedAt(LocalDateTime.now());

            tokenRepository.save(token);
            log.info("Token refreshed successfully for domain: {}", token.getDomain());
            return token;
        } catch (Exception e) {
            log.error("Refresh token failed: ", e);
            throw new RuntimeException("Failed to refresh token: " + e.getMessage());
        }
    }

    public void saveOrUpdateTokenFromInstallEvent(Map<String, Object> authData) {
        try {
            String domain = (String) authData.get("DOMAIN");
            String memberId = (String) authData.get("member_id");

            TokenEntity existingToken = tokenRepository.findTopByOrderByCreatedAtDesc();
            TokenEntity tokenEntity;

            if (existingToken != null && existingToken.getDomain().equals(domain) && existingToken.getMemberId().equals(memberId)) {
                tokenEntity = existingToken;
                log.info("Updating existing token for domain: {} member: {}", domain, memberId);
            } else {
                tokenEntity = new TokenEntity();
                tokenEntity.setCreatedAt(LocalDateTime.now());
                log.info("Creating new token for domain: {} member: {}", domain, memberId);
            }

            tokenEntity.setAccessToken((String) authData.get("AUTH_ID"));
            tokenEntity.setRefreshToken((String) authData.get("REFRESH_ID"));
            tokenEntity.setExpiresIn(Integer.parseInt((String) authData.get("AUTH_EXPIRES")));
            tokenEntity.setExpiresAt(LocalDateTime.now().plusSeconds(Integer.parseInt((String) authData.get("AUTH_EXPIRES"))));
            tokenEntity.setDomain(domain);
            tokenEntity.setMemberId(memberId);

            if (domain != null && !domain.startsWith("http")) {
                tokenEntity.setClientEndpoint("https://" + domain + "/rest/");
            } else if (domain != null) {
                tokenEntity.setClientEndpoint(domain + "/rest/");
            }

            tokenEntity.setUpdatedAt(LocalDateTime.now());
            tokenRepository.save(tokenEntity);
            log.info("Token saved/updated successfully for domain: {}", domain);
        } catch (Exception e) {
            log.error("Error when saving/updating token: ", e);
            throw new RuntimeException("Failed to save/update token: " + e.getMessage());
        }
    }
}