import { gql } from 'apollo-angular';

export const DASHBOARD_STATS_SUBSCRIPTION = gql`
  subscription DashboardStats {
    dashboardStats {
      totalUsers
      activeSessions
      totalAuditLogs
      systemHealth
      loginAttemptsToday
      failedLoginAttempts
      totalLoginAttempts
      loginAttemptTrends {
        date
        successCount
        failedCount
      }
    }
  }
`;