package com.example.aascapibitrix24.service;

import com.example.aascapibitrix24.entity.TokenEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ContactService {

    private final TokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ContactService(TokenService tokenService) {
        this.tokenService = tokenService;
        // Cấu hình RestTemplate để giữ body lỗi
        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
        this.objectMapper = new ObjectMapper();
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
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            if (jsonResponse.has("error")) {
                String errorCode = jsonResponse.get("error").asText();
                if ("expired_token".equals(errorCode) || "invalid_token".equals(errorCode)) {
                    log.info("Token expired, trying to refresh...");
                    token = tokenService.refreshToken();
                    url = String.format("%s%s?auth=%s", token.getClientEndpoint(), method, token.getAccessToken());
                    response = restTemplate.postForEntity(url, request, String.class);
                    jsonResponse = objectMapper.readTree(response.getBody());
                } else {
                    throw new RuntimeException("Bitrix API error: " + errorCode);
                }
            }
            return jsonResponse;
        } catch (HttpClientErrorException e) {
            log.error("HTTP Error: {} - Response body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    public JsonNode getContacts() throws Exception {
        log.info("Fetching contacts from Bitrix24");
        Map<String, Object> params = new HashMap<>();
        params.put("select", new String[]{"ID", "NAME", "LAST_NAME", "PHONE", "EMAIL", "WEB", "ADDRESS", "COMMENTS"});
        params.put("start", 0);
        JsonNode result = callBitrixAPI("crm.contact.list", params);
        if (result == null) {
            log.error("Null response from Bitrix API");
            throw new RuntimeException("Invalid response from Bitrix API");
        }
        log.info("Bitrix API response: {}", result);
        return result;
    }

    // ... (các phương thức khác như getContact, createContact, v.v. giữ nguyên)

    // ... (các phương thức khác như getContact, createContact, v.v. giữ nguyên)


    // Lấy contact theo ID
    public JsonNode getContact(String id) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        return callBitrixAPI("crm.contact.get", params);
    }

    // Tạo contact mới
    public JsonNode createContact(Map<String, Object> contactData) throws Exception {
        Map<String, Object> params = new HashMap<>();

        // Chuẩn bị fields cho contact
        Map<String, Object> fields = new HashMap<>();

        if (contactData.get("NAME") != null) {
            fields.put("NAME", contactData.get("NAME"));
        }
        if (contactData.get("LAST_NAME") != null) {
            fields.put("LAST_NAME", contactData.get("LAST_NAME"));
        }
        if (contactData.get("PHONE") != null) {
            fields.put("PHONE", contactData.get("PHONE"));
        }
        if (contactData.get("EMAIL") != null) {
            fields.put("EMAIL", contactData.get("EMAIL"));
        }
        if (contactData.get("WEB") != null) {
            fields.put("WEB", contactData.get("WEB"));
        }
        if (contactData.get("ADDRESS") != null) {
            fields.put("ADDRESS", contactData.get("ADDRESS"));
        }
        if (contactData.get("COMMENTS") != null) {
            fields.put("COMMENTS", contactData.get("COMMENTS"));
        }

        params.put("fields", fields);

        JsonNode result = callBitrixAPI("crm.contact.add", params);

        // Nếu có thông tin ngân hàng, tạo requisites
        if (contactData.containsKey("BANK_NAME") || contactData.containsKey("BANK_ACCOUNT")) {
            try {
                String contactId = result.get("result").asText();
                createBankRequisites(contactId, contactData);
            } catch (Exception e) {
                log.warn("Could not create bank requisites: ", e);
            }
        }

        return result;
    }

    // Cập nhật contact
    public JsonNode updateContact(String id, Map<String, Object> contactData) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);

        // Chuẩn bị fields cho contact
        Map<String, Object> fields = new HashMap<>();

        if (contactData.get("NAME") != null) {
            fields.put("NAME", contactData.get("NAME"));
        }
        if (contactData.get("LAST_NAME") != null) {
            fields.put("LAST_NAME", contactData.get("LAST_NAME"));
        }
        if (contactData.get("PHONE") != null) {
            fields.put("PHONE", contactData.get("PHONE"));
        }
        if (contactData.get("EMAIL") != null) {
            fields.put("EMAIL", contactData.get("EMAIL"));
        }
        if (contactData.get("WEB") != null) {
            fields.put("WEB", contactData.get("WEB"));
        }
        if (contactData.get("ADDRESS") != null) {
            fields.put("ADDRESS", contactData.get("ADDRESS"));
        }
        if (contactData.get("COMMENTS") != null) {
            fields.put("COMMENTS", contactData.get("COMMENTS"));
        }

        params.put("fields", fields);

        JsonNode result = callBitrixAPI("crm.contact.update", params);

        // Cập nhật thông tin ngân hàng nếu có
        if (contactData.containsKey("BANK_NAME") || contactData.containsKey("BANK_ACCOUNT")) {
            try {
                updateBankRequisites(id, contactData);
            } catch (Exception e) {
                log.warn("Could not update bank requisites: ", e);
            }
        }

        return result;
    }

    // Xóa contact
    public JsonNode deleteContact(String id) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        return callBitrixAPI("crm.contact.delete", params);
    }

    // Tạo requisites ngân hàng
    private void createBankRequisites(String contactId, Map<String, Object> contactData) throws Exception {
        // Lấy danh sách preset requisites
        JsonNode presets = callBitrixAPI("crm.requisite.preset.list", new HashMap<>());

        String presetId = null;
        if (presets.has("result") && presets.get("result").isArray() && presets.get("result").size() > 0) {
            presetId = presets.get("result").get(0).get("ID").asText();
        }

        if (presetId == null) {
            log.warn("No requisite preset found");
            return;
        }

        // Tạo requisite
        Map<String, Object> params = new HashMap<>();
        params.put("fields", Map.of(
                "ENTITY_TYPE_ID", 3,
                "ENTITY_ID", contactId,
                "PRESET_ID", presetId,
                "NAME", String.valueOf(contactData.getOrDefault("BANK_NAME", "")),
                "ACTIVE", "Y"
        ));

        JsonNode requisiteResult = callBitrixAPI("crm.requisite.add", params);

        if (requisiteResult.has("result")) {
            String requisiteId = requisiteResult.get("result").asText();

            // Thêm bank detail
            Map<String, Object> bankParams = new HashMap<>();
            bankParams.put("fields", Map.of(
                    "ENTITY_ID", requisiteId, // Sử dụng requisiteId thay vì contactId
                    "COUNTRY_ID", 1,
                    "NAME", String.valueOf(contactData.getOrDefault("BANK_NAME", "Default Bank")),
                    "RQ_BANK_NAME", String.valueOf(contactData.getOrDefault("BANK_NAME", "")),
                    "RQ_ACC_NUM", String.valueOf(contactData.getOrDefault("BANK_ACCOUNT", "")),
                    "ACTIVE", "Y",
                    "SORT", 500
            ));

            // Log để kiểm tra payload
            log.info("Bank detail params: {}", bankParams);

            callBitrixAPI("crm.requisite.bankdetail.add", bankParams);
        }
    }

    // Cập nhật requisites ngân hàng
    private void updateBankRequisites(String contactId, Map<String, Object> contactData) throws Exception {
        log.info("Updating bank requisites for contactId: {}, contactData: {}", contactId, contactData);

        Map<String, Object> params = new HashMap<>();
        params.put("filter", Map.of(
                "ENTITY_TYPE_ID", 3,
                "ENTITY_ID", contactId
        ));

        log.info("Fetching requisites with params: {}", params);
        JsonNode requisites = callBitrixAPI("crm.requisite.list", params);

        if (requisites.has("result") && requisites.get("result").isArray() && requisites.get("result").size() > 0) {
            String requisiteId = requisites.get("result").get(0).get("ID").asText();
            log.info("Found requisiteId: {}", requisiteId);

            Map<String, Object> updateParams = new HashMap<>();
            updateParams.put("id", requisiteId);
            String bankName = contactData.get("BANK_NAME") != null ? String.valueOf(contactData.get("BANK_NAME")) : "";
            updateParams.put("fields", Map.of(
                    "NAME", bankName
            ));

            log.info("Updating requisite with params: {}", updateParams);
            callBitrixAPI("crm.requisite.update", updateParams);

            Map<String, Object> bankParams = new HashMap<>();
            bankParams.put("filter", Map.of("REQUISITE_ID", requisiteId));

            log.info("Fetching bank details with params: {}", bankParams);
            JsonNode bankDetails = callBitrixAPI("crm.requisite.bankdetail.list", bankParams);

            if (bankDetails.has("result") && bankDetails.get("result").isArray() && bankDetails.get("result").size() > 0) {
                String bankDetailId = bankDetails.get("result").get(0).get("ID").asText();
                log.info("Found bankDetailId: {}", bankDetailId);

                Map<String, Object> updateBankParams = new HashMap<>();
                updateBankParams.put("id", bankDetailId);
                String bankAccount = contactData.get("BANK_ACCOUNT") != null ? String.valueOf(contactData.get("BANK_ACCOUNT")) : "";
                updateBankParams.put("fields", Map.of(
                        "RQ_BANK_NAME", bankName,
                        "RQ_ACC_NUM", bankAccount,
                        "NAME", bankName,
                        "ACTIVE", "Y",
                        "SORT", 500
                ));

                log.info("Updating bank detail with params: {}", updateBankParams);
                try {
                    JsonNode updateResult = callBitrixAPI("crm.requisite.bankdetail.update", updateBankParams);
                    log.info("Bank detail update result: {}", updateResult);
                } catch (HttpClientErrorException e) {
                    log.error("Failed to update bank detail: HTTP {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
                    throw new RuntimeException("Failed to update bank detail: " + e.getResponseBodyAsString(), e);
                }
            } else {
                log.info("No bank details found, creating new bank requisites");
                createBankRequisites(contactId, contactData);
            }
        } else {
            log.info("No requisites found, creating new bank requisites");
            createBankRequisites(contactId, contactData);
        }
    }
}

