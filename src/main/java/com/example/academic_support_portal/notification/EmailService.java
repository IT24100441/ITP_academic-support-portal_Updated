package com.example.academic_support_portal.notification;

import com.example.academic_support_portal.tutor.model.TutoringSession;
import com.example.academic_support_portal.tutor_request.model.TutorRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

  private final JavaMailSender studentMailSender;
  private final JavaMailSender tutorMailSender;
  private final JavaMailSender issueMailSender;

  public EmailService(
      @Qualifier("studentMailSender") JavaMailSender studentMailSender,
      @Qualifier("tutorMailSender") JavaMailSender tutorMailSender,
      @Qualifier("issueMailSender") JavaMailSender issueMailSender) {
    this.studentMailSender = studentMailSender;
    this.tutorMailSender = tutorMailSender;
    this.issueMailSender = issueMailSender;
  }

  @Value("${spring.mail.student.from:${spring.mail.student.username}}")
  private String studentFromAddress;

  @Value("${spring.mail.tutor.from:${spring.mail.tutor.username}}")
  private String tutorFromAddress;

  @Value("${spring.mail.issue.from:${spring.mail.issue.username}}")
  private String issueFromAddress;

  @Value("${app.frontend.url:http://localhost:5173}")
  private String frontendUrl;

  @Value("${app.mail.enabled:false}")
  private boolean mailEnabled;

  // ========== TUTORING EMAIL METHODS (Friend's Code - DO NOT MODIFY) ==========

  public void sendTutorRequestEmailToTutor(TutorRequest request) {
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping tutor request email. tutorEmail={} requestId={}", request.getTutorEmail(), request.getId());
      return;
    }
    log.info("Preparing tutor request email. tutorEmail={} requestId={}", request.getTutorEmail(), request.getId());
    String subject = "New Tutor Request - Academic Support Portal";
    String body = buildTutorRequestTemplate(request);
    sendTutorMail(request.getTutorEmail(), subject, body);
    log.info("Tutor request email dispatched to mail sender. tutorEmail={} requestId={}", request.getTutorEmail(), request.getId());
  }

  public void sendAcceptanceEmailToStudent(TutorRequest request) {
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping session confirmation email. studentEmail={} requestId={}", request.getStudentEmail(), request.getId());
      return;
    }
    String subject = "Your Tutoring Session is Confirmed";
    String body = buildSessionConfirmationTemplate(request);
    sendStudentMail(request.getStudentEmail(), subject, body);
  }

  public void notifyTutorOfNewRequest(TutoringSession session) {
    if (session == null) {
      log.warn("Session is null. Skipping tutor notification.");
      return;
    }
    String tutorEmail = safeEmail(session.getTutorEmail());
    if (!StringUtils.hasText(tutorEmail)) {
      log.warn("Tutor email missing. Skipping new request notification. sessionId={}", session.getId());
      return;
    }
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping tutor notification. tutorEmail={} sessionId={}", tutorEmail, session.getId());
      return;
    }
    String subject = "New Tutoring Request Received";
    String body = "Hello " + safe(session.getTutorName()) + ",\n\n"
        + "You have received a new tutoring request.\n\n"
        + "----------- Request Details -----------\n"
        + "Student Name : " + safe(session.getStudentName()) + "\n"
        + "Subject      : " + safe(session.getSubject()) + "\n"
        + "Date         : " + safe(session.getDate()) + "\n"
        + "Time         : " + safe(session.getTime()) + "\n"
        + "Mode         : " + safe(session.getMode()) + "\n"
        + "Message      : " + safe(session.getNote()) + "\n"
        + "---------------------------------------\n\n"
        + "Open dashboard:\n"
        + frontendUrl + "/support/requests\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
    sendTutorMail(tutorEmail, subject, body);
    log.info("Tutor request notification sent. sessionId={} tutorEmail={}", session.getId(), tutorEmail);
  }

  public void notifyStudentOfAcceptedRequest(TutoringSession session) {
    if (session == null) {
      log.warn("Session is null. Skipping student acceptance notification.");
      return;
    }
    String studentEmail = safeEmail(session.getStudentEmail());
    if (!StringUtils.hasText(studentEmail)) {
      log.warn("Student email missing. Skipping acceptance notification. sessionId={}", session.getId());
      return;
    }
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping acceptance notification. studentEmail={} sessionId={}", studentEmail, session.getId());
      return;
    }
    String subject = "Your Tutoring Request Was Accepted";
    String body = "Hello " + safe(session.getStudentName()) + ",\n\n"
        + "Your tutoring request has been accepted.\n\n"
        + "-------- Confirmed Session Details --------\n"
        + "Tutor Name   : " + safe(session.getTutorName()) + "\n"
        + "Subject      : " + safe(session.getSubject()) + "\n"
        + "Date         : " + safe(session.getDate()) + "\n"
        + "Time         : " + safe(session.getTime()) + "\n"
        + "Mode         : " + safe(session.getMode()) + "\n"
        + "Status       : " + safe(session.getStatus()) + "\n"
        + "Tutor Note   : " + safe(session.getTutorNote()) + "\n"
        + "-------------------------------------------\n\n"
        + "Open dashboard:\n"
        + frontendUrl + "/support/requests\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
    sendStudentMail(studentEmail, subject, body);
    log.info("Student acceptance notification sent. sessionId={} studentEmail={}", session.getId(), studentEmail);
  }

  public void notifyStudentOfRejectedRequest(TutoringSession session) {
    if (session == null) {
      log.warn("Session is null. Skipping student rejection notification.");
      return;
    }
    String studentEmail = safeEmail(session.getStudentEmail());
    if (!StringUtils.hasText(studentEmail)) {
      log.warn("Student email missing. Skipping rejection notification. sessionId={}", session.getId());
      return;
    }
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping rejection notification. studentEmail={} sessionId={}", studentEmail, session.getId());
      return;
    }
    String subject = "Your Tutoring Request Was Rejected";
    String body = "Hello " + safe(session.getStudentName()) + ",\n\n"
        + "Your tutoring request has been rejected.\n\n"
        + "----------- Request Details -----------\n"
        + "Tutor Name   : " + safe(session.getTutorName()) + "\n"
        + "Subject      : " + safe(session.getSubject()) + "\n"
        + "Date         : " + safe(session.getDate()) + "\n"
        + "Time         : " + safe(session.getTime()) + "\n"
        + "Status       : " + safe(session.getStatus()) + "\n"
        + "Tutor Note   : " + safe(session.getTutorNote()) + "\n"
        + "---------------------------------------\n\n"
        + "Open dashboard:\n"
        + frontendUrl + "/support/requests\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
    sendStudentMail(studentEmail, subject, body);
    log.info("Student rejection notification sent. sessionId={} studentEmail={}", session.getId(), studentEmail);
  }

  public void sendBookingRequestEmail(
      String tutorEmail,
      String tutorName,
      String studentName,
      String subjectName,
      String sessionDate,
      String sessionTime,
      String note) {
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping booking request email. tutorEmail={}", tutorEmail);
      return;
    }
    String subject = "New Booking Request - Academic Support Portal";
    String body = "Hello " + safe(tutorName) + ",\n\n"
        + "You have a new tutoring booking request.\n\n"
        + "----------- Booking Details -----------\n"
        + "Student Name : " + safe(studentName) + "\n"
        + "Subject      : " + safe(subjectName) + "\n"
        + "Session Date : " + safe(sessionDate) + "\n"
        + "Time         : " + safe(sessionTime) + "\n"
        + "Note         : " + safe(note) + "\n"
        + "---------------------------------------\n\n"
        + "Open dashboard:\n"
        + frontendUrl + "/support/requests\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
    sendTutorMail(tutorEmail, subject, body);
  }

  public void sendSessionConfirmationForBooking(
      String studentEmail,
      String studentName,
      String tutorName,
      String subjectName,
      String sessionDate,
      String sessionTime,
      String joinLink) {
    if (studentEmail == null || studentEmail.isBlank()) {
      log.warn("Student email is missing. Skipping booking confirmation email.");
      return;
    }
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping booking confirmation email. studentEmail={}", studentEmail);
      return;
    }
    String subject = "Your Booking is Confirmed - Academic Support Portal";
    String body = "Hello " + safe(studentName) + ",\n\n"
        + "Your tutoring booking has been accepted by the tutor.\n\n"
        + "------ Confirmed Session Details ------\n"
        + "Tutor Name   : " + safe(tutorName) + "\n"
        + "Subject      : " + safe(subjectName) + "\n"
        + "Session Date : " + safe(sessionDate) + "\n"
        + "Time         : " + safe(sessionTime) + "\n"
        + "Join Link    : " + safe(joinLink) + "\n"
        + "---------------------------------------\n\n"
        + "Open dashboard:\n"
        + frontendUrl + "/support/requests\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
    sendStudentMail(studentEmail, subject, body);
  }

  // ========== ISSUE REPORTER EMAIL METHODS (Your Code) ==========

  /**
   * Send email to department when a new issue is reported (with Reply-To set to student)
   */
  public void sendNewIssueEmail(
      String departmentEmail,
      String issueId,
      String title,
      String category,
      String description,
      String location,
      String priority,
      String createdAt,
      String reporterName,
      String reporterEmail,
      String updateToken,
      List<String> imageUrls,
      List<?> supportingDocs) {

    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping new issue email. issueId={}", issueId);
      return;
    }

    if (departmentEmail == null || departmentEmail.isBlank()) {
      log.warn("Department email missing. Skipping new issue email. issueId={}", issueId);
      return;
    }

    String subject = String.format("[%s] New Issue Report: %s", category, title);
    String body = buildNewIssueEmailBody(issueId, title, category, description, location, priority, createdAt,
        reporterName, reporterEmail, updateToken, imageUrls, supportingDocs);

    // Use issue mail sender with Reply-To set to the student's email
    sendIssueMailWithReplyTo(departmentEmail, reporterEmail, subject, body);
    log.info("New issue email sent to {} for issueId={}", departmentEmail, issueId);
  }

  /**
   * Send email to student when issue status changes
   */
  public void sendIssueStatusUpdateEmail(
      String studentEmail,
      String studentName,
      String issueId,
      String title,
      String oldStatus,
      String newStatus,
      String note) {

    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping status update email. issueId={}", issueId);
      return;
    }

    if (studentEmail == null || studentEmail.isBlank()) {
      log.warn("Student email missing. Skipping status update email. issueId={}", issueId);
      return;
    }

    String subject = String.format("Issue #%s Status Update: %s", issueId, title);
    String body = buildIssueStatusUpdateBody(issueId, title, oldStatus, newStatus, note, studentName);

    sendStudentMail(studentEmail, subject, body);
    log.info("Status update email sent to {} for issueId={}", studentEmail, issueId);
  }

  /**
   * Send email to department when student adds a comment
   */
  public void sendIssueCommentEmail(
      String departmentEmail,
      String issueId,
      String title,
      String comment,
      String commenterName,
      String commenterEmail) {

    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping comment email. issueId={}", issueId);
      return;
    }

    if (departmentEmail == null || departmentEmail.isBlank()) {
      log.warn("Department email missing. Skipping comment email. issueId={}", issueId);
      return;
    }

    String subject = String.format("[%s] Student Comment on Issue #%s", commenterName, issueId);
    String body = buildIssueCommentBody(issueId, title, comment, commenterName, commenterEmail);

    sendIssueMailWithReplyTo(departmentEmail, commenterEmail, subject, body);
    log.info("Comment email sent to {} for issueId={}", departmentEmail, issueId);
  }

  /**
   * Send email notification when an issue is updated
   */
  public void sendIssueUpdateEmail(
      String oldCategory,
      String newCategory,
      String issueId,
      String title,
      String description,
      String location,
      String updatedBy,
      String studentEmail,
      String studentName,
      List<String> imageUrls,
      List<?> supportingDocs,
      String existingToken) {

    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping issue update email. issueId={}", issueId);
      return;
    }

    String frontendBaseUrl = frontendUrl != null ? frontendUrl : "http://localhost:5173";
    String studentViewLink = frontendBaseUrl + "/issues/" + issueId;

    boolean categoryChanged = oldCategory != null && !oldCategory.equals(newCategory);

    // 1. Always notify student (reporter)
    if (studentEmail != null && !studentEmail.isBlank()) {
      String studentSubject = "Update on Your Issue Report: " + title;
      String studentBody = buildStudentUpdateEmailBody(issueId, title, description, location, updatedBy,
          studentViewLink);
      sendStudentMail(studentEmail, studentSubject, studentBody);
      log.info("Update email sent to student: {} for issueId={}", studentEmail, issueId);
    }

    // 2. Handle based on whether category changed or not
    if (categoryChanged) {
      // Category CHANGED - New department gets NEW ISSUE EMAIL
      String newDepartmentEmail = getDepartmentEmail(newCategory);
      if (newDepartmentEmail != null && !newDepartmentEmail.isBlank()) {
        String tokenForLink = existingToken;
        
        String newDeptSubject = String.format("[%s] New Issue Report: %s", newCategory, title);
        String newDeptBody = buildNewDepartmentEmailBody(
            issueId, title, newCategory, description, location,
            updatedBy, studentName, studentEmail, tokenForLink, imageUrls, supportingDocs);
        
        sendIssueMailWithReplyTo(newDepartmentEmail, studentEmail, newDeptSubject, newDeptBody);
        log.info("NEW ISSUE EMAIL sent to new department: {} with token: {} for issueId={}", newDepartmentEmail, tokenForLink, issueId);
      }

      // Category CHANGED - Old department gets TRANSFER NOTIFICATION
      String oldDepartmentEmail = getDepartmentEmail(oldCategory);
      String newDepartmentEmailForCheck = getDepartmentEmail(newCategory);
      if (oldDepartmentEmail != null && !oldDepartmentEmail.isBlank() &&
          !oldDepartmentEmail.equals(newDepartmentEmailForCheck)) {
        String oldDeptSubject = String.format("[TRANSFERRED] Issue #%s: %s", issueId, title);
        String oldDeptBody = buildOldDepartmentTransferEmailBody(
            issueId, title, description, location, updatedBy, oldCategory, newCategory, studentName);
        sendIssueMail(oldDepartmentEmail, oldDeptSubject, oldDeptBody);
        log.info("TRANSFER NOTIFICATION sent to old department: {} for issueId={}", oldDepartmentEmail, issueId);
      }
    } else {
      // Category SAME - Send UPDATE email to current department with token link
      String currentDepartmentEmail = getDepartmentEmail(newCategory);
      if (currentDepartmentEmail != null && !currentDepartmentEmail.isBlank()) {
        String departmentViewLink = frontendBaseUrl + "/public/issue/" + issueId + "?token=" + existingToken;

        String currentDeptSubject = String.format("[UPDATE] Issue #%s: %s", issueId, title);
        String currentDeptBody = buildDepartmentUpdateEmailBody(
            issueId, title, description, location, updatedBy, newCategory, departmentViewLink, imageUrls,
            supportingDocs);
        
        sendIssueMailWithReplyTo(currentDepartmentEmail, studentEmail, currentDeptSubject, currentDeptBody);
        log.info("UPDATE EMAIL sent to current department: {} for issueId={}", currentDepartmentEmail, issueId);
      }
    }
  }

  // ========== MAIL SENDER METHODS ==========

  public void sendStudentMail(String to, String subject, String text) {
    sendPlainText(studentMailSender, studentFromAddress, to, subject, text, "student");
  }

  public void sendTutorMail(String to, String subject, String text) {
    sendPlainText(tutorMailSender, tutorFromAddress, to, subject, text, "tutor");
  }

  public void sendIssueMail(String to, String subject, String text) {
    sendPlainText(issueMailSender, issueFromAddress, to, subject, text, "issue");
  }

  public void sendIssueMailWithReplyTo(String to, String replyTo, String subject, String text) {
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping ISSUE email to {}", to);
      return;
    }
    if (!StringUtils.hasText(to)) {
      log.warn("Skipping ISSUE email. Recipient is blank. subject='{}'", subject);
      return;
    }
    if (issueMailSender == null) {
      log.warn("ISSUE JavaMailSender is not configured. Cannot send email to {} with subject '{}'", to, subject);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(issueFromAddress);
    message.setTo(to);
    if (StringUtils.hasText(replyTo)) {
      message.setReplyTo(replyTo);
    }
    message.setSubject(subject);
    message.setText(text);
    issueMailSender.send(message);
    log.info("ISSUE email sent to {} with reply-to {} and subject '{}'", to, replyTo, subject);
  }

  private void sendPlainText(
      JavaMailSender sender,
      String fromAddress,
      String to,
      String subject,
      String body,
      String accountType) {
    if (!mailEnabled) {
      log.info("Mail sending is disabled. Skipping {} email to {}", accountType, to);
      return;
    }
    if (!StringUtils.hasText(to)) {
      log.warn("Skipping {} email. Recipient is blank. subject='{}'", accountType, subject);
      return;
    }
    if (sender == null) {
      log.warn("{} JavaMailSender is not configured. Cannot send email to {} with subject '{}'", accountType, to, subject);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
    log.info("{} email sent to {} with subject '{}'", accountType, to, subject);
  }

  // ========== HELPER METHODS FOR TUTORING ==========

  private String buildTutorRequestTemplate(TutorRequest request) {
    return "Hello " + safe(request.getTutorName()) + ",\n\n"
        + "You have received a new tutor request in Academic Support Portal.\n\n"
        + "----------- Request Details -----------\n"
        + "Student Name : " + safe(request.getStudentName()) + "\n"
        + "Student Email: " + safe(request.getStudentEmail()) + "\n"
        + "Subject      : " + safe(request.getSubject()) + "\n"
        + "Preferred Day: " + safe(request.getPreferredDay()) + "\n"
        + "From         : " + safe(request.getPreferredTimeFrom()) + "\n"
        + "To           : " + safe(request.getPreferredTimeTo()) + "\n"
        + "Message      : " + safe(request.getMessage()) + "\n"
        + "---------------------------------------\n\n"
        + "Open and respond from your dashboard:\n"
        + frontendUrl + "/support/requests\n"
        + "If the link does not open, login and go to Support > My Requests.\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
  }

  private String buildSessionConfirmationTemplate(TutorRequest request) {
    return "Hello " + safe(request.getStudentName()) + ",\n\n"
        + "Your tutoring session has been confirmed.\n\n"
        + "-------- Confirmed Session Details --------\n"
        + "Tutor Name   : " + safe(request.getTutorName()) + "\n"
        + "Subject      : " + safe(request.getSubject()) + "\n"
        + "Date/Time    : " + safe(request.getSessionDateTime()) + "\n"
        + "Join Link    : " + safe(request.getJoinLink()) + "\n"
        + "-------------------------------------------\n\n"
        + "Please join the session on time.\n\n"
        + "Regards,\n"
        + "Academic Support Portal";
  }

  // ========== HELPER METHODS FOR ISSUE REPORTER ==========

  private String buildNewIssueEmailBody(
      String issueId,
      String title,
      String category,
      String description,
      String location,
      String priority,
      String createdAt,
      String reporterName,
      String reporterEmail,
      String updateToken,
      List<String> imageUrls,
      List<?> supportingDocs) {

    StringBuilder body = new StringBuilder();
    body.append("A new campus issue has been reported that requires your attention.\n\n");
    body.append("========================================\n");
    body.append("ISSUE DETAILS\n");
    body.append("========================================\n");
    body.append("Report ID    : ").append(issueId).append("\n");
    body.append("Category     : ").append(safe(category)).append("\n");
    body.append("Priority     : ").append(safe(priority)).append("\n");
    body.append("Reported By  : ").append(safe(reporterName)).append("\n");
    body.append("Student Email: ").append(safe(reporterEmail)).append("\n");
    body.append("Reported At  : ").append(safe(createdAt)).append("\n");
    body.append("Title        : ").append(safe(title)).append("\n");
    body.append("Location     : ").append(safe(location)).append("\n");
    body.append("\nDescription:\n").append(safe(description)).append("\n");

    // Add images if any
    if (imageUrls != null && !imageUrls.isEmpty()) {
      body.append("\n========================================\n");
      body.append("Images included\n");
      body.append("========================================\n");
      body.append("📷 ").append(imageUrls.size()).append(" image(s) attached to this report.\n");
      body.append("(Click the link below to view them in the portal)\n");
      // for (int i = 0; i < imageUrls.size(); i++) {
      //   body.append("Image ").append(i + 1).append(": ").append(imageUrls.get(i)).append("\n");
      // }
    }

    // Add documents if any
    if (supportingDocs != null && !supportingDocs.isEmpty()) {
      body.append("\n========================================\n");
      body.append("Supporting documents included\n");
      body.append("========================================\n");
      body.append("📄 ").append(supportingDocs.size()).append(" document(s) attached to this report.\n");
      body.append("(Click the link below to view them in the portal)\n");
    
      // for (Object doc : supportingDocs) {
      //   try {
      //     java.lang.reflect.Method getName = doc.getClass().getMethod("getName");
      //     String docName = (String) getName.invoke(doc);
      //     body.append("Document: ").append(safe(docName)).append("\n");
      //   } catch (Exception e) {
      //     body.append("Document available\n");
      //   }
      // }
    }

    String frontendBaseUrl = frontendUrl != null ? frontendUrl : "http://localhost:5173";
    body.append("\n========================================\n");
    body.append("QUICK ACTIONS\n");
    body.append("========================================\n");
    body.append("\nView Full Report:\n");
    body.append(frontendBaseUrl).append("/public/issue/").append(issueId)
        .append("?token=").append(updateToken).append("\n\n");
    body.append("\n========================================\n");
    body.append("REPLY TO THIS EMAIL\n");
    body.append("========================================\n");
    body.append("To contact the student directly, simply reply to this email.\n");
    body.append("Your reply will go to: ").append(safe(reporterEmail)).append("\n\n");
    body.append("========================================\n");
    body.append("This is an automated message from the Campus Issue Reporter.\n");
    body.append("Click the link above to view and update the issue status.\n");
    body.append("========================================\n");

    return body.toString();
  }

  private String buildIssueStatusUpdateBody(
      String issueId,
      String title,
      String oldStatus,
      String newStatus,
      String note,
      String studentName) {

    StringBuilder body = new StringBuilder();
    body.append("Hello ").append(safe(studentName)).append(",\n\n");
    body.append("Your reported issue has been updated.\n\n");
    body.append("========================================\n");
    body.append("ISSUE UPDATE\n");
    body.append("========================================\n");
    body.append("Issue ID     : ").append(issueId).append("\n");
    body.append("Title        : ").append(safe(title)).append("\n");
    body.append("Old Status   : ").append(safe(oldStatus)).append("\n");
    body.append("New Status   : ").append(safe(newStatus)).append("\n");

    if (note != null && !note.trim().isEmpty()) {
      body.append("\nNote from department:\n").append(safe(note)).append("\n");
    }

    body.append("\nView your issue:\n");
    body.append(frontendUrl).append("/issues/").append(issueId).append("\n\n");
    body.append("========================================\n");
    body.append("Regards,\n");
    body.append("Campus Issue Reporter\n");

    return body.toString();
  }

  private String buildIssueCommentBody(
      String issueId,
      String title,
      String comment,
      String commenterName,
      String commenterEmail) {

    StringBuilder body = new StringBuilder();
    body.append("A new comment has been added to an issue.\n\n");
    body.append("========================================\n");
    body.append("COMMENT DETAILS\n");
    body.append("========================================\n");
    body.append("Issue ID      : ").append(issueId).append("\n");
    body.append("Title         : ").append(safe(title)).append("\n");
    body.append("Commented by  : ").append(safe(commenterName)).append("\n");
    body.append("Student Email : ").append(safe(commenterEmail)).append("\n");
    body.append("\nComment:\n").append(safe(comment)).append("\n\n");
    body.append("View the issue:\n");
    body.append(frontendUrl).append("/issues/").append(issueId).append("\n\n");
    body.append("\n========================================\n");
    body.append("REPLY TO THIS EMAIL\n");
    body.append("========================================\n");
    body.append("To reply to the student, simply reply to this email.\n");
    body.append("========================================\n");
    body.append("Regards,\n");
    body.append("Campus Issue Reporter\n");

    return body.toString();
  }

  private String buildStudentUpdateEmailBody(
      String issueId,
      String title,
      String description,
      String location,
      String updatedBy,
      String viewLink) {

    StringBuilder body = new StringBuilder();
    body.append("Hello,\n\n");
    body.append("Your reported issue has been updated by ").append(safe(updatedBy)).append(".\n\n");
    body.append("========================================\n");
    body.append("ISSUE UPDATE\n");
    body.append("========================================\n");
    body.append("Issue ID    : ").append(issueId).append("\n");
    body.append("Title       : ").append(safe(title)).append("\n");
    body.append("Location    : ").append(safe(location)).append("\n");
    body.append("Description : ").append(safe(description)).append("\n\n");
    body.append("Status      : Please check the portal for current status\n\n");
    body.append("========================================\n");
    body.append("Regards,\n");
    body.append("Campus Issue Reporter\n");

    return body.toString();
  }

  private String buildDepartmentUpdateEmailBody(
      String issueId,
      String title,
      String description,
      String location,
      String updatedBy,
      String category,
      String viewLink,
      List<String> imageUrls,
      List<?> supportingDocs) {

    StringBuilder body = new StringBuilder();
    body.append("An existing issue has been updated.\n\n");
    body.append("========================================\n");
    body.append("ISSUE DETAILS\n");
    body.append("========================================\n");
    body.append("Issue ID    : ").append(issueId).append("\n");
    body.append("Category    : ").append(safe(category)).append("\n");
    body.append("Title       : ").append(safe(title)).append("\n");
    body.append("Location    : ").append(safe(location)).append("\n");
    body.append("Updated By  : ").append(safe(updatedBy)).append("\n");
    body.append("\nDescription:\n").append(safe(description)).append("\n");

    body.append("\n========================================\n");
    body.append("VIEW & UPDATE THIS ISSUE\n");
    body.append("========================================\n");
    body.append("Click the link below to view full details and update the status:\n");
    body.append(viewLink).append("\n\n");
    body.append("========================================\n");
    body.append("This is an automated message from the Campus Issue Reporter.\n");

    return body.toString();
  }

  private String buildNewDepartmentEmailBody(
      String issueId,
      String title,
      String category,
      String description,
      String location,
      String updatedBy,
      String reporterName,
      String reporterEmail,
      String updateToken,
      List<String> imageUrls,
      List<?> supportingDocs) {
    
    StringBuilder body = new StringBuilder();
    body.append("A campus issue has been transferred to your department for handling.\n\n");
    body.append("========================================\n");
    body.append("ISSUE DETAILS\n");
    body.append("========================================\n");
    body.append("Report ID     : ").append(issueId).append("\n");
    body.append("Category      : ").append(safe(category)).append("\n");
    body.append("Reported By   : ").append(safe(reporterName)).append("\n");
    body.append("Student Email : ").append(safe(reporterEmail)).append("\n");
    body.append("Title         : ").append(safe(title)).append("\n");
    body.append("Location      : ").append(safe(location)).append("\n");
    body.append("Transferred By: ").append(safe(updatedBy)).append("\n");
    body.append("\nDescription:\n").append(safe(description)).append("\n");
    
    // Add images if any
    if (imageUrls != null && !imageUrls.isEmpty()) {
        body.append("\n========================================\n");
        body.append("Images included\n");
        body.append("========================================\n");
        body.append("📷 ").append(imageUrls.size()).append(" image(s) attached to this report.\n");
      body.append("(Click the link below to view them in the portal)\n");
        // for (int i = 0; i < imageUrls.size(); i++) {
        //     body.append("Image ").append(i + 1).append(": ").append(imageUrls.get(i)).append("\n");
        // }
    }
    
    // Add documents if any
    if (supportingDocs != null && !supportingDocs.isEmpty()) {
        body.append("\n========================================\n");
        body.append("Supporting documents included\n");
        body.append("========================================\n");
        body.append("📄 ").append(supportingDocs.size()).append(" document(s) attached to this report.\n");
      body.append("(Click the link below to view them in the portal)\n");
    
        // for (Object doc : supportingDocs) {
        //     try {
        //         java.lang.reflect.Method getName = doc.getClass().getMethod("getName");
        //         String docName = (String) getName.invoke(doc);
        //         body.append("Document: ").append(safe(docName)).append("\n");
        //     } catch (Exception e) {
        //         body.append("Document available\n");
        //     }
        // }
    }
    
    String frontendBaseUrl = frontendUrl != null ? frontendUrl : "http://localhost:5173";
    body.append("\n========================================\n");
    body.append("QUICK ACTIONS\n");
    body.append("========================================\n");
    body.append("\nView Full Report & Update Status:\n");
    body.append(frontendBaseUrl).append("/public/issue/").append(issueId)
        .append("?token=").append(updateToken).append("\n\n");
    body.append("\n========================================\n");
    body.append("REPLY TO THIS EMAIL\n");
    body.append("========================================\n");
    body.append("To contact the student directly, simply reply to this email.\n");
    body.append("Your reply will go to: ").append(safe(reporterEmail)).append("\n\n");
    body.append("========================================\n");
    body.append("Please review this issue and update its status using the link above.\n");
    body.append("This is an automated message from the Campus Issue Reporter.\n");
    body.append("========================================\n");
    
    return body.toString();
  }

  private String buildOldDepartmentTransferEmailBody(
      String issueId,
      String title,
      String description,
      String location,
      String updatedBy,
      String oldCategory,
      String newCategory,
      String reporterName) {

    StringBuilder body = new StringBuilder();
    body.append("An issue previously assigned to your department has been transferred.\n\n");
    body.append("========================================\n");
    body.append("TRANSFER NOTIFICATION\n");
    body.append("========================================\n");
    body.append("Report ID    : ").append(issueId).append("\n");
    body.append("Reported By  : ").append(safe(reporterName)).append("\n");
    body.append("Title        : ").append(safe(title)).append("\n");
    body.append("Location     : ").append(safe(location)).append("\n");
    body.append("Old Category : ").append(safe(oldCategory)).append("\n");
    body.append("New Category : ").append(safe(newCategory)).append("\n");
    body.append("Transferred By: ").append(safe(updatedBy)).append("\n");
    body.append("\nBrief Description:\n")
        .append(safe(description.length() > 200 ? description.substring(0, 200) + "..." : description)).append("\n");
    body.append("\n========================================\n");
    body.append("This issue is no longer under your department's responsibility.\n");
    body.append("No further action is required from your department.\n");
    body.append("========================================\n");
    body.append("Regards,\n");
    body.append("Campus Issue Reporter\n");

    return body.toString();
  }

  private String getDepartmentEmail(String category) {
    if (category == null)
      return null;

    Map<String, String> departmentEmails = new HashMap<>();
    departmentEmails.put("FACILITIES", "kkdsashani@gmail.com");
    departmentEmails.put("IT_SERVICES", "kkdsashani@gmail.com");
    departmentEmails.put("SECURITY", "kkdsashani@gmail.com");
    departmentEmails.put("ACADEMIC", "kkdsashani@gmail.com");
    departmentEmails.put("OTHER", "kkdsashani@gmail.com");

    return departmentEmails.get(category);
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private String safeEmail(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}