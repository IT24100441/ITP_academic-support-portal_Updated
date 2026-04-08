package com.example.academic_support_portal.dashboard.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StudentDashboardResponse {
  String studentName;
  String todayLabel;
  long availableStudySpots;
  long activeStudyGroups;
  long recentIssues;
  long myBookingsCount;
  long myActiveBookings;
  long myTutorRequestsCount;
  List<DashboardActivityDto> recentActivities;
  List<RecommendedGroupDto> recommendedGroups;
  StudentDashboardQuickStatsDto quickStats;
  EnvironmentSnapshotDto environmentSnapshot;
}
