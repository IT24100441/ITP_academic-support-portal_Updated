package com.example.academic_support_portal.issue.dto;

import com.example.academic_support_portal.issue.model.IssueStatus;
import com.example.academic_support_portal.issue.model.SupportingDocument;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class IssueResponse {
  private String id;
  private String title;
  private String category;
  private String description;
  private List<String> imageUrls;
  private String building;
  private String locationText;
  private Double latitude;
  private Double longitude;
  private IssueStatus status;
  private String createdByUserId;
  private String createdByName;
  private String assignedToUserId;
  private String assignedToName;
  private String adminNotes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<SupportingDocument> supportingDocs;
}
