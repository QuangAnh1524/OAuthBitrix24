package com.example.aascapibitrix24.service;

import com.example.aascapibitrix24.dto.TokenResponse;
import com.example.aascapibitrix24.entity.TokenEntity;
import com.example.aascapibitrix24.repository.TokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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

    @Value("${bitrix24.oauth.server:}")
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
            tokenEntity.setClientEndpoint("client_endpoint");
            tokenEntity.setCreatedAt(LocalDateTime.now());
            tokenEntity.setUpdatedAt(LocalDateTime.now());

            tokenRepository.save(tokenEntity);
            log.info("Token saved: {}", tokenEntity);
        } catch (Exception e) {
            log.error("Error when save token", e);
            e.printStackTrace();
        }
    }

    public TokenEntity getCurrentToken() {
        return tokenRepository.findTopByOrderByCreatedAtDesc();
    }

    public TokenEntity refreshToken() {
        TokenEntity currentToken = getCurrentToken();
        if (currentToken == null || currentToken.getRefreshToken() == null) {
            throw new RuntimeException("Token not found");
        }

        try {
            String url = String.format("%s/oauth/token/?grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                    oauthServer, clientId, clientSecret, currentToken.getRefreshToken());
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            TokenResponse tokenResponse = objectMapper.readValue(response.getBody(), TokenResponse.class);

            if (tokenResponse.getError() != null) {
                throw new RuntimeException("Lỗi khi refresh token: " + tokenResponse.getErrorDescription());
            }

            //save token mới
            TokenEntity newToken = new TokenEntity(
                    tokenResponse.getAccessToken(),
                    tokenResponse.getExpiresIn(),
                    tokenResponse.getDomain(),
                    tokenResponse.getClientEndpoint(),
                    tokenResponse.getMemberId(),
                    tokenResponse.getRefreshToken()
            );
            return tokenRepository.save(newToken);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi refresh token: " + e.getMessage());

        }
    }
}
