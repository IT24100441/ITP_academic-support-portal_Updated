import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlertTriangle,
  BookOpen,
  Brain,
  CalendarClock,
  ChevronRight,
  Clock3,
  Compass,
  Flame,
  Leaf,
  Loader2,
  MapPin,
  RefreshCw,
  Sparkles,
  Users,
  Wrench
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { dashboardApi } from "../api/dashboardApi";

const numberFormat = new Intl.NumberFormat("en-US");

const cardTone = {
  blue: "from-blue-500 to-cyan-500",
  indigo: "from-indigo-500 to-violet-500",
  amber: "from-amber-500 to-orange-500",
  emerald: "from-emerald-500 to-teal-500"
};

const activityIconByType = {
  BOOKING: CalendarClock,
  ISSUE: AlertTriangle,
  TUTOR_REQUEST: Brain,
  CIRCLE_JOIN: Users,
  RESOURCE: BookOpen,
  EQUIPMENT: Wrench,
  ENVIRONMENT: Leaf
};

const SummaryCard = ({ icon: Icon, label, value, tone, hint, onClick }) => (
  <button
    onClick={onClick}
    className="group relative overflow-hidden rounded-2xl border border-slate-100 bg-white p-6 text-left shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg"
  >
    <div className={`absolute -right-8 -top-8 h-24 w-24 rounded-full bg-gradient-to-br ${tone} opacity-15 transition-opacity group-hover:opacity-25`} />
    <div className="relative flex items-start justify-between gap-4">
      <div>
        <p className="text-sm font-semibold text-slate-500">{label}</p>
        <p className="mt-2 text-3xl font-black text-slate-900">{numberFormat.format(value || 0)}</p>
        <p className="mt-2 text-xs font-semibold text-slate-500">{hint}</p>
      </div>
      <div className={`rounded-xl bg-gradient-to-br ${tone} p-3 text-white shadow-md`}>
        <Icon size={20} />
      </div>
    </div>
  </button>
);

const EmptyState = ({ message }) => (
  <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-500">
    {message}
  </div>
);

const extractApiError = (err) => {
  const status = err?.response?.status;
  const payload = err?.response?.data;
  if (typeof payload === "string" && payload.trim()) {
    return status ? `${payload} (HTTP ${status})` : payload;
  }
  if (payload?.message) {
    return status ? `${payload.message} (HTTP ${status})` : payload.message;
  }
  if (payload?.error) {
    return status ? `${payload.error} (HTTP ${status})` : payload.error;
  }
  if (status) {
    return `Failed to load dashboard data (HTTP ${status}).`;
  }
  return "Failed to load dashboard data.";
};

const Dashboard = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const fetchDashboard = async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    try {
      const data = await dashboardApi.getStudentDashboard();
      setDashboard(data || null);
      setError("");
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchDashboard(false);
  }, []);

  const quickActions = useMemo(
    () => [
      { label: "Book Study Spot", icon: MapPin, route: "/study-spots" },
      { label: "Report an Issue", icon: AlertTriangle, route: "/issues" },
      { label: "Browse Equipment", icon: Wrench, route: "/equipment" },
      { label: "View Environment", icon: Compass, route: "/environment" },
      { label: "Find Tutor", icon: Brain, route: "/support/tutors" },
      { label: "Join Circle", icon: Users, route: "/support/groups" }
    ],
    []
  );

  const greetingName = dashboard?.studentName || user?.name || "Scholar";
  const todayLabel =
    dashboard?.todayLabel ||
    new Date().toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="h-28 animate-pulse rounded-3xl bg-slate-100" />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          {[1, 2, 3, 4].map((key) => <div key={key} className="h-36 animate-pulse rounded-2xl bg-slate-100" />)}
        </div>
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
          <div className="h-96 animate-pulse rounded-2xl bg-slate-100 xl:col-span-2" />
          <div className="h-96 animate-pulse rounded-2xl bg-slate-100" />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <section className="relative overflow-hidden rounded-3xl border border-slate-100 bg-white p-7 shadow-sm">
        <div className="absolute -right-8 -top-8 h-28 w-28 rounded-full bg-primary/10" />
        <div className="absolute -left-6 -bottom-10 h-24 w-24 rounded-full bg-indigo-100/60" />
        <div className="relative flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black uppercase tracking-widest text-primary">Student Dashboard</p>
            <h1 className="mt-2 text-3xl font-black text-slate-900">Hello, {greetingName}</h1>
            <p className="mt-2 text-sm font-medium text-slate-500">
              {todayLabel} | Here's what's happening on campus today.
            </p>
          </div>
          <button
            onClick={() => fetchDashboard(true)}
            disabled={refreshing}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-black uppercase tracking-widest text-slate-600 transition hover:bg-slate-50 disabled:opacity-60"
          >
            <RefreshCw size={14} className={refreshing ? "animate-spin" : ""} />
            Refresh
          </button>
        </div>
      </section>

      {error && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 px-5 py-4 text-sm font-semibold text-rose-700">
          {error}
        </div>
      )}

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          icon={MapPin}
          label="Available Study Spots"
          value={dashboard?.availableStudySpots}
          hint="Open now across study rooms"
          tone={cardTone.blue}
          onClick={() => navigate("/study-spots")}
        />
        <SummaryCard
          icon={Users}
          label="Active Study Groups"
          value={dashboard?.activeStudyGroups}
          hint="Explore active circles"
          tone={cardTone.indigo}
          onClick={() => navigate("/support/groups")}
        />
        <SummaryCard
          icon={AlertTriangle}
          label="Recent Issues"
          value={dashboard?.recentIssues}
          hint="Open or in-progress incidents"
          tone={cardTone.amber}
          onClick={() => navigate("/issues")}
        />
        <SummaryCard
          icon={Sparkles}
          label="My Active Bookings"
          value={dashboard?.myActiveBookings ?? dashboard?.myBookingsCount}
          hint="Your study room reservations"
          tone={cardTone.emerald}
          onClick={() => navigate("/study-spots")}
        />
      </section>

      <section className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm xl:col-span-2">
          <div className="mb-5 flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-lg font-black text-slate-900">
              <Clock3 size={18} className="text-primary" />
              Recent Activity
            </h2>
            <button
              onClick={() => navigate("/support/requests")}
              className="text-xs font-black uppercase tracking-widest text-primary hover:underline"
            >
              View All
            </button>
          </div>

          <div className="space-y-3">
            {(dashboard?.recentActivities || []).length === 0 && (
              <EmptyState message="No recent activity yet. Your latest actions will appear here." />
            )}
            {(dashboard?.recentActivities || []).map((activity, index) => {
              const Icon = activityIconByType[activity.type] || Clock3;
              return (
                <button
                  key={`${activity.type}-${index}`}
                  onClick={() => navigate(activity.route || "/dashboard")}
                  className="group flex w-full items-start gap-3 rounded-xl border border-slate-100 bg-slate-50/70 p-3 text-left transition hover:border-primary/20 hover:bg-primary/5"
                >
                  <div className="rounded-xl bg-white p-2 text-slate-500 transition group-hover:text-primary">
                    <Icon size={16} />
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-bold text-slate-800">{activity.title}</p>
                    <p className="mt-1 text-xs font-medium text-slate-500">
                      {activity.timeAgo || "Recently"} {activity.subtitle ? `| ${activity.subtitle}` : ""}
                    </p>
                  </div>
                  <ChevronRight size={16} className="mt-1 text-slate-300 group-hover:text-primary" />
                </button>
              );
            })}
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-lg font-black text-slate-900">
                <Brain size={18} className="text-indigo-500" />
                Recommended Groups
              </h2>
              <button
                onClick={() => navigate("/support/groups")}
                className="text-xs font-black uppercase tracking-widest text-primary hover:underline"
              >
                Explore
              </button>
            </div>

            <div className="space-y-3">
              {(dashboard?.recommendedGroups || []).length === 0 && (
                <EmptyState message="No group recommendations right now. Check back later." />
              )}
              {(dashboard?.recommendedGroups || []).map((group) => (
                <button
                  key={group.id}
                  onClick={() => navigate(group.route || "/support/groups")}
                  className="w-full rounded-xl border border-slate-100 bg-slate-50 p-4 text-left transition hover:border-primary/20 hover:bg-primary/5"
                >
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="text-sm font-black text-slate-800">{group.title}</h3>
                    <span className="rounded-full border border-slate-200 bg-white px-2 py-1 text-[10px] font-black uppercase tracking-widest text-slate-500">
                      {numberFormat.format(group.memberCount || 0)} Members
                    </span>
                  </div>
                  <p className="mt-2 text-xs font-medium text-slate-500">{group.subtitle || "Active circle"}</p>
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-black text-slate-900">Quick Stats</h2>
            <div className="space-y-2 text-sm">
              <div className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2">
                <span className="font-semibold text-slate-600">My Issues</span>
                <span className="font-black text-slate-900">{dashboard?.quickStats?.myIssues ?? 0}</span>
              </div>
              <div className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2">
                <span className="font-semibold text-slate-600">Joined Circles</span>
                <span className="font-black text-slate-900">{dashboard?.quickStats?.myJoinedCircles ?? 0}</span>
              </div>
              <div className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2">
                <span className="font-semibold text-slate-600">Tutor Requests</span>
                <span className="font-black text-slate-900">{dashboard?.myTutorRequestsCount ?? 0}</span>
              </div>
              <div className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2">
                <span className="font-semibold text-slate-600">Equipment Alerts</span>
                <span className="font-black text-slate-900">{dashboard?.quickStats?.equipmentAlerts ?? 0}</span>
              </div>
            </div>
            <div className="mt-4 rounded-xl border border-emerald-100 bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700">
              Environment: {dashboard?.environmentSnapshot?.averageTemperature ?? 0} C |{" "}
              {dashboard?.environmentSnapshot?.totalOccupancy ?? 0} occupants
            </div>
          </div>
        </div>
      </section>

      <section className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
        <div className="mb-4 flex items-center gap-2">
          <Flame size={18} className="text-primary" />
          <h2 className="text-lg font-black text-slate-900">Quick Actions</h2>
        </div>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
          {quickActions.map((action) => (
            <button
              key={action.label}
              onClick={() => navigate(action.route)}
              className="group rounded-xl border border-slate-200 bg-white p-4 text-left transition hover:border-primary/30 hover:bg-primary/5"
            >
              <action.icon size={18} className="text-slate-500 transition group-hover:text-primary" />
              <p className="mt-2 text-xs font-black uppercase tracking-widest text-slate-700">{action.label}</p>
            </button>
          ))}
        </div>
      </section>

      {refreshing && (
        <div className="fixed bottom-6 right-6 inline-flex items-center gap-2 rounded-xl bg-slate-900 px-3 py-2 text-xs font-bold text-white shadow-lg">
          <Loader2 size={14} className="animate-spin" />
          Refreshing dashboard
        </div>
      )}
    </div>
  );
};

export default Dashboard;
