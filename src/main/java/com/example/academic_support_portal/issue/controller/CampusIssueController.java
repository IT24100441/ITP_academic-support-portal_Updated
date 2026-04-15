package com.example.academic_support_portal.issue.controller;

import com.example.academic_support_portal.issue.dto.IssueAssignRequest;
import com.example.academic_support_portal.issue.dto.IssueCommentRequest;
import com.example.academic_support_portal.issue.dto.IssueCommentResponse;
import com.example.academic_support_portal.issue.dto.IssueCreateRequest;
import com.example.academic_support_portal.issue.dto.IssueResponse;
import com.example.academic_support_portal.issue.dto.IssueStatusUpdateRequest;
import com.example.academic_support_portal.issue.dto.IssueUpdateRequest;
import com.example.academic_support_portal.issue.model.IssuePriority;
import com.example.academic_support_portal.issue.model.IssueStatus;
import com.example.academic_support_portal.issue.service.IssueService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class CampusIssueController {

  private final IssueService issueService;

  @GetMapping
  public List<IssueResponse> getAllIssues(
      @RequestParam(required = false) IssueStatus status,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String building,
      @RequestParam(required = false) IssuePriority priority,
      @RequestParam(required = false) String assignedToUserId,
      @RequestParam(required = false) String createdByUserId,
      @RequestParam(required = false) String keyword) {
    return issueService.getAllIssues(status, category, building, priority, assignedToUserId, createdByUserId, keyword);
  }

  @GetMapping("/{id}")
  public IssueResponse getIssueById(@PathVariable String id) {
    return issueService.getIssueById(id);
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public IssueResponse createIssue(@Valid @RequestBody IssueCreateRequest request) {
    return issueService.createIssue(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public IssueResponse updateIssue(@PathVariable String id, @Valid @RequestBody IssueUpdateRequest request) {
    return issueService.updateIssue(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public void deleteIssue(@PathVariable String id) {
    issueService.deleteIssue(id);
  }

  @PatchMapping("/{id}/assign")
  @PreAuthorize("hasRole('ADMIN')")
  public IssueResponse assignIssue(@PathVariable String id, @Valid @RequestBody IssueAssignRequest request) {
    return issueService.assignIssue(id, request);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public IssueResponse updateStatus(@PathVariable String id, @Valid @RequestBody IssueStatusUpdateRequest request) {
    return issueService.updateStatus(id, request);
  }

  @PostMapping("/{id}/comments")
  @PreAuthorize("isAuthenticated()")
  public IssueCommentResponse addComment(@PathVariable String id, @Valid @RequestBody IssueCommentRequest request) {
    return issueService.addComment(id, request);
  }

  @GetMapping("/{id}/comments")
  public List<IssueCommentResponse> getComments(@PathVariable String id) {
    return issueService.getCommentsByIssue(id);
  }

  /**
   * Endpoint for one-click status update from email (returns HTML)
   */
  @GetMapping("/update-status")
  public ResponseEntity<String> updateStatusViaToken(
          @RequestParam String token,
          @RequestParam String status,
          @RequestParam(required = false) String note,
          HttpServletRequest request) {
      
      String userEmail = request.getRemoteAddr();
      IssueResponse response = issueService.updateStatusViaToken(token, status, note, userEmail);
      
      String html = String.format("""
          <!DOCTYPE html>
          <html>
          <head>
              <title>Status Updated</title>
              <style>
                  body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #f3f4f6; }
                  .container { max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                  h1 { color: #10b981; }
                  .button { display: inline-block; margin-top: 20px; padding: 10px 20px; background: #4F46E5; color: white; text-decoration: none; border-radius: 8px; }
              </style>
          </head>
          <body>
              <div class="container">
                  <h1>✓ Status Updated</h1>
                  <p>Issue <strong>%s</strong> has been marked as <strong>%s</strong>.</p>
                  <p>%s</p>
                  <a href="/issues/%s" class="button">View Issue</a>
              </div>
          </body>
          </html>
          """, response.getId(), status, note != null ? "Note: " + note : "", response.getId());
      
      return ResponseEntity.ok(html);
  }

  /**
   * Endpoint for adding a note via token from email
   */
  @GetMapping("/add-note")
  public ResponseEntity<String> addNoteViaToken(
          @RequestParam String token,
          @RequestParam(required = false) String note,
          HttpServletRequest request) {
      
      if (note == null || note.trim().isEmpty()) {
          String html = String.format("""
              <!DOCTYPE html>
              <html>
              <head>
                  <title>Add Note</title>
                  <style>
                      body { font-family: Arial, sans-serif; padding: 50px; background: #f3f4f6; }
                      .container { max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                      textarea { width: 100%%; padding: 10px; border: 1px solid #e5e7eb; border-radius: 8px; margin: 15px 0; font-family: inherit; }
                      button { background: #4F46E5; color: white; padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; }
                      button:hover { background: #4338ca; }
                  </style>
              </head>
              <body>
                  <div class="container">
                      <h2>Add a Note to Issue</h2>
                      <form method="get" action="/api/issues/add-note">
                          <input type="hidden" name="token" value="%s">
                          <textarea name="note" rows="5" placeholder="Enter your note here..." required></textarea>
                          <button type="submit">Add Note</button>
                      </form>
                  </div>
              </body>
              </html>
              """, token);
          return ResponseEntity.ok(html);
      }
      
      String userEmail = request.getRemoteAddr();
      IssueCommentResponse response = issueService.addNoteViaToken(token, note, userEmail);
      
      String html = String.format("""
          <!DOCTYPE html>
          <html>
          <head>
              <title>Note Added</title>
              <style>
                  body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #f3f4f6; }
                  .container { max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                  h1 { color: #10b981; }
                  .button { display: inline-block; margin-top: 20px; padding: 10px 20px; background: #4F46E5; color: white; text-decoration: none; border-radius: 8px; }
              </style>
          </head>
          <body>
              <div class="container">
                  <h1>✓ Note Added</h1>
                  <p>Your note has been added to the issue.</p>
                  <a href="/issues/%s" class="button">View Issue</a>
              </div>
          </body>
          </html>
          """, response.getIssueId());
      
      return ResponseEntity.ok(html);
  }

}
