package com.techwizards.club.controller;

import com.techwizards.club.model.SecurityLog;
import com.techwizards.club.service.SecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/security")
@CrossOrigin(origins = "*")
public class SecurityController {

    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    // Get recent logs
    @GetMapping("/logs")
    public ResponseEntity<List<SecurityLog>> getSecurityLogs() {
        return ResponseEntity.ok(securityService.getSecurityLogs());
    }

    // Get live status metrics
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSecurityStatus() {
        return ResponseEntity.ok(securityService.getSecurityStatus());
    }

    // Toggle custom shields (e.g. DDOS, firewall, App Shield)
    @PostMapping("/shield")
    public ResponseEntity<Map<String, Object>> toggleShield(@RequestParam String layer, @RequestParam boolean enable, @RequestParam String username) {
        Optional<String> logDescOpt = securityService.toggleShield(layer, enable, username);
        if (logDescOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", logDescOpt.get());
        return ResponseEntity.ok(response);
    }

    // Trigger mock backup
    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> triggerBackup(@RequestParam String username) {
        securityService.triggerBackup(username);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "System backup generated and stored successfully.");
        return ResponseEntity.ok(response);
    }

    // Trigger mock restore
    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> triggerRestore(@RequestParam String username) {
        securityService.triggerRestore(username);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "System state roll-back finished. All core systems are operational.");
        return ResponseEntity.ok(response);
    }
}

