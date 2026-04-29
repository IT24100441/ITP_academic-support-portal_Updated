package com.example.academic_support_portal.issue.repository;

import com.example.academic_support_portal.issue.model.UpdateToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UpdateTokenRepository extends MongoRepository<UpdateToken, String> {
    Optional<UpdateToken> findByToken(String token);
    Optional<UpdateToken> findByIssueId(String issueId);
    void deleteByIssueId(String issueId);
}
