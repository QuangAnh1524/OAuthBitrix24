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
    private final BitrixApiService bitrixApiService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ContactService(TokenService tokenService, BitrixApiService bitrixApiService) {
        this.tokenService = tokenService;
        this.bitrixApiService = bitrixApiService;
        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
        this.objectMapper = new ObjectMapper();
    }

    public JsonNode callBitrixAPI(String method, Map<String, Object> params) throws Exception {
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
        return bitrixApiService.callAPI("crm.contact.list", params);
    }

    public JsonNode getContact(String id) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        return bitrixApiService.callAPI("crm.contact.get", params);
    }

    public JsonNode createContact(Map<String, Object> contactData) throws Exception {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> fields = new HashMap<>();
        if (contactData.get("NAME") != null) fields.put("NAME", contactData.get("NAME"));
        if (contactData.get("PHONE") != null) fields.put("PHONE", contactData.get("PHONE"));
        if (contactData.get("EMAIL") != null) fields.put("EMAIL", contactData.get("EMAIL"));
        if (contactData.get("WEB") != null) fields.put("WEB", contactData.get("WEB"));
        if (contactData.get("ADDRESS") != null) fields.put("ADDRESS", contactData.get("ADDRESS"));
        params.put("fields", fields);

        JsonNode result = bitrixApiService.callAPI("crm.contact.add", params);

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

    public JsonNode updateContact(String id, Map<String, Object> contactData) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        Map<String, Object> fields = new HashMap<>();
        if (contactData.get("NAME") != null) fields.put("NAME", contactData.get("NAME"));
        if (contactData.get("PHONE") != null) fields.put("PHONE", contactData.get("PHONE"));
        if (contactData.get("EMAIL") != null) fields.put("EMAIL", contactData.get("EMAIL"));
        if (contactData.get("WEB") != null) fields.put("WEB", contactData.get("WEB"));
        if (contactData.get("ADDRESS") != null) fields.put("ADDRESS", contactData.get("ADDRESS"));
        params.put("fields", fields);

        JsonNode result = bitrixApiService.callAPI("crm.contact.update", params);

        if (contactData.containsKey("BANK_NAME") || contactData.containsKey("BANK_ACCOUNT")) {
            try {
                updateBankRequisites(id, contactData);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                    log.warn("Access denied for updating bank requisites: {}", e.getResponseBodyAsString());
                    throw new RuntimeException("Access denied for updating bank details.");
                }
                throw e;
            }
        }

        return result;
    }

    public JsonNode deleteContact(String id) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filter", Map.of("ENTITY_TYPE_ID", 3, "ENTITY_ID", id));
        JsonNode requisites = bitrixApiService.callAPI("crm.requisite.list", params);

        if (requisites.has("result") && requisites.get("result").isArray()) {
            for (JsonNode requisite : requisites.get("result")) {
                String requisiteId = requisite.get("ID").asText();
                log.info("Deleting requisite ID: {}", requisiteId);
                Map<String, Object> deleteParams = new HashMap<>();
                deleteParams.put("id", requisiteId);
                bitrixApiService.callAPI("crm.requisite.delete", deleteParams);
            }
        }

        params = new HashMap<>();
        params.put("id", id);
        return bitrixApiService.callAPI("crm.contact.delete", params);
    }

    private void createBankRequisites(String contactId, Map<String, Object> contactData) throws Exception {
        JsonNode presets = bitrixApiService.callAPI("crm.requisite.preset.list", new HashMap<>());
        String presetId = null;

        if (presets.has("result") && presets.get("result").isArray()) {
            for (JsonNode preset : presets.get("result")) {
                if ("Person".equals(preset.get("NAME").asText())) {
                    presetId = preset.get("ID").asText();
                    log.info("Found Person Preset with ID: {}", presetId);
                    break;
                }
            }
        }

        if (presetId == null && presets.get("result").size() > 0) {
            presetId = presets.get("result").get(0).get("ID").asText();
            log.warn("No Person Preset found, using default Preset ID: {}", presetId);
        }

        if (presetId == null) {
            log.error("No requisite preset found");
            throw new RuntimeException("No requisite preset available");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("fields", Map.of(
                "ENTITY_TYPE_ID", 3,
                "ENTITY_ID", contactId,
                "PRESET_ID", presetId,
                "NAME", String.valueOf(contactData.getOrDefault("BANK_NAME", "Default")),
                "ACTIVE", "Y"
        ));

        JsonNode requisiteResult = bitrixApiService.callAPI("crm.requisite.add", params);

        if (requisiteResult.has("result")) {
            String requisiteId = requisiteResult.get("result").asText();
            Map<String, Object> bankParams = new HashMap<>();
            bankParams.put("fields", Map.of(
                    "ENTITY_ID", requisiteId,
                    "COUNTRY_ID", 122,
                    "NAME", String.valueOf(contactData.getOrDefault("BANK_NAME", "Default Bank")),
                    "RQ_BANK_NAME", String.valueOf(contactData.getOrDefault("BANK_NAME", "")),
                    "RQ_ACC_NUM", String.valueOf(contactData.getOrDefault("BANK_ACCOUNT", "")),
                    "ACTIVE", "Y",
                    "SORT", 500
            ));

            log.info("Bank detail params: {}", bankParams);
            bitrixApiService.callAPI("crm.requisite.bankdetail.add", bankParams);
        }
    }

    private void updateBankRequisites(String contactId, Map<String, Object> contactData) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filter", Map.of("ENTITY_TYPE_ID", 3, "ENTITY_ID", contactId));
        JsonNode requisites = bitrixApiService.callAPI("crm.requisite.list", params);

        if (requisites.has("result") && requisites.get("result").isArray() && requisites.get("result").size() > 0) {
            String requisiteId = requisites.get("result").get(0).get("ID").asText();
            log.info("Found requisiteId: {}", requisiteId);

            Map<String, Object> updateParams = new HashMap<>();
            updateParams.put("id", requisiteId);
            String bankName = contactData.get("BANK_NAME") != null ? String.valueOf(contactData.get("BANK_NAME")) : "";
            updateParams.put("fields", Map.of("NAME", bankName));
            bitrixApiService.callAPI("crm.requisite.update", updateParams);

            Map<String, Object> bankParams = new HashMap<>();
            bankParams.put("filter", Map.of("ENTITY_ID", requisiteId));
            bankParams.put("select", new String[]{"ID", "NAME", "RQ_BANK_NAME", "RQ_ACC_NUM"});
            JsonNode bankDetails = bitrixApiService.callAPI("crm.requisite.bankdetail.list", bankParams);

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

                bitrixApiService.callAPI("crm.requisite.bankdetail.update", updateBankParams);
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