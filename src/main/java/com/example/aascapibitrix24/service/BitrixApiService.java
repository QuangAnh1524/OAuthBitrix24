package com.example.aascapibitrix24.service;

import com.example.aascapibitrix24.entity.TokenEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class BitrixApiService {
    private final TokenService tokenService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BitrixApiService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public JsonNode callAPI(String method, Map<String, Object> params) {
        TokenEntity tokenEntity = tokenService.getCurrentToken();
        if (tokenEntity == null) {
            throw new RuntimeException("Không tìm thấy access token");
        }

        try {
            return makeApiCall(tokenEntity, method, params);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                //het hạn token, làm mới
                log.info("Access token expired, refresh token");
                tokenEntity = tokenService.refreshToken();
                return null;
            }
            throw new RuntimeException("Lỗi API: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi gọi API: " + e.getMessage());
        }
    }

    public JsonNode makeApiCall(TokenEntity token, String method, Map<String, Object> params) {
        try {
            StringBuilder url = new StringBuilder(token.getClientEndpoint() + method + "?auth=" + token.getAccessToken());
            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    url.append("&").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            ResponseEntity<String> response = restTemplate.getForEntity(url.toString(), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw  new RuntimeException("Lỗi khi thực hiện call API: " + e.getMessage());
        }
    }

}
