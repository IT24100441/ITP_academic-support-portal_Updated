package com.example.academic_support_portal.issue.controller;

import com.example.academic_support_portal.issue.dto.IssueResponse;
import com.example.academic_support_portal.issue.model.UpdateToken;
import com.example.academic_support_portal.issue.repository.UpdateTokenRepository;
import com.example.academic_support_portal.issue.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/issues")
@RequiredArgsConstructor
public class PublicIssueController {

    private final IssueService issueService;
    private final UpdateTokenRepository updateTokenRepository;

    @GetMapping("/{id}/data")
    public IssueResponse getIssueData(@PathVariable String id, @RequestParam String token) {
        // Verify token is valid
        UpdateToken updateToken = updateTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired token"));
        
        // Verify token belongs to this issue
        if (!updateToken.getIssueId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token does not match this issue");
        }
        
        return issueService.getIssueById(id);
    }

    /**
     * Check if token is valid (for frontend)
     */
    @GetMapping("/check-token")
    public ResponseEntity<?> checkToken(@RequestParam String token, @RequestParam String issueId) {
        UpdateToken updateToken = updateTokenRepository.findByToken(token).orElse(null);
        
        if (updateToken == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", "Invalid token");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        if (!updateToken.getIssueId().equals(issueId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", "Token does not match this issue");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("isUsed", updateToken.getIsUsed() != null && updateToken.getIsUsed());
        response.put("expiresAt", updateToken.getExpiresAt());
        
        return ResponseEntity.ok(response);
    }
}
