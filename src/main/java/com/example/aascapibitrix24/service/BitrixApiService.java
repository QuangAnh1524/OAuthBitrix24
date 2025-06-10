package com.example.aascapibitrix24.service;

import com.example.aascapibitrix24.entity.TokenEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        try {
            // Kiểm tra và lấy token hợp lệ
            TokenEntity token = tokenService.getCurrentToken(); // Tự động refresh nếu cần
            return makeApiCall(token, method, params);
        } catch (HttpClientErrorException e) {
            log.error("HTTP Error: {} - Response body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("API call failed: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Network error or timeout: ", e);
            throw new RuntimeException("Network error or timeout: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling API: ", e);
            throw new RuntimeException("Unexpected error: " + e.getMessage());
        }
    }

    private JsonNode makeApiCall(TokenEntity token, String method, Map<String, Object> params) throws Exception {
        String url = String.format("%s%s?auth=%s", token.getClientEndpoint(), method, token.getAccessToken());
        log.info("Calling Bitrix API: {} with URL: {}", method, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            if (jsonResponse.has("error")) {
                String errorCode = jsonResponse.get("error").asText();
                throw new RuntimeException("Bitrix API error: " + errorCode);
            }

            return jsonResponse;
        } catch (HttpClientErrorException e) {
            log.error("HTTP Error: {} - Response body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}