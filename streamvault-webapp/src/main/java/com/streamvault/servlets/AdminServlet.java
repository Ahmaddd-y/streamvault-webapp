package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import com.streamvault.mongo.MongoAnalyticsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.bson.Document;

import java.io.IOException;
import java.sql.*;
import java.util.List;

@WebServlet("/admin")
public class AdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            resp.sendRedirect(req.getContextPath() + "/views/login.html");
            return;
        }

        String role = (String) session.getAttribute("role");

        if (role == null || !role.equals("admin")) {
            resp.sendError(403, "Access denied");
            return;
        }

        resp.setContentType("text/html");
        var out = resp.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'>");
        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<title>Admin Analytics</title>");
        out.println("<link rel='stylesheet' href='" + req.getContextPath() + "/static/css/style.css'>");

        out.println("<style>");
        out.println("""
                .mongo-section {
                    margin-top: 35px;
                    background: #181818;
                    border-radius: 12px;
                    padding: 22px;
                    border-left: 5px solid #e50914;
                    box-shadow: 0 15px 35px rgba(0, 0, 0, 0.45);
                }

                .mongo-section h2 {
                    margin-top: 0;
                    margin-bottom: 10px;
                }

                .mongo-section p {
                    color: #aaa;
                    margin-bottom: 18px;
                }

                .mongo-badge {
                    display: inline-block;
                    background: #e50914;
                    color: white;
                    padding: 4px 9px;
                    border-radius: 999px;
                    font-size: 12px;
                    font-weight: bold;
                    margin-left: 8px;
                }

                .mongo-grid {
                    display: grid;
                    grid-template-columns: 1fr 1.5fr;
                    gap: 20px;
                    margin-top: 20px;
                }

                @media (max-width: 900px) {
                    .mongo-grid {
                        grid-template-columns: 1fr;
                    }
                }
                """);
        out.println("</style>");

        out.println("</head>");
        out.println("<body>");

        out.println("<div class='navbar'>");
        out.println("<div class='logo'>STREAMVAULT ADMIN</div>");
        out.println("<div class='nav-links'>");
        out.println("<a href='" + req.getContextPath() + "/home'>Home</a>");
        out.println("<a href='" + req.getContextPath() + "/dashboard'>Dashboard</a>");
        out.println("<a href='" + req.getContextPath() + "/logout'>Logout</a>");
        out.println("</div>");
        out.println("</div>");

        out.println("<div class='admin-hero'>");
        out.println("<h1>Admin Analytics</h1>");
        out.println("<p>Platform performance, revenue, MySQL analytics, and MongoDB activity logs.</p>");
        out.println("</div>");

        out.println("<div class='container'>");

        try (Connection conn = DatabaseConnection.getConnection()) {

            String churnSql =
                    "SELECT COUNT(*) AS churned_users " +
                            "FROM users u " +
                            "WHERE u.is_active = TRUE " +
                            "AND NOT EXISTS ( " +
                            "SELECT 1 FROM watch_history wh " +
                            "WHERE wh.user_id = u.user_id " +
                            "AND wh.watch_date >= DATE_SUB(NOW(), INTERVAL 30 DAY))";

            int churnCount = 0;
            try (PreparedStatement churnPs = conn.prepareStatement(churnSql);
                 ResultSet churnRs = churnPs.executeQuery()) {

                if (churnRs.next()) {
                    churnCount = churnRs.getInt("churned_users");
                }
            }

            int totalUsers = 0;
            String totalUsersSql = "SELECT COUNT(*) AS total_users FROM users";

            try (PreparedStatement usersPs = conn.prepareStatement(totalUsersSql);
                 ResultSet usersRs = usersPs.executeQuery()) {

                if (usersRs.next()) {
                    totalUsers = usersRs.getInt("total_users");
                }
            }

            double totalRevenue = 0;
            String revenueTotalSql =
                    "SELECT COALESCE(SUM(amount), 0) AS total_revenue " +
                            "FROM payments WHERE status = 'Completed'";

            try (PreparedStatement revenuePs = conn.prepareStatement(revenueTotalSql);
                 ResultSet totalRevenueRs = revenuePs.executeQuery()) {

                if (totalRevenueRs.next()) {
                    totalRevenue = totalRevenueRs.getDouble("total_revenue");
                }
            }

            int totalViews = 0;
            String viewsSql = "SELECT COUNT(*) AS total_views FROM watch_history";

            try (PreparedStatement viewsPs = conn.prepareStatement(viewsSql);
                 ResultSet viewsRs = viewsPs.executeQuery()) {

                if (viewsRs.next()) {
                    totalViews = viewsRs.getInt("total_views");
                }
            }

            out.println("<div class='stats-grid'>");

            out.println("<div class='stat-card'>");
            out.println("<h3>Total Users</h3>");
            out.println("<p>" + totalUsers + "</p>");
            out.println("</div>");

            out.println("<div class='stat-card'>");
            out.println("<h3>Total Revenue</h3>");
            out.println("<p>" + totalRevenue + " AED</p>");
            out.println("</div>");

            out.println("<div class='stat-card'>");
            out.println("<h3>Total Watch Events</h3>");
            out.println("<p>" + totalViews + "</p>");
            out.println("</div>");

            out.println("<div class='stat-card'>");
            out.println("<h3>Churn Risk Users</h3>");
            out.println("<p>" + churnCount + "</p>");
            out.println("</div>");

            out.println("</div>");

            out.println("<div class='admin-grid'>");

            out.println("<div class='analytics-panel'>");
            out.println("<h2>Top Content by Watch Count</h2>");

            String topSql =
                    "SELECT ci.title, ci.type, COUNT(wh.history_id) AS watch_count " +
                            "FROM content_items ci " +
                            "LEFT JOIN watch_history wh ON ci.content_id = wh.content_id " +
                            "GROUP BY ci.content_id, ci.title, ci.type " +
                            "ORDER BY watch_count DESC " +
                            "LIMIT 10";

            try (PreparedStatement topPs = conn.prepareStatement(topSql);
                 ResultSet topRs = topPs.executeQuery()) {

                out.println("<div class='table-box'>");
                out.println("<table>");
                out.println("<tr><th>Title</th><th>Type</th><th>Views</th></tr>");

                while (topRs.next()) {
                    out.println("<tr>");
                    out.println("<td>" + escape(topRs.getString("title")) + "</td>");
                    out.println("<td>" + escape(topRs.getString("type")) + "</td>");
                    out.println("<td>" + topRs.getInt("watch_count") + "</td>");
                    out.println("</tr>");
                }

                out.println("</table>");
                out.println("</div>");
            }

            out.println("</div>");

            out.println("<div class='analytics-panel'>");
            out.println("<h2>Revenue by Plan</h2>");

            String revenueSql =
                    "SELECT sp.plan_name, COUNT(DISTINCT s.subscription_id) AS total_subscriptions, " +
                            "COALESCE(SUM(p.amount),0) AS total_revenue " +
                            "FROM subscription_plans sp " +
                            "LEFT JOIN subscriptions s ON sp.plan_id = s.plan_id " +
                            "LEFT JOIN payments p ON s.subscription_id = p.subscription_id AND p.status = 'Completed' " +
                            "GROUP BY sp.plan_id, sp.plan_name " +
                            "ORDER BY total_revenue DESC";

            try (PreparedStatement revPs = conn.prepareStatement(revenueSql);
                 ResultSet revRs = revPs.executeQuery()) {

                out.println("<div class='table-box'>");
                out.println("<table>");
                out.println("<tr><th>Plan</th><th>Subscriptions</th><th>Revenue</th></tr>");

                while (revRs.next()) {
                    out.println("<tr>");
                    out.println("<td>" + escape(revRs.getString("plan_name")) + "</td>");
                    out.println("<td>" + revRs.getInt("total_subscriptions") + "</td>");
                    out.println("<td>" + revRs.getDouble("total_revenue") + " AED</td>");
                    out.println("</tr>");
                }

                out.println("</table>");
                out.println("</div>");
            }

            out.println("</div>");
            out.println("</div>");

            printMongoAnalytics(out);

        } catch (Exception e) {
            e.printStackTrace();
            out.println("<p>Error loading admin analytics: " + escape(e.getMessage()) + "</p>");
        }

        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }

    private void printMongoAnalytics(java.io.PrintWriter out) {
        out.println("<div class='mongo-section'>");
        out.println("<h2>MongoDB Activity Analytics <span class='mongo-badge'>NoSQL</span></h2>");
        out.println("<p>This section is pulled directly from MongoDB collection <b>streamvault_mongodb.activity_logs</b>.</p>");

        out.println("<div class='mongo-grid'>");

        out.println("<div class='analytics-panel'>");
        out.println("<h2>Event Type Counts</h2>");
        out.println("<div class='table-box'>");
        out.println("<table>");
        out.println("<tr><th>Event Type</th><th>Total Events</th></tr>");

        List<Document> eventCounts = MongoAnalyticsService.getEventTypeCounts();

        for (Document doc : eventCounts) {
            out.println("<tr>");
            out.println("<td>" + escape(String.valueOf(doc.get("_id"))) + "</td>");
            out.println("<td>" + escape(String.valueOf(doc.get("total_events"))) + "</td>");
            out.println("</tr>");
        }

        out.println("</table>");
        out.println("</div>");
        out.println("</div>");

        out.println("<div class='analytics-panel'>");
        out.println("<h2>Recent MongoDB Activity Logs</h2>");
        out.println("<div class='table-box'>");
        out.println("<table>");
        out.println("<tr><th>Event Type</th><th>User ID</th><th>Details</th><th>Timestamp</th></tr>");

        List<Document> recentEvents = MongoAnalyticsService.getRecentEvents();

        for (Document doc : recentEvents) {
            String eventType = String.valueOf(doc.get("event_type"));
            String userId = String.valueOf(doc.get("user_id"));
            String timestamp = String.valueOf(doc.get("timestamp"));

            String details;

            if ("SEARCH".equalsIgnoreCase(eventType)) {
                details = "Search: " + String.valueOf(doc.get("search_text")) +
                        " | Type: " + String.valueOf(doc.get("type_filter")) +
                        " | Genre: " + String.valueOf(doc.get("genre_filter"));
            } else if ("PLAY_CONTENT".equalsIgnoreCase(eventType)) {
                details = "Content ID: " + String.valueOf(doc.get("content_id")) +
                        " | Episode ID: " + String.valueOf(doc.get("episode_id")) +
                        " | Device: " + String.valueOf(doc.get("device_type"));
            } else {
                details = doc.toJson();
            }

            out.println("<tr>");
            out.println("<td>" + escape(eventType) + "</td>");
            out.println("<td>" + escape(userId) + "</td>");
            out.println("<td>" + escape(details) + "</td>");
            out.println("<td>" + escape(timestamp) + "</td>");
            out.println("</tr>");
        }

        out.println("</table>");
        out.println("</div>");
        out.println("</div>");

        out.println("</div>");
        out.println("</div>");
    }

    private String escape(String value) {
        if (value == null) return "";

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}