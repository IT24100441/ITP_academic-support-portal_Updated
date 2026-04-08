package com.example.academic_support_portal.dashboard.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StudentDashboardQuickStatsDto {
  long myIssues;
  long myJoinedCircles;
  long myTutorRequests;
  long myBookings;
  long equipmentAlerts;
}
