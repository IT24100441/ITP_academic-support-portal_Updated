import api from "../services/api";

export const dashboardApi = {
  getStudentDashboard: async () => {
    const res = await api.get("/dashboard/student");
    return res.data;
  }
};
