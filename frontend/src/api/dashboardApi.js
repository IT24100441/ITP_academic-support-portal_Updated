import api from "../services/api";
import { environmentApi } from "./environmentApi";
import { issueApi } from "./issueApi";
import { studySpotApi } from "./studySpotApi";
import { studyGroupApi, tutorRequestApi } from "./supportApi";

const getArray = (value) => (Array.isArray(value) ? value : []);

const getFromListLike = (value) => {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.content)) return value.content;
  if (Array.isArray(value?.items)) return value.items;
  if (Array.isArray(value?.data)) return value.data;
  return [];
};

const toRecentActivities = ({ bookings, issues, tutorRequests, circles }) => {
  const activities = [];

  getArray(bookings).slice(0, 3).forEach((booking) => {
    activities.push({
      type: "BOOKING",
      title: booking?.roomName ? `Booked ${booking.roomName}` : "New study spot booking",
      subtitle: booking?.startTime || booking?.date || "Study spots",
      timeAgo: booking?.timeAgo || "Recently",
      route: "/study-spots"
    });
  });

  getArray(issues).slice(0, 3).forEach((issue) => {
    activities.push({
      type: "ISSUE",
      title: issue?.title || "Issue reported",
      subtitle: issue?.status || "In progress",
      timeAgo: issue?.timeAgo || "Recently",
      route: "/issues"
    });
  });

  getArray(tutorRequests).slice(0, 2).forEach((request) => {
    activities.push({
      type: "TUTOR_REQUEST",
      title: request?.subject ? `Tutor request: ${request.subject}` : "Tutor request created",
      subtitle: request?.status || "Pending",
      timeAgo: request?.timeAgo || "Recently",
      route: "/support/requests"
    });
  });

  getArray(circles).slice(0, 2).forEach((circle) => {
    activities.push({
      type: "CIRCLE_JOIN",
      title: circle?.name || circle?.title || "Study circle activity",
      subtitle: circle?.subject || "Study circles",
      timeAgo: "Recently",
      route: "/support/groups"
    });
  });

  return activities.slice(0, 8);
};

const buildFallbackDashboard = async () => {
  const [roomsRes, bookingsRes, groupsRes, myGroupsRes, issuesRes, tutorRequestsRes, envRes] = await Promise.allSettled([
    studySpotApi.getRooms({ availableOnly: true }),
    studySpotApi.getMyBookings(),
    studyGroupApi.getAll(),
    studyGroupApi.getMy(),
    issueApi.getAll(),
    tutorRequestApi.getAll(),
    environmentApi.getDashboardEnvironment()
  ]);

  const rooms = roomsRes.status === "fulfilled" ? getFromListLike(roomsRes.value) : [];
  const myBookings = bookingsRes.status === "fulfilled" ? getFromListLike(bookingsRes.value) : [];
  const groups = groupsRes.status === "fulfilled" ? getFromListLike(groupsRes.value) : [];
  const myGroups = myGroupsRes.status === "fulfilled" ? getFromListLike(myGroupsRes.value) : [];
  const issues = issuesRes.status === "fulfilled" ? getFromListLike(issuesRes.value) : [];
  const tutorRequests = tutorRequestsRes.status === "fulfilled" ? getFromListLike(tutorRequestsRes.value) : [];
  const env = envRes.status === "fulfilled" ? envRes.value : {};

  return {
    todayLabel: new Date().toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" }),
    availableStudySpots: rooms.length,
    activeStudyGroups: groups.length,
    recentIssues: issues.length,
    myActiveBookings: myBookings.filter((booking) => {
      const status = String(booking?.status || "").toUpperCase();
      return !["CANCELLED", "COMPLETED"].includes(status);
    }).length,
    myBookingsCount: myBookings.length,
    myTutorRequestsCount: tutorRequests.length,
    recentActivities: toRecentActivities({ bookings: myBookings, issues, tutorRequests, circles: myGroups }),
    recommendedGroups: groups.slice(0, 4).map((group) => ({
      id: group?.id ?? group?.groupId ?? group?.name,
      title: group?.name || group?.title || "Study Circle",
      subtitle: group?.subject || group?.description || "Active circle",
      memberCount: group?.memberCount ?? group?.membersCount ?? 0,
      route: "/support/groups"
    })),
    quickStats: {
      myIssues: issues.length,
      myJoinedCircles: myGroups.length,
      equipmentAlerts: env?.activeAlerts ?? 0
    },
    environmentSnapshot: {
      averageTemperature: env?.avgTemp ?? env?.averageTemperature ?? 0,
      totalOccupancy: env?.activeStudents ?? env?.totalOccupancy ?? 0
    }
  };
};

export const dashboardApi = {
  getStudentDashboard: async () => {
    try {
      const response = await api.get("/dashboard/student");
      return response.data;
    } catch (err) {
      const status = err?.response?.status;
      const shouldFallback = [401, 403, 404, 405].includes(status);
      if (status && !shouldFallback) {
        throw err;
      }
      return buildFallbackDashboard();
    }
  }
};
