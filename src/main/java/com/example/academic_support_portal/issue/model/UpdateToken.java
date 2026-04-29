package com.example.academic_support_portal.issue.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "issue_update_tokens")
public class UpdateToken {
    
    @Id
    private String id;
    
    private String token;
    
    private String issueId;
    
    private String status;  
    
    private Boolean isUsed;  
    
    private LocalDateTime createdAt;
    
    private LocalDateTime expiresAt;  
    
    private String usedByEmail;  
    
    private String usedAt;
}
