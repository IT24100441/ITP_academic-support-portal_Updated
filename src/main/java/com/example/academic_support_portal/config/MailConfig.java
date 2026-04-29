package com.example.academic_support_portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

  @Value("${spring.mail.student.host}")
  private String studentHost;

  @Value("${spring.mail.student.port}")
  private int studentPort;

  @Value("${spring.mail.student.username}")
  private String studentUsername;

  @Value("${spring.mail.student.password}")
  private String studentPassword;

  @Value("${spring.mail.student.properties.mail.smtp.auth:true}")
  private boolean studentSmtpAuth;

  @Value("${spring.mail.student.properties.mail.smtp.starttls.enable:true}")
  private boolean studentStartTls;

  @Value("${spring.mail.student.properties.mail.smtp.connectiontimeout:5000}")
  private int studentConnectionTimeout;

  @Value("${spring.mail.student.properties.mail.smtp.timeout:5000}")
  private int studentTimeout;

  @Value("${spring.mail.student.properties.mail.smtp.writetimeout:5000}")
  private int studentWriteTimeout;

  @Value("${spring.mail.tutor.host}")
  private String tutorHost;

  @Value("${spring.mail.tutor.port}")
  private int tutorPort;

  @Value("${spring.mail.tutor.username}")
  private String tutorUsername;

  @Value("${spring.mail.tutor.password}")
  private String tutorPassword;

  @Value("${spring.mail.tutor.properties.mail.smtp.auth:true}")
  private boolean tutorSmtpAuth;

  @Value("${spring.mail.tutor.properties.mail.smtp.starttls.enable:true}")
  private boolean tutorStartTls;

  @Value("${spring.mail.tutor.properties.mail.smtp.connectiontimeout:5000}")
  private int tutorConnectionTimeout;

  @Value("${spring.mail.tutor.properties.mail.smtp.timeout:5000}")
  private int tutorTimeout;

  @Value("${spring.mail.tutor.properties.mail.smtp.writetimeout:5000}")
  private int tutorWriteTimeout;

  @Value("${spring.mail.issue.host}")
  private String issueHost;

  @Value("${spring.mail.issue.port}")
  private int issuePort;

  @Value("${spring.mail.issue.username}")
  private String issueUsername;

  @Value("${spring.mail.issue.password}")
  private String issuePassword;

  @Value("${spring.mail.issue.properties.mail.smtp.auth:true}")
  private boolean issueSmtpAuth;

  @Value("${spring.mail.issue.properties.mail.smtp.starttls.enable:true}")
  private boolean issueStartTls;

  @Value("${spring.mail.issue.properties.mail.smtp.connectiontimeout:5000}")
  private int issueConnectionTimeout;

  @Value("${spring.mail.issue.properties.mail.smtp.timeout:5000}")
  private int issueTimeout;

  @Value("${spring.mail.issue.properties.mail.smtp.writetimeout:5000}")
  private int issueWriteTimeout;

  @Bean(name = "studentMailSender")
  public JavaMailSender studentMailSender() {
    return createMailSender(
        studentHost,
        studentPort,
        studentUsername,
        studentPassword,
        studentSmtpAuth,
        studentStartTls,
        studentConnectionTimeout,
        studentTimeout,
        studentWriteTimeout);
  }

  @Bean(name = "tutorMailSender")
  public JavaMailSender tutorMailSender() {
    return createMailSender(
        tutorHost,
        tutorPort,
        tutorUsername,
        tutorPassword,
        tutorSmtpAuth,
        tutorStartTls,
        tutorConnectionTimeout,
        tutorTimeout,
        tutorWriteTimeout);
  }

  @Bean(name = "issueMailSender")
  public JavaMailSender issueMailSender() {
    return createMailSender(
        issueHost,
        issuePort,
        issueUsername,
        issuePassword,
        issueSmtpAuth,
        issueStartTls,
        issueConnectionTimeout,
        issueTimeout,
        issueWriteTimeout);
  }

  // Shared builder for both accounts to keep the configuration consistent.
  private JavaMailSender createMailSender(
      String host,
      int port,
      String username,
      String password,
      boolean smtpAuth,
      boolean startTls,
      int connectionTimeout,
      int timeout,
      int writeTimeout) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(host);
    mailSender.setPort(port);
    mailSender.setUsername(username);
    mailSender.setPassword(password);

    Properties props = mailSender.getJavaMailProperties();
    props.put("mail.smtp.auth", String.valueOf(smtpAuth));
    props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
    props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
    props.put("mail.smtp.timeout", String.valueOf(timeout));
    props.put("mail.smtp.writetimeout", String.valueOf(writeTimeout));

    return mailSender;
  }
}