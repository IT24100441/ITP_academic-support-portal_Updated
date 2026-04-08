package com.example.academic_support_portal.dashboard.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EnvironmentSnapshotDto {
  Double averageTemperature;
  Integer totalOccupancy;
}
