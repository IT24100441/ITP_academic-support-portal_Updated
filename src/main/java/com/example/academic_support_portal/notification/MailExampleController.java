package com.example.academic_support_portal.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug/mail")
@RequiredArgsConstructor
public class MailExampleController {

  private final EmailService emailService;

  // Example endpoint: sends using the student SMTP account.
  @PostMapping("/student")
  public ResponseEntity<Map<String, String>> sendStudentMail(
      @RequestParam String to,
      @RequestParam String subject,
      @RequestParam String text) {
    emailService.sendStudentMail(to, subject, text);
    return ResponseEntity.ok(Map.of("message", "Student email request submitted"));
  }

  // Example endpoint: sends using the tutor SMTP account.
  @PostMapping("/tutor")
  public ResponseEntity<Map<String, String>> sendTutorMail(
      @RequestParam String to,
      @RequestParam String subject,
      @RequestParam String text) {
    emailService.sendTutorMail(to, subject, text);
    return ResponseEntity.ok(Map.of("message", "Tutor email request submitted"));
  }
}