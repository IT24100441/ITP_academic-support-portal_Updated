package com.example.academic_support_portal.dashboard.controller;

import com.example.academic_support_portal.dashboard.dto.EnvironmentDashboardResponse;
import com.example.academic_support_portal.dashboard.dto.StudentDashboardResponse;
import com.example.academic_support_portal.dashboard.service.StudentDashboardService;
import com.example.academic_support_portal.iot.service.IotService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final IotService iotService;
  private final StudentDashboardService studentDashboardService;

  @GetMapping("/environment")
  public EnvironmentDashboardResponse getEnvironment() {
    return iotService.getEnvironmentDashboard();
  }

  @GetMapping("/student")
  @PreAuthorize("isAuthenticated()")
  public StudentDashboardResponse getStudentDashboard(Authentication authentication) {
    return studentDashboardService.getStudentDashboard(authentication.getName());
  }
}
