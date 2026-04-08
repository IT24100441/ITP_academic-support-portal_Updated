package com.example.academic_support_portal.dashboard.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardActivityDto {
  String type;
  String title;
  String subtitle;
  String timeAgo;
  String route;
}
