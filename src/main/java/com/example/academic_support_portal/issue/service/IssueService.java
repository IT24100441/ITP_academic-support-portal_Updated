package com.example.academic_support_portal.issue.service;

import com.example.academic_support_portal.issue.dto.IssueAssignRequest;
import com.example.academic_support_portal.issue.dto.IssueCommentRequest;
import com.example.academic_support_portal.issue.dto.IssueCommentResponse;
import com.example.academic_support_portal.issue.dto.IssueCreateRequest;
import com.example.academic_support_portal.issue.dto.IssueResponse;
import com.example.academic_support_portal.issue.dto.IssueStatusUpdateRequest;
import com.example.academic_support_portal.issue.dto.IssueUpdateRequest;
import com.example.academic_support_portal.issue.model.CampusIssue;
import com.example.academic_support_portal.issue.model.SupportingDocument;
import com.example.academic_support_portal.issue.model.UpdateToken;
import com.example.academic_support_portal.issue.model.IssueComment;
import com.example.academic_support_portal.issue.model.IssuePriority;
import com.example.academic_support_portal.issue.model.IssueStatus;
import com.example.academic_support_portal.issue.model.IssueTimelineType;
import com.example.academic_support_portal.issue.repository.IssueCommentRepository;
import com.example.academic_support_portal.issue.repository.IssueRepository;
import com.example.academic_support_portal.issue.repository.UpdateTokenRepository;
import com.example.academic_support_portal.user.model.Role;
import com.example.academic_support_portal.user.model.User;
import com.example.academic_support_portal.user.repository.UserRepository;
import com.example.academic_support_portal.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueService {

  private final IssueRepository issueRepository;
  private final IssueCommentRepository commentRepository;
  private final UserRepository userRepository;
  private final MongoTemplate mongoTemplate;
  private final EmailService emailService;
  private final UpdateTokenRepository updateTokenRepository;

  public IssueResponse createIssue(IssueCreateRequest request) {
    User user = getCurrentUser();
    ensureLocationProvided(request.getBuilding(), request.getLocationText(), request.getCategory());

    IssuePriority priority = Optional.ofNullable(request.getPriority()).orElse(IssuePriority.MEDIUM);
    LocalDateTime now = LocalDateTime.now();

    CampusIssue issue = CampusIssue.builder()
        .title(request.getTitle())
        .category(request.getCategory())
        .description(request.getDescription())
        .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : new ArrayList<>())
        .building(request.getBuilding())
        .locationText(request.getLocationText())
        .latitude(request.getLatitude())
        .longitude(request.getLongitude())
        .priority(priority)
        .status(IssueStatus.OPEN)
        .createdByUserId(user.getId())
        .createdByName(user.getName())
        .studentEmail(user.getEmail())
        .supportingDocs(request.getSupportingDocs() != null ? request.getSupportingDocs() : new ArrayList<>())
        .createdAt(now)
        .updatedAt(now)
        .build();

    CampusIssue saved = issueRepository.save(issue);
    commentRepository.save(IssueComment.builder()
        .issueId(saved.getId())
        .userId(user.getId())
        .userName(user.getName())
        .message("Issue created")
        .type(IssueTimelineType.STATUS_CHANGE)
        .createdAt(now)
        .build());

    // Generate update token for email links
    String token = UUID.randomUUID().toString();
    UpdateToken updateToken = UpdateToken.builder()
        .token(token)
        .issueId(saved.getId())
        .status(null)
        .isUsed(false)
        .createdAt(now)
        .expiresAt(now.plusDays(30))
        .build();
    updateTokenRepository.save(updateToken);

    String departmentEmail = getDepartmentEmail(saved.getCategory());

        try {
      emailService.sendNewIssueEmail(
          departmentEmail,
          saved.getId(),
          saved.getTitle(),
          saved.getCategory(),
          saved.getDescription(),
          saved.getBuilding() != null ? saved.getBuilding() : saved.getLocationText(),
          saved.getPriority() != null ? saved.getPriority().toString() : "MEDIUM",
          now.toString(),
          user.getName(),
          user.getEmail(),  // ← ADD THIS LINE - student's email for Reply-To
          token,
          saved.getImageUrls(),
          saved.getSupportingDocs());
    } catch (Exception e) {
      System.err.println("Failed to send email: " + e.getMessage());
    }

    return toResponse(saved);
  }

  public List<IssueResponse> getAllIssues(
      IssueStatus status,
      String category,
      String building,
      IssuePriority priority,
      String assignedToUserId,
      String createdByUserId,
      String keyword) {

    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();

    if (status != null) {
      criteria.add(Criteria.where("status").is(status));
    }
    if (StringUtils.hasText(category)) {
      criteria.add(Criteria.where("category").is(category));
    }
    if (StringUtils.hasText(building)) {
      criteria.add(Criteria.where("building").is(building));
    }
    if (priority != null) {
      criteria.add(Criteria.where("priority").is(priority));
    }
    if (StringUtils.hasText(assignedToUserId)) {
      criteria.add(Criteria.where("assignedToUserId").is(assignedToUserId));
    }
    if (StringUtils.hasText(createdByUserId)) {
      criteria.add(Criteria.where("createdByUserId").is(createdByUserId));
    }
    if (StringUtils.hasText(keyword)) {
      criteria.add(new Criteria().orOperator(
          Criteria.where("title").regex(keyword, "i"),
          Criteria.where("description").regex(keyword, "i")));
    }

    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    List<CampusIssue> issues = mongoTemplate.find(query, CampusIssue.class);
    return issues.stream().map(this::toResponse).toList();
  }

  public IssueResponse getIssueById(String id) {
    CampusIssue issue = issueRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    return toResponse(issue);
  }

  public IssueResponse updateIssue(String id, IssueUpdateRequest request) {
    CampusIssue issue = issueRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    User user = getCurrentUser();

    boolean isAdmin = isAdmin(user);
    boolean isOwner = issue.getCreatedByUserId() != null && issue.getCreatedByUserId().equals(user.getId());
    if (!isAdmin && !(isOwner && issue.getStatus() == IssueStatus.OPEN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to update this issue");
    }

    // Store old values before updating (for email notification)
    String oldCategory = issue.getCategory();
    String oldTitle = issue.getTitle();
    String oldDescription = issue.getDescription();
    String oldBuilding = issue.getBuilding();
    String oldLocationText = issue.getLocationText();

    boolean hasChanges = false;

    if (StringUtils.hasText(request.getTitle())) {
      issue.setTitle(request.getTitle());
      hasChanges = true;
    }
    if (StringUtils.hasText(request.getCategory())) {
      issue.setCategory(request.getCategory());
      hasChanges = true;
    }
    if (StringUtils.hasText(request.getDescription())) {
      issue.setDescription(request.getDescription());
      hasChanges = true;
    }
    if (request.getImageUrls() != null) {
      issue.setImageUrls(request.getImageUrls());
      hasChanges = true;
    }
    if (request.getSupportingDocs() != null) {
      issue.setSupportingDocs(request.getSupportingDocs());
      hasChanges = true;
    }
    if (request.getBuilding() != null) {
      issue.setBuilding(request.getBuilding());
      hasChanges = true;
    }
    if (request.getLocationText() != null) {
      issue.setLocationText(request.getLocationText());
      hasChanges = true;
    }
    if (request.getLatitude() != null) {
      issue.setLatitude(request.getLatitude());
      hasChanges = true;
    }
    if (request.getLongitude() != null) {
      issue.setLongitude(request.getLongitude());
      hasChanges = true;
    }
    if (request.getPriority() != null) {
      issue.setPriority(request.getPriority());
      hasChanges = true;
    }
    if (isAdmin && request.getAdminNotes() != null) {
      issue.setAdminNotes(request.getAdminNotes());
      hasChanges = true;
    }

    // Also handle floor if present in request
    if (request.getFloor() != null) {
      issue.setFloor(request.getFloor());
      hasChanges = true;
    }

    // Handle academic fields if present
    if (request.getAcademicIssueCategory() != null) {
      issue.setAcademicIssueCategory(request.getAcademicIssueCategory());
      hasChanges = true;
    }
    if (request.getFaculty() != null) {
      issue.setFaculty(request.getFaculty());
      hasChanges = true;
    }
    if (request.getModuleCode() != null) {
      issue.setModuleCode(request.getModuleCode());
      hasChanges = true;
    }

    ensureLocationProvided(issue.getBuilding(), issue.getLocationText(), issue.getCategory());
    issue.setUpdatedAt(LocalDateTime.now());

    CampusIssue saved = issueRepository.save(issue);

    // Add timeline entry for the update
    if (hasChanges) {
      String changeSummary = buildChangeSummary(oldTitle, saved.getTitle(), oldCategory, saved.getCategory(),
          oldDescription, saved.getDescription(), oldBuilding, saved.getBuilding(),
          oldLocationText, saved.getLocationText());

      commentRepository.save(IssueComment.builder()
          .issueId(saved.getId())
          .userId(user.getId())
          .userName(user.getName())
          .message("Issue updated: " + changeSummary)
          .type(IssueTimelineType.COMMENT)
          .createdAt(LocalDateTime.now())
          .build());

      // Send email notifications
      try {
        String studentEmail = saved.getStudentEmail();
        String studentName = saved.getCreatedByName();

        String location = saved.getBuilding() != null ? saved.getBuilding() : saved.getLocationText();
        if (location == null) location = "Not specified";

        List<String> imageUrls = saved.getImageUrls() != null ? saved.getImageUrls() : new ArrayList<>();
        List<SupportingDocument> supportingDocs = saved.getSupportingDocs() != null ? saved.getSupportingDocs() : new ArrayList<>();

        boolean categoryChanged = oldCategory != null && !oldCategory.equals(saved.getCategory());
        
        String tokenToUse = null;
        
        if (categoryChanged) {
    // Category changed - create and save a NEW token for the new department
    String newToken = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now();
    UpdateToken newUpdateToken = UpdateToken.builder()
        .token(newToken)
        .issueId(saved.getId())
        .status(null)
        .isUsed(false)
        .createdAt(now)
        .expiresAt(now.plusDays(30))
        .build();
    updateTokenRepository.save(newUpdateToken);
    tokenToUse = newToken;
    log.info("Created NEW token for category change: {} -> {}, token={}", oldCategory, saved.getCategory(), newToken);
} else {
    tokenToUse = updateTokenRepository.findByIssueId(saved.getId())
        .map(UpdateToken::getToken)
        .orElse(null);
    log.info("Using existing token for update: {}", tokenToUse);
}

// Then pass tokenToUse to email service
emailService.sendIssueUpdateEmail(
    oldCategory,
    saved.getCategory(),
    saved.getId(),
    saved.getTitle(),
    saved.getDescription(),
    location,
    user.getName(),
    studentEmail,
    studentName,
    imageUrls,
    supportingDocs,
    tokenToUse  // This is the token that should be used in the email
);
            
      } catch (Exception e) {
        log.error("Failed to send issue update email for issueId={}: {}", saved.getId(), e.getMessage());
      }
    }

    return toResponse(saved);
  }

  /**
   * Build a summary of changes for the timeline comment
   */
  private String buildChangeSummary(String oldTitle, String newTitle, String oldCategory, String newCategory,
      String oldDescription, String newDescription, String oldBuilding, String newBuilding,
      String oldLocationText, String newLocationText) {

    List<String> changes = new ArrayList<>();

    if (!oldTitle.equals(newTitle)) {
      changes.add("title changed from '" + oldTitle + "' to '" + newTitle + "'");
    }
    if (!oldCategory.equals(newCategory)) {
      changes.add("category changed from " + oldCategory + " to " + newCategory);
    }
    if (!oldDescription.equals(newDescription)) {
      changes.add("description updated");
    }
    if (oldBuilding != null && !oldBuilding.equals(newBuilding) && newBuilding != null) {
      changes.add("building changed from " + oldBuilding + " to " + newBuilding);
    }
    if (oldLocationText != null && !oldLocationText.equals(newLocationText) && newLocationText != null) {
      changes.add("location changed from " + oldLocationText + " to " + newLocationText);
    }

    if (changes.isEmpty()) {
      return "details updated";
    }
    return String.join(", ", changes);
  }

  public void deleteIssue(String id) {
    CampusIssue issue = issueRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    User user = getCurrentUser();
    boolean isAdmin = isAdmin(user);
    boolean isOwner = issue.getCreatedByUserId() != null && issue.getCreatedByUserId().equals(user.getId());
    if (!isAdmin && !(isOwner && issue.getStatus() == IssueStatus.OPEN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this issue");
    }
    issueRepository.deleteById(id);
    commentRepository.findByIssueIdOrderByCreatedAtAsc(id)
        .forEach(commentRepository::delete);
  }

  public IssueResponse assignIssue(String id, IssueAssignRequest request) {
    CampusIssue issue = issueRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    User admin = requireAdmin();

    User assignee = userRepository.findById(request.getAssignedToUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned user not found"));

    issue.setAssignedToUserId(assignee.getId());
    issue.setAssignedToName(assignee.getName());
    if (issue.getStatus() == IssueStatus.OPEN) {
      issue.setStatus(IssueStatus.IN_PROGRESS);
    }
    issue.setUpdatedAt(LocalDateTime.now());

    CampusIssue saved = issueRepository.save(issue);
    commentRepository.save(IssueComment.builder()
        .issueId(saved.getId())
        .userId(admin.getId())
        .userName(admin.getName())
        .message("Assigned to " + assignee.getName())
        .type(IssueTimelineType.ASSIGNMENT)
        .createdAt(LocalDateTime.now())
        .build());

    return toResponse(saved);
  }

  public IssueResponse updateStatus(String id, IssueStatusUpdateRequest request) {
    CampusIssue issue = issueRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    User admin = requireAdmin();

    issue.setStatus(request.getStatus());
    if (request.getAdminNotes() != null) {
      issue.setAdminNotes(request.getAdminNotes());
    }
    issue.setUpdatedAt(LocalDateTime.now());

    CampusIssue saved = issueRepository.save(issue);

    String note = StringUtils.hasText(request.getNote())
        ? request.getNote()
        : "Status updated to " + request.getStatus();
    commentRepository.save(IssueComment.builder()
        .issueId(saved.getId())
        .userId(admin.getId())
        .userName(admin.getName())
        .message(note)
        .type(IssueTimelineType.STATUS_CHANGE)
        .createdAt(LocalDateTime.now())
        .build());

    return toResponse(saved);
  }

  public IssueCommentResponse addComment(String issueId, IssueCommentRequest request) {
    CampusIssue issue = issueRepository.findById(issueId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    User user = getCurrentUser();

    IssueTimelineType type = Optional.ofNullable(request.getType()).orElse(IssueTimelineType.COMMENT);
    if (type == IssueTimelineType.NOTE && !isAdmin(user)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can add notes");
    }

    IssueComment comment = commentRepository.save(IssueComment.builder()
        .issueId(issue.getId())
        .userId(user.getId())
        .userName(user.getName())
        .message(request.getMessage())
        .type(type)
        .createdAt(LocalDateTime.now())
        .build());

    issue.setUpdatedAt(LocalDateTime.now());
    issueRepository.save(issue);

        // Send email notification to department when student adds a comment
    try {
      String departmentEmail = getDepartmentEmail(issue.getCategory());
      if (departmentEmail != null && StringUtils.hasText(departmentEmail)) {
        String studentEmail = user.getEmail();
        String studentName = user.getName();
        
        emailService.sendIssueCommentEmail(
            departmentEmail,
            issue.getId(),
            issue.getTitle(),
            request.getMessage(),
            studentName,
            studentEmail);
      }
    } catch (Exception e) {
      log.error("Failed to send comment notification email: {}", e.getMessage());
    }

    return toCommentResponse(comment);
  }

  public List<IssueCommentResponse> getCommentsByIssue(String issueId) {
    issueRepository.findById(issueId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId)
        .stream()
        .map(this::toCommentResponse)
        .toList();
  }

  public IssueResponse updateStatusViaToken(String token, String status, String note, String userEmail) {
    UpdateToken updateToken = updateTokenRepository.findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired token"));

    CampusIssue issue = issueRepository.findById(updateToken.getIssueId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

    IssueStatus newStatus;
    try {
      newStatus = IssueStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
    }

    if (issue.getStatus() == IssueStatus.RESOLVED || issue.getStatus() == IssueStatus.REJECTED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "This issue is already " + issue.getStatus() + " and cannot be changed");
    }

    if (issue.getStatus() == newStatus) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Issue is already " + newStatus);
    }

    IssueStatus oldStatus = issue.getStatus();

    issue.setStatus(newStatus);
    issue.setUpdatedAt(LocalDateTime.now());

    if (note != null && !note.trim().isEmpty()) {
      String existingNotes = issue.getAdminNotes();
      if (existingNotes != null && !existingNotes.isEmpty()) {
        issue.setAdminNotes(existingNotes + "\n[" + LocalDateTime.now() + "] " + note);
      } else {
        issue.setAdminNotes("[" + LocalDateTime.now() + "] " + note);
      }
    }

    CampusIssue saved = issueRepository.save(issue);

    if (newStatus == IssueStatus.RESOLVED || newStatus == IssueStatus.REJECTED) {
      updateToken.setIsUsed(true);
      updateToken.setUsedByEmail(userEmail);
      updateToken.setUsedAt(LocalDateTime.now().toString());
      updateTokenRepository.save(updateToken);
    }

    commentRepository.save(IssueComment.builder()
        .issueId(saved.getId())
        .userId("system")
        .userName("System")
        .message(String.format("Status updated from %s to %s via email", oldStatus, newStatus) +
            (note != null && !note.isEmpty() ? ": " + note : ""))
        .type(IssueTimelineType.STATUS_CHANGE)
        .createdAt(LocalDateTime.now())
        .build());

    return toResponse(saved);
  }

  public IssueCommentResponse addNoteViaToken(String token, String note, String userEmail) {
    UpdateToken updateToken = updateTokenRepository.findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired token"));

    CampusIssue issue = issueRepository.findById(updateToken.getIssueId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

    if (note == null || note.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note cannot be empty");
    }

    IssueComment comment = commentRepository.save(IssueComment.builder()
        .issueId(issue.getId())
        .userId("system")
        .userName("Staff")
        .message(note)
        .type(IssueTimelineType.NOTE)
        .createdAt(LocalDateTime.now())
        .build());

    updateToken.setIsUsed(true);
    updateToken.setUsedByEmail(userEmail);
    updateToken.setUsedAt(LocalDateTime.now().toString());
    updateTokenRepository.save(updateToken);

    return toCommentResponse(comment);
  }

  private void ensureLocationProvided(String building, String locationText, String category) {
    // Only validate for categories that require location
    if (category != null && (category.equals("FACILITIES") || category.equals("IT_SERVICES"))) {
      if (!StringUtils.hasText(building) && !StringUtils.hasText(locationText)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Building or location text is required for " + category + " issues");
      }
    }
    // For SECURITY, OTHER, ACADEMIC - location is optional or auto-generated
  }

  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    String email = authentication.getName();
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }

  private boolean isAdmin(User user) {
    if (user == null) {
      return false;
    }
    if (user.getRoles() != null && user.getRoles().contains(Role.ADMIN)) {
      return true;
    }
    return user.getRole() == Role.ADMIN;
  }

  private User requireAdmin() {
    User user = getCurrentUser();
    if (!isAdmin(user)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
    return user;
  }

  private String getDepartmentEmail(String category) {
    Map<String, String> departmentEmails = new HashMap<>();
    departmentEmails.put("FACILITIES", "kkdsashani@gmail.com");
    departmentEmails.put("IT_SERVICES", "kkdsashani@gmail.com");
    departmentEmails.put("SECURITY", "kkdsashani@gmail.com");
    departmentEmails.put("ACADEMIC", "kkdsashani@gmail.com");
    departmentEmails.put("OTHER", "kkdsashani@gmail.com");

    return departmentEmails.getOrDefault(category, "kkdsashani@gmail.com");
  }

  private IssueResponse toResponse(CampusIssue issue) {
    return IssueResponse.builder()
        .id(issue.getId())
        .title(issue.getTitle())
        .category(issue.getCategory())
        .description(issue.getDescription())
        .imageUrls(issue.getImageUrls() != null ? issue.getImageUrls() : new ArrayList<>())
        .building(issue.getBuilding())
        .locationText(issue.getLocationText())
        .latitude(issue.getLatitude())
        .longitude(issue.getLongitude())
        .status(issue.getStatus())
        .priority(issue.getPriority())
        .createdByUserId(issue.getCreatedByUserId())
        .createdByName(issue.getCreatedByName())
        .assignedToUserId(issue.getAssignedToUserId())
        .assignedToName(issue.getAssignedToName())
        .adminNotes(issue.getAdminNotes())
        .createdAt(issue.getCreatedAt())
        .updatedAt(issue.getUpdatedAt())
        .supportingDocs(issue.getSupportingDocs() != null ? issue.getSupportingDocs() : new ArrayList<>())
        .build();
  }

  private IssueCommentResponse toCommentResponse(IssueComment comment) {
    return IssueCommentResponse.builder()
        .id(comment.getId())
        .issueId(comment.getIssueId())
        .userId(comment.getUserId())
        .userName(comment.getUserName())
        .message(comment.getMessage())
        .type(comment.getType())
        .createdAt(comment.getCreatedAt())
        .build();
  }
}