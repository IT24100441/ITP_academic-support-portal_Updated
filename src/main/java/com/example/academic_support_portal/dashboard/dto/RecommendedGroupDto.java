package com.example.academic_support_portal.dashboard.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RecommendedGroupDto {
  String id;
  String title;
  String subtitle;
  long memberCount;
  String route;
}
