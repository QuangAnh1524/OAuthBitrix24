package com.example.aascapibitrix24.service;

import com.example.aascapibitrix24.entity.TokenEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
                // Token hết hạn, làm mới token
                log.info("Access token expired, refreshing token...");
                try {
                    tokenEntity = tokenService.refreshToken();
                    log.info("Token refreshed successfully, retrying API call...");
                    return makeApiCall(tokenEntity, method, params);
                } catch (Exception refreshException) {
                    log.error("Failed to refresh token: ", refreshException);
                    throw new RuntimeException("Không thể làm mới token: " + refreshException.getMessage());
                }
            }
            throw new RuntimeException("Lỗi API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error calling API: ", e);
            throw new RuntimeException("Lỗi khi gọi API: " + e.getMessage());
        }
    }

    private JsonNode callBitrixAPI(String method, Map<String, Object> params) throws Exception {
        TokenEntity token = tokenService.getCurrentToken();
        if (token == null) throw new RuntimeException("No valid token found");

        String url = String.format("%s%s?auth=%s", token.getClientEndpoint(), method, token.getAccessToken());
        log.info("Calling Bitrix API: {} with URL: {}", method, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            // Log response để debug
            log.info("Response status: {}, body: {}", response.getStatusCode(), response.getBody());

            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            // Kiểm tra lỗi trong response JSON
            if (jsonResponse.has("error")) {
                String errorCode = jsonResponse.get("error").asText();
                log.warn("Bitrix API returned error: {}", errorCode);

                if ("expired_token".equals(errorCode) || "invalid_token".equals(errorCode)) {
                    log.info("Token expired, trying to refresh...");
                    token = tokenService.refreshToken();
                    url = String.format("%s%s?auth=%s", token.getClientEndpoint(), method, token.getAccessToken());
                    request = new HttpEntity<>(params, headers);
                    response = restTemplate.postForEntity(url, request, String.class);
                    jsonResponse = objectMapper.readTree(response.getBody());

                    // Kiểm tra lỗi sau khi refresh
                    if (jsonResponse.has("error")) {
                        throw new RuntimeException("Bitrix API error after token refresh: " + jsonResponse.get("error").asText());
                    }
                } else {
                    throw new RuntimeException("Bitrix API error: " + errorCode);
                }
            }
            return jsonResponse;

        } catch (HttpClientErrorException e) {
            log.error("HTTP Error: {} - Response body: {}", e.getStatusCode(), e.getResponseBodyAsString());

            // Nếu 401 và chưa thử refresh token
            if (e.getStatusCode().value() == 401) {
                log.info("Got 401, attempting to refresh token...");
                try {
                    token = tokenService.refreshToken();
                    url = String.format("%s%s?auth=%s", token.getClientEndpoint(), method, token.getAccessToken());
                    request = new HttpEntity<>(params, headers);
                    ResponseEntity<String> retryResponse = restTemplate.postForEntity(url, request, String.class);
                    return objectMapper.readTree(retryResponse.getBody());
                } catch (Exception refreshEx) {
                    log.error("Failed to refresh token: ", refreshEx);
                    throw new RuntimeException("Token refresh failed: " + refreshEx.getMessage());
                }
            }
            throw e;
        }
    }

    private JsonNode makeApiCall(TokenEntity token, String method, Map<String, Object> params) {
        try {
            StringBuilder url = new StringBuilder(token.getClientEndpoint() + method + "?auth=" + token.getAccessToken());

            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    url.append("&").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            log.debug("Calling Bitrix24 API: {}", url.toString());
            ResponseEntity<String> response = restTemplate.getForEntity(url.toString(), String.class);

            JsonNode result = objectMapper.readTree(response.getBody());
            log.debug("API Response: {}", result);

            // Kiểm tra lỗi trong response
            if (result.has("error")) {
                String errorCode = result.get("error").asText();
                String errorDescription = result.has("error_description") ?
                        result.get("error_description").asText() : "Unknown error";

                if ("expired_token".equals(errorCode) || "invalid_token".equals(errorCode)) {
                    throw new HttpClientErrorException(org.springframework.http.HttpStatus.UNAUTHORIZED, errorDescription);
                }

                throw new RuntimeException("Bitrix24 API Error: " + errorCode + " - " + errorDescription);
            }

            return result;
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException) {
                throw (HttpClientErrorException) e;
            }
            throw new RuntimeException("Lỗi khi thực hiện call API: " + e.getMessage());
        }
    }
}