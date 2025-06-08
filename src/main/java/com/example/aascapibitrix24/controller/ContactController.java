package com.example.aascapibitrix24.controller;

import com.example.aascapibitrix24.service.ContactService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@Slf4j
public class ContactController {

    private final ContactService contactService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    // Lấy danh sách contact
    @GetMapping
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
    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getContact(@PathVariable String id) {
        try {
            JsonNode result = contactService.getContact(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting contact {}: ", id, e);
            return ResponseEntity.status(500).body(null);
        }
    }

    // Tạo contact mới
    @PostMapping
    public ResponseEntity<JsonNode> createContact(@RequestBody Map<String, Object> contactData) {
        try {
            JsonNode result = contactService.createContact(contactData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating contact: ", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    // Cập nhật contact
    @PutMapping("/{id}")
    public ResponseEntity<JsonNode> updateContact(@PathVariable String id,
                                                  @RequestBody Map<String, Object> contactData) {
        try {
            JsonNode result = contactService.updateContact(id, contactData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating contact {}: ", id, e);
            return ResponseEntity.status(500).body(null);
        }
    }

    // Xóa contact
    @DeleteMapping("/{id}")
    public ResponseEntity<JsonNode> deleteContact(@PathVariable String id) {
        try {
            JsonNode result = contactService.deleteContact(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error deleting contact {}: ", id, e);
            return ResponseEntity.status(500).body(null);
        }
    }
}