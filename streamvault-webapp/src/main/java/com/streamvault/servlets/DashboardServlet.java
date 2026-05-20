package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            resp.sendRedirect(req.getContextPath() + "/views/login.html");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        resp.setContentType("text/html");
        var out = resp.getWriter();

        out.println("<html><head>");
        out.println("<title>Dashboard</title>");
        out.println("<link rel='stylesheet' href='" + req.getContextPath() + "/static/css/style.css'>");
        out.println("</head><body>");

        out.println("<div class='navbar'>");
        out.println("<div class='logo'>StreamVault</div>");
        out.println("<div class='nav-links'>");
        out.println("<a href='" + req.getContextPath() + "/home'>Home</a>");
        out.println("<a href='" + req.getContextPath() + "/dashboard'>Dashboard</a>");
        out.println("<a href='" + req.getContextPath() + "/logout'>Logout</a>");
        out.println("</div>");
        out.println("</div>");

        out.println("<div class='container'>");
        out.println("<h1>Dashboard</h1>");

        try (Connection conn = DatabaseConnection.getConnection()) {

            /*
             * ACTIVE SUBSCRIPTION
             */
            out.println("<h2>Active Subscription</h2>");

            String subSql =
                    "SELECT sp.plan_name, sp.monthly_price, sp.resolution_limit, " +
                            "s.status, s.start_date, s.auto_renew " +
                            "FROM subscriptions s " +
                            "JOIN subscription_plans sp ON s.plan_id = sp.plan_id " +
                            "WHERE s.user_id = ? AND s.status = 'Active' " +
                            "LIMIT 1";

            try (PreparedStatement subPs = conn.prepareStatement(subSql)) {
                subPs.setInt(1, userId);

                try (ResultSet subRs = subPs.executeQuery()) {
                    if (subRs.next()) {
                        out.println("<div class='stats-grid'>");

                        out.println("<div class='stat-card'>");
                        out.println("<h3>Plan</h3>");
                        out.println("<p>" + escape(subRs.getString("plan_name")) + "</p>");
                        out.println("</div>");

                        out.println("<div class='stat-card'>");
                        out.println("<h3>Price</h3>");
                        out.println("<p>" + subRs.getDouble("monthly_price") + " AED</p>");
                        out.println("</div>");

                        out.println("<div class='stat-card'>");
                        out.println("<h3>Resolution</h3>");
                        out.println("<p>" + escape(subRs.getString("resolution_limit")) + "</p>");
                        out.println("</div>");

                        out.println("<div class='stat-card'>");
                        out.println("<h3>Status</h3>");
                        out.println("<p>" + escape(subRs.getString("status")) + "</p>");
                        out.println("</div>");

                        out.println("</div>");
                    } else {
                        out.println("<p>No active subscription.</p>");
                    }
                }
            }

            /*
             * BILLING HISTORY
             */
            out.println("<h2>Billing History</h2>");

            String billSql =
                    "SELECT p.amount, p.currency, p.method, p.payment_date, p.status " +
                            "FROM payments p " +
                            "JOIN subscriptions s ON p.subscription_id = s.subscription_id " +
                            "WHERE s.user_id = ? " +
                            "ORDER BY p.payment_date DESC " +
                            "LIMIT 5";

            try (PreparedStatement billPs = conn.prepareStatement(billSql)) {
                billPs.setInt(1, userId);

                try (ResultSet billRs = billPs.executeQuery()) {
                    out.println("<div class='table-box'>");
                    out.println("<table>");
                    out.println("<tr><th>Amount</th><th>Currency</th><th>Method</th><th>Date</th><th>Status</th></tr>");

                    boolean hasBills = false;

                    while (billRs.next()) {
                        hasBills = true;

                        out.println("<tr>");
                        out.println("<td>" + billRs.getDouble("amount") + "</td>");
                        out.println("<td>" + escape(billRs.getString("currency")) + "</td>");
                        out.println("<td>" + escape(billRs.getString("method")) + "</td>");
                        out.println("<td>" + billRs.getTimestamp("payment_date") + "</td>");
                        out.println("<td>" + escape(billRs.getString("status")) + "</td>");
                        out.println("</tr>");
                    }

                    out.println("</table>");
                    out.println("</div>");

                    if (!hasBills) {
                        out.println("<p>No billing records found.</p>");
                    }
                }
            }

            /*
             * CONTINUE WATCHING
             * Shows only one latest unfinished record per content item.
             * For series, it shows the latest unfinished episode watched.
             */
            out.println("<h2>Continue Watching</h2>");

            String cwSql =
                    "SELECT * FROM ( " +
                            "   SELECT wh.history_id, ci.content_id, ci.title, ci.type, ci.release_year, " +
                            "          ci.language, ci.age_rating, wh.progress_pct, wh.watch_date, " +
                            "          e.season_no, e.episode_no, e.title AS episode_title, " +
                            "          ROW_NUMBER() OVER (PARTITION BY ci.content_id ORDER BY wh.watch_date DESC, wh.history_id DESC) AS rn " +
                            "   FROM watch_history wh " +
                            "   JOIN content_items ci ON wh.content_id = ci.content_id " +
                            "   LEFT JOIN episodes e ON wh.episode_id = e.episode_id " +
                            "   WHERE wh.user_id = ? AND wh.completed = FALSE " +
                            ") latest_watch " +
                            "WHERE rn = 1 " +
                            "ORDER BY watch_date DESC";

            try (PreparedStatement cwPs = conn.prepareStatement(cwSql)) {
                cwPs.setInt(1, userId);

                try (ResultSet cwRs = cwPs.executeQuery()) {

                    boolean hasContinue = false;

                    out.println("<div class='horizontal-row continue-row'>");

                    while (cwRs.next()) {
                        hasContinue = true;

                        int contentId = cwRs.getInt("content_id");
                        String title = cwRs.getString("title");
                        String type = cwRs.getString("type");
                        int progress = cwRs.getInt("progress_pct");
                        String posterUrl = getPosterUrl(title);

                        String episodeTitle = cwRs.getString("episode_title");
                        int seasonNo = cwRs.getInt("season_no");
                        int episodeNo = cwRs.getInt("episode_no");

                        out.println("<div class='title-card continue-card'>");

                        out.println("<img class='poster-img continue-poster' src='" + posterUrl + "' alt='" + escape(title) + " poster' " +
                                "onerror=\"this.onerror=null;this.src='https://image.tmdb.org/t/p/w500/8UlWHLMpgZm9bx6QYh0NFoq67TZ.jpg';\">");

                        out.println("<div class='card-info continue-info'>");

                        out.println("<h3>" + escape(title) + "</h3>");
                        out.println("<p>" + escape(type) + " • " + cwRs.getInt("release_year") + "</p>");
                        out.println("<p>" + escape(cwRs.getString("language")) + " • " + escape(cwRs.getString("age_rating")) + "</p>");

                        if ("Series".equalsIgnoreCase(type) && episodeTitle != null && !episodeTitle.isBlank()) {
                            out.println("<p class='episode-meta'>S" + seasonNo + " E" + episodeNo + " • " + escape(episodeTitle) + "</p>");
                        }

                        out.println("<div class='progress-wrapper'>");
                        out.println("<div class='progress-label'>" + progress + "% watched</div>");

                        out.println("<div class='progress-bar'>");
                        out.println("<div class='progress-fill' style='width:" + progress + "%;'></div>");
                        out.println("</div>");

                        out.println("</div>");

                        out.println("<a class='watch-link continue-play-btn' href='" + req.getContextPath() + "/content?id=" + contentId + "'>▶ Play</a>");

                        out.println("</div>");
                        out.println("</div>");
                    }

                    out.println("</div>");

                    if (!hasContinue) {
                        out.println("<p>No continue-watching items yet.</p>");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("<p>Error loading dashboard: " + escape(e.getMessage()) + "</p>");
        }

        out.println("</div>");
        out.println("</body></html>");
    }

    private String getPosterUrl(String title) {
        if (title == null) {
            return "https://image.tmdb.org/t/p/w500/8UlWHLMpgZm9bx6QYh0NFoq67TZ.jpg";
        }

        String t = title.toLowerCase().trim();

        switch (t) {
            case "daredevil":
                return "https://image.tmdb.org/t/p/w500/QWbPaDxiB6LW2LjASknzYBvjMj.jpg";

            case "spiderman 3":
            case "spider-man 3":
                return "https://image.tmdb.org/t/p/w500/qFmwhVUoUSXjkKRmca5yGDEXBIj.jpg";

            case "the maid":
                return "https://image.tmdb.org/t/p/w500/7K5a3Fo1jKxL9pQbZ6xMLxvZ3qU.jpg";

            case "dune part 2":
            case "dune: part two":
                return "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg";

            case "loki":
                return "https://image.tmdb.org/t/p/w500/voHUmluYmKyleFkTu3lOXQG702u.jpg";

            case "the fall guy":
                return "https://image.tmdb.org/t/p/w500/aBkqu7EddWK7qmY4grL4I6edx2h.jpg";

            case "it part 2":
            case "it: chapter two":
                return "https://image.tmdb.org/t/p/w500/zfE0R94v1E8cuKAerbskfD3VfUt.jpg";

            case "jumanji":
            case "jumanji: welcome to the jungle":
                return "https://image.tmdb.org/t/p/w500/pSgXKPU5h6U89ipF7HBYajvYt7j.jpg";

            case "starwars return of the sith":
            case "star wars return of the sith":
            case "star wars: return of the sith":
            case "star wars: revenge of the sith":
                return "https://image.tmdb.org/t/p/w500/xfSAoBEm9MNBjmlNcDYLvLSMlnq.jpg";

            case "avatar the last airbender":
            case "avatar: the last airbender":
                return "https://image.tmdb.org/t/p/w500/v2vn1coUMPKw0GI1KGC5J4IXtqp.jpg";

            default:
                return "https://image.tmdb.org/t/p/w500/8UlWHLMpgZm9bx6QYh0NFoq67TZ.jpg";
        }
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