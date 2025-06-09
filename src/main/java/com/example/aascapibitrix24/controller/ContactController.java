package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.service.ContactService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ContactController {

    private final ContactService contactService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    // Lấy danh sách contact
    @GetMapping("/contacts")
    public ResponseEntity<JsonNode> getContacts() {
        try {
            JsonNode contacts = contactService.getContacts();
            log.info("Contacts response: {}", contacts);
            return ResponseEntity.ok(contacts);
        } catch (Exception e) {
            log.error("Error fetching contacts: ", e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to fetch contacts: " + e.getMessage()));
        }
    }

    // Lấy contact theo ID
    @GetMapping("/contacts/{id}")
    public ResponseEntity<JsonNode> getContact(@PathVariable String id) {
        try {
            JsonNode result = contactService.getContact(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting contact {}: ", id, e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to get contact: " + e.getMessage()));
        }
    }

    // Tạo contact mới
    @PostMapping("/contacts")
    public ResponseEntity<JsonNode> createContact(@RequestBody Map<String, Object> contactData) {
        try {
            JsonNode result = contactService.createContact(contactData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating contact: ", e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to create contact: " + e.getMessage()));
        }
    }

    // Cập nhật contact
    @PutMapping("/contacts/{id}")
    public ResponseEntity<JsonNode> updateContact(@PathVariable String id,
                                                  @RequestBody Map<String, Object> contactData) {
        try {
            JsonNode result = contactService.updateContact(id, contactData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating contact {}: ", id, e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to update contact: " + e.getMessage()));
        }
    }

    // Xóa contact
    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<JsonNode> deleteContact(@PathVariable String id) {
        try {
            JsonNode result = contactService.deleteContact(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error deleting contact {}: ", id, e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to delete contact: " + e.getMessage()));
        }
    }

    // Lấy danh sách requisites
    @GetMapping("/requisites")
    public ResponseEntity<JsonNode> getRequisites() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("filter", Map.of("ENTITY_TYPE_ID", 3)); // Lọc cho contact
            JsonNode requisites = contactService.callBitrixAPI("crm.requisite.list", params);
            log.info("Requisites response: {}", requisites);
            return ResponseEntity.ok(requisites);
        } catch (Exception e) {
            log.error("Error fetching requisites: ", e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to fetch requisites: " + e.getMessage()));
        }
    }

    // Lấy danh sách bank details
    @GetMapping("/requisites/bankdetails")
    public ResponseEntity<JsonNode> getBankDetails() {
        try {
            Map<String, Object> params = new HashMap<>();
            JsonNode bankDetails = contactService.callBitrixAPI("crm.requisite.bankdetail.list", params);
            log.info("Bank details response: {}", bankDetails);
            return ResponseEntity.ok(bankDetails);
        } catch (Exception e) {
            log.error("Error fetching bank details: ", e);
            return ResponseEntity.status(500).body(objectMapper.createObjectNode()
                    .put("error", "Failed to fetch bank details: " + e.getMessage()));
        }
    }
}