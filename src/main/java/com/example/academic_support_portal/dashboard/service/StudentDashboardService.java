package com.example.academic_support_portal.dashboard.service;

import com.example.academic_support_portal.dashboard.dto.DashboardActivityDto;
import com.example.academic_support_portal.dashboard.dto.EnvironmentSnapshotDto;
import com.example.academic_support_portal.dashboard.dto.RecommendedGroupDto;
import com.example.academic_support_portal.dashboard.dto.StudentDashboardQuickStatsDto;
import com.example.academic_support_portal.dashboard.dto.StudentDashboardResponse;
import com.example.academic_support_portal.environment.model.Alert;
import com.example.academic_support_portal.environment.repository.AlertRepository;
import com.example.academic_support_portal.equipment.model.EquipmentBooking;
import com.example.academic_support_portal.equipment.repository.EquipmentBookingRepository;
import com.example.academic_support_portal.issue.model.CampusIssue;
import com.example.academic_support_portal.issue.model.IssueStatus;
import com.example.academic_support_portal.issue.repository.IssueRepository;
import com.example.academic_support_portal.resource.repository.AcademicResourceRepository;
import com.example.academic_support_portal.study_circle.model.StudyCircle;
import com.example.academic_support_portal.study_circle.model.StudyCircleMember;
import com.example.academic_support_portal.study_circle.repository.StudyCircleMemberRepository;
import com.example.academic_support_portal.study_circle.repository.StudyCircleRepository;
import com.example.academic_support_portal.study_spot.model.StudyReservation;
import com.example.academic_support_portal.study_spot.model.StudyReservationStatus;
import com.example.academic_support_portal.study_spot.model.StudyRoom;
import com.example.academic_support_portal.study_spot.repository.RoomBookingRepository;
import com.example.academic_support_portal.study_spot.repository.StudyRoomRepository;
import com.example.academic_support_portal.study_spot.service.StudySpotService;
import com.example.academic_support_portal.tutor_request.model.TutorRequest;
import com.example.academic_support_portal.tutor_request.repository.TutorRequestRepository;
import com.example.academic_support_portal.user.model.User;
import com.example.academic_support_portal.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StudentDashboardService {

  private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault();
  private static final DateTimeFormatter TODAY_LABEL_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);

  private final UserRepository userRepository;
  private final StudyRoomRepository studyRoomRepository;
  private final RoomBookingRepository bookingRepository;
  private final IssueRepository issueRepository;
  private final StudyCircleRepository studyCircleRepository;
  private final StudyCircleMemberRepository studyCircleMemberRepository;
  private final TutorRequestRepository tutorRequestRepository;
  private final EquipmentBookingRepository equipmentBookingRepository;
  private final AlertRepository alertRepository;
  private final AcademicResourceRepository academicResourceRepository;

  public StudentDashboardResponse getStudentDashboard(String userEmail) {
    User student = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new NoSuchElementException("Authenticated user not found"));

    List<StudyRoom> rooms = studyRoomRepository.findAll();
    List<StudyReservation> myBookings = bookingRepository
        .findByStudentIdOrderByBookingDateDescStartTimeDesc(student.getId());
    List<CampusIssue> myIssues = issueRepository.findByCreatedByUserId(student.getId());
    List<TutorRequest> myTutorRequests = tutorRequestRepository.findByStudentId(student.getId());
    List<StudyCircleMember> myCircleMemberships = studyCircleMemberRepository
        .findByUserIdOrderByJoinedAtDesc(student.getId());
    List<EquipmentBooking> myEquipmentBookings = equipmentBookingRepository.findByUserId(student.getId());
    List<Alert> myAlerts = alertRepository.findByUserIdAndActive(student.getId(), true);

    long availableStudySpots = rooms.stream()
        .filter(room -> "AVAILABLE".equals(StudySpotService.deriveOccupancyStatus(room)))
        .count();
    long activeStudyGroups = studyCircleRepository.findByIsActiveTrueOrderByCreatedAtDesc().size();
    long recentIssues = issueRepository.findByStatus(IssueStatus.OPEN).size()
        + issueRepository.findByStatus(IssueStatus.IN_PROGRESS).size();
    long myBookingsCount = myBookings.stream()
        .filter(booking -> booking.getStatus() == StudyReservationStatus.BOOKED
            || booking.getStatus() == StudyReservationStatus.ACTIVE)
        .count();
    long myTutorRequestsCount = myTutorRequests.size();
    long myJoinedCircles = myCircleMemberships.stream()
        .map(StudyCircleMember::getCircleId)
        .distinct()
        .count();
    long equipmentAlerts = myEquipmentBookings.stream()
        .filter(booking -> isStatus(booking.getStatus(), "PENDING") || isStatus(booking.getStatus(), "DECLINED"))
        .count();

    return StudentDashboardResponse.builder()
        .studentName(student.getName())
        .todayLabel(LocalDate.now().format(TODAY_LABEL_FORMAT))
        .availableStudySpots(availableStudySpots)
        .activeStudyGroups(activeStudyGroups)
        .recentIssues(recentIssues)
        .myBookingsCount(myBookingsCount)
        .myActiveBookings(myBookingsCount)
        .myTutorRequestsCount(myTutorRequestsCount)
        .recentActivities(buildRecentActivities(student, myBookings, myIssues, myTutorRequests, myCircleMemberships,
            myEquipmentBookings, myAlerts))
        .recommendedGroups(buildRecommendedGroups(student.getId()))
        .quickStats(StudentDashboardQuickStatsDto.builder()
            .myIssues(myIssues.size())
            .myJoinedCircles(myJoinedCircles)
            .myTutorRequests(myTutorRequestsCount)
            .myBookings(myBookingsCount)
            .equipmentAlerts(equipmentAlerts)
            .build())
        .environmentSnapshot(buildEnvironmentSnapshot(rooms))
        .build();
  }

  private List<DashboardActivityDto> buildRecentActivities(
      User student,
      List<StudyReservation> myBookings,
      List<CampusIssue> myIssues,
      List<TutorRequest> myTutorRequests,
      List<StudyCircleMember> myCircleMemberships,
      List<EquipmentBooking> myEquipmentBookings,
      List<Alert> myAlerts) {

    List<ActivityCandidate> activities = new ArrayList<>();

    myBookings.stream()
        .filter(booking -> booking.getCreatedAt() != null)
        .forEach(booking -> activities.add(ActivityCandidate.of(
            booking.getCreatedAt(),
            DashboardActivityDto.builder()
                .type("BOOKING")
                .title("Room \"" + safeText(booking.getRoomName(), "Study room") + "\" booked")
                .subtitle(booking.getBookingDate() + " | " + booking.getStartTime() + " - " + booking.getEndTime())
                .route("/study-spots")
                .build())));

    myIssues.stream()
        .filter(issue -> issue.getCreatedAt() != null)
        .forEach(issue -> activities.add(ActivityCandidate.of(
            issue.getCreatedAt(),
            DashboardActivityDto.builder()
                .type("ISSUE")
                .title("Issue reported: \"" + safeText(issue.getTitle(), "Campus issue") + "\"")
                .subtitle(safeText(issue.getBuilding(), "Campus") + " | " + issue.getStatus())
                .route("/issues")
                .build())));

    myTutorRequests.stream()
        .map(request -> ActivityCandidate.of(
            parseDateTime(request.getCreatedAt()),
            DashboardActivityDto.builder()
                .type("TUTOR_REQUEST")
                .title("Tutor request submitted")
                .subtitle(safeText(request.getSubject(), "General support") + " | " + safeText(request.getStatus(), "PENDING"))
                .route("/support/requests")
                .build()))
        .filter(candidate -> candidate.occurredAt() != null)
        .forEach(activities::add);

    Map<String, StudyCircle> circleById = loadCircleMap(myCircleMemberships.stream()
        .map(StudyCircleMember::getCircleId)
        .collect(Collectors.toSet()));

    myCircleMemberships.stream()
        .filter(member -> member.getJoinedAt() != null)
        .forEach(member -> {
          StudyCircle circle = circleById.get(member.getCircleId());
          activities.add(ActivityCandidate.of(
              member.getJoinedAt(),
              DashboardActivityDto.builder()
                  .type("CIRCLE_JOIN")
                  .title("Joined circle \"" + safeText(circle == null ? null : circle.getTitle(), "Study circle") + "\"")
                  .subtitle(safeText(circle == null ? null : circle.getSubject(), "Academic Support"))
                  .route("/support/groups")
                  .build()));
        });

    academicResourceRepository.findAll().stream()
        .filter(resource -> student.getId().equals(resource.getUploaderId()))
        .map(resource -> ActivityCandidate.of(
            parseDateTime(resource.getUploadedAt()),
            DashboardActivityDto.builder()
                .type("RESOURCE")
                .title("Resource uploaded: \"" + safeText(resource.getTitle(), "Resource") + "\"")
                .subtitle(safeText(resource.getSubject(), "Academic Support"))
                .route("/support/resources")
                .build()))
        .filter(candidate -> candidate.occurredAt() != null)
        .forEach(activities::add);

    myEquipmentBookings.stream()
        .map(booking -> ActivityCandidate.of(
            parseDateTime(booking.getCreatedAt()),
            DashboardActivityDto.builder()
                .type("EQUIPMENT")
                .title("Equipment request submitted")
                .subtitle(safeText(booking.getEquipmentName(), "Equipment") + " | " + safeText(booking.getStatus(), "PENDING"))
                .route("/equipment")
                .build()))
        .filter(candidate -> candidate.occurredAt() != null)
        .forEach(activities::add);

    myAlerts.stream()
        .filter(alert -> alert.getCreatedAt() != null)
        .forEach(alert -> activities.add(ActivityCandidate.of(
            alert.getCreatedAt(),
            DashboardActivityDto.builder()
                .type("ENVIRONMENT")
                .title("Environment alert created")
                .subtitle(alert.getMetric() + " " + alert.getCondition() + " " + alert.getThreshold())
                .route("/environment")
                .build())));

    return activities.stream()
        .sorted(Comparator.comparing(ActivityCandidate::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .limit(8)
        .map(candidate -> DashboardActivityDto.builder()
            .type(candidate.activity().getType())
            .title(candidate.activity().getTitle())
            .subtitle(candidate.activity().getSubtitle())
            .route(candidate.activity().getRoute())
            .timeAgo(toRelativeTime(candidate.occurredAt()))
            .build())
        .toList();
  }

  private List<RecommendedGroupDto> buildRecommendedGroups(String studentId) {
    Set<String> joinedCircleIds = studyCircleMemberRepository.findByUserIdOrderByJoinedAtDesc(studentId).stream()
        .map(StudyCircleMember::getCircleId)
        .collect(Collectors.toSet());

    return studyCircleRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
        .filter(circle -> !joinedCircleIds.contains(circle.getId()))
        .limit(4)
        .map(circle -> RecommendedGroupDto.builder()
            .id(circle.getId())
            .title(circle.getTitle())
            .subtitle(buildCircleSubtitle(circle))
            .memberCount(studyCircleMemberRepository.countByCircleId(circle.getId()))
            .route("/support/groups")
            .build())
        .toList();
  }

  private EnvironmentSnapshotDto buildEnvironmentSnapshot(List<StudyRoom> rooms) {
    if (rooms.isEmpty()) {
      return EnvironmentSnapshotDto.builder()
          .averageTemperature(0.0)
          .totalOccupancy(0)
          .build();
    }

    double avgTemperature = rooms.stream()
        .map(StudyRoom::getTemperature)
        .filter(value -> value != null)
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);
    int totalOccupancy = rooms.stream()
        .map(StudyRoom::getCurrentOccupancy)
        .filter(value -> value != null)
        .mapToInt(Integer::intValue)
        .sum();

    return EnvironmentSnapshotDto.builder()
        .averageTemperature(Math.round(avgTemperature * 10.0) / 10.0)
        .totalOccupancy(totalOccupancy)
        .build();
  }

  private Map<String, StudyCircle> loadCircleMap(Set<String> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<String, StudyCircle> map = new HashMap<>();
    studyCircleRepository.findAllById(ids).forEach(circle -> map.put(circle.getId(), circle));
    return map;
  }

  private LocalDateTime parseDateTime(String rawValue) {
    if (!StringUtils.hasText(rawValue)) {
      return null;
    }

    try {
      return Instant.parse(rawValue).atZone(LOCAL_ZONE).toLocalDateTime();
    } catch (DateTimeParseException ignored) {
      // Try the next format.
    }

    try {
      return OffsetDateTime.parse(rawValue).atZoneSameInstant(LOCAL_ZONE).toLocalDateTime();
    } catch (DateTimeParseException ignored) {
      // Try the next format.
    }

    try {
      return LocalDateTime.parse(rawValue);
    } catch (DateTimeParseException ignored) {
      // Try the next format.
    }

    try {
      return LocalDate.parse(rawValue).atStartOfDay();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private String toRelativeTime(LocalDateTime occurredAt) {
    if (occurredAt == null) {
      return "Recently";
    }

    Duration diff = Duration.between(occurredAt, LocalDateTime.now());
    long minutes = Math.max(0, diff.toMinutes());
    if (minutes < 1) {
      return "Just now";
    }
    if (minutes < 60) {
      return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
    }

    long hours = diff.toHours();
    if (hours < 24) {
      return hours + (hours == 1 ? " hour ago" : " hours ago");
    }

    long days = diff.toDays();
    if (days < 7) {
      return days + (days == 1 ? " day ago" : " days ago");
    }

    long weeks = days / 7;
    if (weeks < 5) {
      return weeks + (weeks == 1 ? " week ago" : " weeks ago");
    }

    long months = days / 30;
    if (months < 12) {
      return months + (months == 1 ? " month ago" : " months ago");
    }

    long years = days / 365;
    return years + (years == 1 ? " year ago" : " years ago");
  }

  private String buildCircleSubtitle(StudyCircle circle) {
    if (circle == null) {
      return "Active circle";
    }
    if (StringUtils.hasText(circle.getMeetingDay()) && StringUtils.hasText(circle.getMeetingTime())) {
      return circle.getMeetingDay() + " | " + circle.getMeetingTime();
    }
    if (StringUtils.hasText(circle.getMeetingDay())) {
      return circle.getMeetingDay() + " | Schedule updating";
    }
    return "No fixed schedule yet";
  }

  private String safeText(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private boolean isStatus(String value, String expected) {
    if (!StringUtils.hasText(value) || !StringUtils.hasText(expected)) {
      return false;
    }
    return value.trim().equalsIgnoreCase(expected.trim());
  }

  private record ActivityCandidate(LocalDateTime occurredAt, DashboardActivityDto activity) {
    private static ActivityCandidate of(LocalDateTime occurredAt, DashboardActivityDto activity) {
      return new ActivityCandidate(occurredAt, activity);
    }
  }
}


