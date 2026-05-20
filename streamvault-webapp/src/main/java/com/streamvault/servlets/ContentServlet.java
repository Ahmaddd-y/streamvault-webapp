package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import com.streamvault.mongo.MongoAnalyticsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

@WebServlet("/content")
public class ContentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            resp.sendRedirect(req.getContextPath() + "/views/login.html");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        String idValue = req.getParameter("id");

        if (idValue == null || idValue.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/home");
            return;
        }

        int contentId;

        try {
            contentId = Integer.parseInt(idValue);
        } catch (NumberFormatException e) {
            resp.sendRedirect(req.getContextPath() + "/home");
            return;
        }

        resp.setContentType("text/html");
        var out = resp.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {

            String contentSql =
                    "SELECT ci.content_id, ci.title, ci.type, ci.release_year, ci.duration_minutes, " +
                            "ci.language, ci.age_rating, s.name AS studio_name " +
                            "FROM content_items ci " +
                            "JOIN studios s ON ci.studio_id = s.studio_id " +
                            "WHERE ci.content_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(contentSql)) {
                ps.setInt(1, contentId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        resp.sendRedirect(req.getContextPath() + "/home");
                        return;
                    }

                    String title = rs.getString("title");
                    String type = rs.getString("type");
                    int releaseYear = rs.getInt("release_year");
                    int duration = rs.getInt("duration_minutes");
                    String language = rs.getString("language");
                    String ageRating = rs.getString("age_rating");
                    String studio = rs.getString("studio_name");

                    String posterUrl = getPosterUrl(title);
                    String description = getDescription(title, type);
                    String genres = getGenreLine(title, type);
                    String mood = getMoodLine(title);
                    int latestProgress = getLatestContentProgress(conn, userId, contentId);

                    out.println("<!DOCTYPE html>");
                    out.println("<html lang='en'>");

                    out.println("<head>");
                    out.println("<meta charset='UTF-8'>");
                    out.println("<title>" + escape(title) + " - StreamVault</title>");
                    out.println("<style>");
                    printPageCss(out);
                    out.println("</style>");
                    out.println("</head>");

                    out.println("<body>");

                    out.println("<nav class='sv-navbar'>");
                    out.println("<div class='sv-logo'>STREAMVAULT</div>");
                    out.println("<div class='sv-links'>");
                    out.println("<a href='" + req.getContextPath() + "/home'>Home</a>");
                    out.println("<a href='" + req.getContextPath() + "/dashboard'>Dashboard</a>");
                    out.println("<a href='" + req.getContextPath() + "/logout'>Logout</a>");
                    out.println("</div>");
                    out.println("</nav>");

                    out.println("<section class='hero-detail' style=\"background-image: linear-gradient(to right, rgba(0,0,0,0.96) 0%, rgba(0,0,0,0.86) 38%, rgba(0,0,0,0.55) 70%, rgba(20,20,20,0.95) 100%), url('" + posterUrl + "');\">");

                    out.println("<div class='hero-content'>");

                    out.println("<div class='left-hero'>");

                    out.println("<div class='play-box'>");
                    out.println("<form action='" + req.getContextPath() + "/content' method='post'>");
                    out.println("<input type='hidden' name='content_id' value='" + contentId + "'>");
                    out.println("<button type='submit' class='hero-play-btn'>▶ Play Now</button>");
                    out.println("</form>");
                    out.println("</div>");

                    out.println("<div class='included'>● Included with StreamVault</div>");

                    out.println("<h1>" + escape(title) + "</h1>");

                    out.println("<div class='meta-line'>");
                    out.println("<span>" + releaseYear + "</span>");
                    out.println("<span>" + formatDuration(duration) + "</span>");
                    out.println("<span class='badge'>" + escape(ageRating) + "</span>");
                    out.println("<span class='badge'>HD</span>");
                    out.println("<span class='badge'>4K</span>");
                    out.println("<span>" + escape(language) + "</span>");
                    out.println("</div>");

                    out.println("<p class='description'>" + escape(description) + "</p>");

                    out.println("<div class='progress-section'>");
                    out.println("<span>" + latestProgress + "% watched</span>");
                    out.println("<div class='progress-bar'>");
                    out.println("<div class='progress-fill' style='width:" + latestProgress + "%;'></div>");
                    out.println("</div>");
                    out.println("</div>");

                    out.println("</div>");

                    out.println("<aside class='right-info'>");
                    out.println("<p><span>Type:</span> " + escape(type) + "</p>");
                    out.println("<p><span>Studio:</span> " + escape(studio) + "</p>");
                    out.println("<p><span>Genres:</span> " + escape(genres) + "</p>");
                    out.println("<p><span>This " + escape(type) + " is:</span> " + escape(mood) + "</p>");
                    out.println("</aside>");

                    out.println("</div>");
                    out.println("</section>");

                    out.println("<main class='page-body'>");

                    if ("Series".equalsIgnoreCase(type)) {
                        printEpisodes(conn, out, req, userId, contentId, posterUrl);
                    }

                    printRelatedContent(conn, out, req, contentId);
                    printReviews(conn, out, req, contentId);

                    out.println("</main>");

                    out.println("</body>");
                    out.println("</html>");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("<p>Error loading content: " + escape(e.getMessage()) + "</p>");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            resp.sendRedirect(req.getContextPath() + "/views/login.html");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        String contentIdValue = req.getParameter("content_id");
        String episodeIdValue = req.getParameter("episode_id");

        if (contentIdValue == null || contentIdValue.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/home");
            return;
        }

        int contentId;

        try {
            contentId = Integer.parseInt(contentIdValue);
        } catch (NumberFormatException e) {
            resp.sendRedirect(req.getContextPath() + "/home");
            return;
        }

        Integer episodeId = null;

        if (episodeIdValue != null && !episodeIdValue.isBlank()) {
            try {
                episodeId = Integer.parseInt(episodeIdValue);
            } catch (NumberFormatException ignored) {
                episodeId = null;
            }
        }

        String insertSql =
                "INSERT INTO watch_history " +
                        "(user_id, content_id, episode_id, watch_date, progress_pct, device_type, completed) " +
                        "VALUES (?, ?, ?, NOW(), 0, 'Browser', FALSE)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            ps.setInt(1, userId);
            ps.setInt(2, contentId);

            if (episodeId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, episodeId);
            }

            ps.executeUpdate();

            // MongoDB analytics log for Phase 5 hybrid SQL + NoSQL integration
            MongoAnalyticsService.logPlayEvent(userId, contentId, episodeId, "Browser");

        } catch (Exception e) {
            e.printStackTrace();
        }

        resp.sendRedirect(req.getContextPath() + "/dashboard");
    }

    private void printEpisodes(Connection conn, java.io.PrintWriter out, HttpServletRequest req,
                               int userId, int contentId, String posterUrl)
            throws SQLException {

        out.println("<section class='episodes-section' id='episodes'>");

        out.println("<div class='tabs-row'>");
        out.println("<a class='tab active' href='#episodes'>Episodes</a>");
        out.println("<a class='tab' href='#related'>Related</a>");
        out.println("</div>");

        String episodeSql =
                "SELECT e.episode_id, e.season_no, e.episode_no, e.title, e.duration_minutes, " +
                        "COALESCE(( " +
                        "   SELECT wh.progress_pct " +
                        "   FROM watch_history wh " +
                        "   WHERE wh.user_id = ? AND wh.episode_id = e.episode_id " +
                        "   ORDER BY wh.watch_date DESC, wh.history_id DESC " +
                        "   LIMIT 1 " +
                        "), 0) AS latest_progress " +
                        "FROM episodes e " +
                        "WHERE e.content_id = ? " +
                        "ORDER BY e.season_no, e.episode_no";

        try (PreparedStatement ps = conn.prepareStatement(episodeSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contentId);

            try (ResultSet rs = ps.executeQuery()) {

                out.println("<div class='episode-count'>Episodes</div>");
                out.println("<div class='episode-carousel'>");

                boolean hasEpisodes = false;

                while (rs.next()) {
                    hasEpisodes = true;

                    int episodeId = rs.getInt("episode_id");
                    int progress = rs.getInt("latest_progress");
                    int seasonNo = rs.getInt("season_no");
                    int episodeNo = rs.getInt("episode_no");
                    String episodeTitle = rs.getString("title");
                    int duration = rs.getInt("duration_minutes");

                    out.println("<form class='episode-card' action='" + req.getContextPath() + "/content' method='post'>");
                    out.println("<input type='hidden' name='content_id' value='" + contentId + "'>");
                    out.println("<input type='hidden' name='episode_id' value='" + episodeId + "'>");

                    out.println("<button type='submit' class='episode-button'>");

                    out.println("<div class='episode-thumb' style=\"background-image: linear-gradient(to bottom, rgba(0,0,0,0.15), rgba(0,0,0,0.75)), url('" + posterUrl + "');\">");
                    out.println("<div class='episode-play'>▶</div>");
                    out.println("</div>");

                    out.println("<div class='episode-info'>");
                    out.println("<h3>" + episodeNo + ". " + escape(episodeTitle) + "</h3>");
                    out.println("<p>S" + seasonNo + " E" + episodeNo + " • " + duration + " min</p>");
                    out.println("<p class='episode-desc'>" + escape(getEpisodeDescription(episodeTitle)) + "</p>");

                    out.println("<div class='episode-progress-label'>" + progress + "% watched</div>");
                    out.println("<div class='episode-progress-bar'>");
                    out.println("<div class='episode-progress-fill' style='width:" + progress + "%;'></div>");
                    out.println("</div>");

                    out.println("</div>");
                    out.println("</button>");
                    out.println("</form>");
                }

                out.println("</div>");

                if (!hasEpisodes) {
                    out.println("<p>No episodes available.</p>");
                }
            }
        }

        out.println("</section>");
    }

    private void printRelatedContent(Connection conn, java.io.PrintWriter out, HttpServletRequest req, int currentContentId)
            throws SQLException {

        out.println("<section class='related-section' id='related'>");
        out.println("<h2>Related</h2>");
        out.println("<p class='related-subtitle'>More movies and TV shows you may like</p>");

        String relatedSql =
                "SELECT ci.content_id, ci.title, ci.type, ci.release_year, ci.language, ci.age_rating " +
                        "FROM content_items ci " +
                        "WHERE ci.content_id <> ? " +
                        "ORDER BY ci.release_year DESC, ci.title ASC " +
                        "LIMIT 10";

        try (PreparedStatement ps = conn.prepareStatement(relatedSql)) {
            ps.setInt(1, currentContentId);

            try (ResultSet rs = ps.executeQuery()) {

                boolean hasRelated = false;

                out.println("<div class='related-carousel'>");

                while (rs.next()) {
                    hasRelated = true;

                    int contentId = rs.getInt("content_id");
                    String title = rs.getString("title");
                    String type = rs.getString("type");
                    String posterUrl = getPosterUrl(title);

                    out.println("<a class='related-card' href='" + req.getContextPath() + "/content?id=" + contentId + "'>");

                    out.println("<img src='" + posterUrl + "' alt='" + escape(title) + " poster' " +
                            "onerror=\"this.onerror=null;this.src='https://image.tmdb.org/t/p/w500/8UlWHLMpgZm9bx6QYh0NFoq67TZ.jpg';\">");

                    out.println("<div class='related-info'>");
                    out.println("<h3>" + escape(title) + "</h3>");
                    out.println("<p>" + escape(type) + " • " + rs.getInt("release_year") + "</p>");
                    out.println("<p>" + escape(rs.getString("language")) + " • " + escape(rs.getString("age_rating")) + "</p>");
                    out.println("</div>");

                    out.println("</a>");
                }

                out.println("</div>");

                if (!hasRelated) {
                    out.println("<p>No related titles found.</p>");
                }
            }
        }

        out.println("</section>");
    }

    private void printReviews(Connection conn, java.io.PrintWriter out, HttpServletRequest req, int contentId)
            throws SQLException {

        out.println("<section class='reviews-section'>");

        out.println("<div class='review-header'>");
        out.println("<h2>User Reviews</h2>");
        out.println("<a href='" + req.getContextPath() + "/review?content_id=" + contentId + "'>Add Review</a>");
        out.println("</div>");

        String reviewSql =
                "SELECT u.full_name, rr.rating, rr.review_text, rr.posted_at " +
                        "FROM reviews_ratings rr " +
                        "JOIN users u ON rr.user_id = u.user_id " +
                        "WHERE rr.content_id = ? " +
                        "ORDER BY rr.posted_at DESC " +
                        "LIMIT 6";

        try (PreparedStatement ps = conn.prepareStatement(reviewSql)) {
            ps.setInt(1, contentId);

            try (ResultSet rs = ps.executeQuery()) {

                boolean hasReviews = false;

                out.println("<div class='review-grid'>");

                while (rs.next()) {
                    hasReviews = true;

                    out.println("<div class='review-card'>");
                    out.println("<h3>" + escape(rs.getString("full_name")) + "</h3>");
                    out.println("<p class='rating'>" + rs.getInt("rating") + "/10</p>");
                    out.println("<p>" + escape(rs.getString("review_text")) + "</p>");
                    out.println("<span>" + rs.getTimestamp("posted_at") + "</span>");
                    out.println("</div>");
                }

                out.println("</div>");

                if (!hasReviews) {
                    out.println("<p>No reviews yet.</p>");
                }
            }
        }

        out.println("</section>");
    }

    private int getLatestContentProgress(Connection conn, int userId, int contentId)
            throws SQLException {

        String sql =
                "SELECT progress_pct " +
                        "FROM watch_history " +
                        "WHERE user_id = ? AND content_id = ? " +
                        "ORDER BY watch_date DESC, history_id DESC " +
                        "LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("progress_pct");
                }
            }
        }

        return 0;
    }

    private void printPageCss(java.io.PrintWriter out) {
        out.println("""
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }

                body {
                    font-family: Arial, Helvetica, sans-serif;
                    background: #05070b;
                    color: #fff;
                    min-height: 100vh;
                }

                a {
                    color: white;
                    text-decoration: none;
                }

                .sv-navbar {
                    width: calc(100% - 90px);
                    height: 76px;
                    margin: 22px auto 0;
                    padding: 0 34px;
                    background: rgba(15, 18, 24, 0.96);
                    border-radius: 14px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    position: sticky;
                    top: 18px;
                    z-index: 1000;
                    box-shadow: 0 18px 45px rgba(0,0,0,0.45);
                }

                .sv-logo {
                    font-size: 30px;
                    font-weight: 900;
                    color: #e50914;
                    letter-spacing: 1px;
                }

                .sv-links {
                    display: flex;
                    gap: 28px;
                    font-weight: 700;
                    color: #ddd;
                }

                .sv-links a {
                    font-size: 16px;
                    font-weight: 800;
                }

                .sv-links a:hover {
                    color: #e50914;
                }

                .hero-detail {
                    min-height: 620px;
                    background-size: cover;
                    background-position: center;
                    padding: 70px 5% 60px;
                    margin-top: -98px;
                    padding-top: 160px;
                }

                .hero-content {
                    display: grid;
                    grid-template-columns: minmax(0, 1fr) 380px;
                    gap: 50px;
                    max-width: 1450px;
                    margin: 0 auto;
                    align-items: start;
                }

                .left-hero {
                    max-width: 850px;
                }

                .play-box {
                    width: 380px;
                    margin-bottom: 18px;
                }

                .hero-play-btn {
                    width: 100%;
                    padding: 18px;
                    background: rgba(0,0,0,0.35);
                    border: 2px solid #4b5563;
                    border-radius: 8px;
                    color: #d8dde5;
                    font-size: 23px;
                    font-weight: 800;
                    cursor: pointer;
                    text-align: center;
                }

                .hero-play-btn:hover {
                    background: #e50914;
                    border-color: #e50914;
                    color: white;
                }

                .included {
                    color: #d8eaff;
                    font-size: 17px;
                    margin: 14px 0 28px;
                }

                h1 {
                    font-size: 56px;
                    line-height: 1.05;
                    margin-bottom: 16px;
                }

                .meta-line {
                    display: flex;
                    align-items: center;
                    flex-wrap: wrap;
                    gap: 13px;
                    color: #b8bec8;
                    font-size: 18px;
                    margin-bottom: 20px;
                }

                .badge {
                    border: 1px solid #8f98a3;
                    padding: 3px 8px;
                    border-radius: 4px;
                    color: white;
                    font-weight: 700;
                }

                .description {
                    color: #f4f4f4;
                    font-size: 23px;
                    line-height: 1.55;
                    max-width: 850px;
                    margin-bottom: 24px;
                }

                .progress-section {
                    width: 420px;
                    margin-top: 20px;
                    color: #d0d0d0;
                    font-weight: 700;
                }

                .progress-bar {
                    margin-top: 8px;
                    height: 8px;
                    width: 100%;
                    background: #2b2f36;
                    border-radius: 999px;
                    overflow: hidden;
                }

                .progress-fill {
                    height: 100%;
                    background: #e50914;
                    border-radius: 999px;
                }

                .right-info {
                    margin-top: 100px;
                    color: #f4f4f4;
                    font-size: 20px;
                    line-height: 1.5;
                    background: rgba(0,0,0,0.25);
                    padding: 20px;
                    border-radius: 12px;
                    border-left: 4px solid #e50914;
                }

                .right-info p {
                    margin-bottom: 18px;
                }

                .right-info span {
                    color: #8d929b;
                    font-weight: 700;
                }

                .page-body {
                    max-width: 1450px;
                    margin: 0 auto;
                    padding: 36px 5% 80px;
                }

                .tabs-row {
                    display: flex;
                    gap: 34px;
                    margin-bottom: 26px;
                    font-size: 22px;
                    font-weight: 800;
                    color: #aaa;
                }

                .tab {
                    padding-bottom: 13px;
                    color: #aaa;
                }

                .tab:hover {
                    color: white;
                }

                .tab.active {
                    color: white;
                    border-bottom: 3px solid white;
                }

                .episode-count {
                    color: #aaa;
                    font-size: 21px;
                    margin-bottom: 24px;
                }

                .episode-carousel {
                    display: grid;
                    grid-template-columns: repeat(4, minmax(250px, 1fr));
                    gap: 26px;
                }

                .episode-card {
                    border: none;
                    background: transparent;
                }

                .episode-button {
                    width: 100%;
                    background: transparent;
                    border: none;
                    color: white;
                    text-align: left;
                    cursor: pointer;
                }

                .episode-thumb {
                    height: 165px;
                    border-radius: 10px;
                    background-size: cover;
                    background-position: center;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-bottom: 14px;
                    overflow: hidden;
                }

                .episode-play {
                    width: 54px;
                    height: 54px;
                    border-radius: 999px;
                    background: rgba(229,9,20,0.9);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 23px;
                    opacity: 0;
                    transition: 0.25s ease;
                }

                .episode-card:hover .episode-play {
                    opacity: 1;
                }

                .episode-info h3 {
                    font-size: 22px;
                    margin-bottom: 10px;
                }

                .episode-info p {
                    color: #aaa;
                    font-size: 16px;
                    line-height: 1.45;
                    margin-bottom: 9px;
                }

                .episode-desc {
                    min-height: 48px;
                }

                .episode-progress-label {
                    color: #b8bec8;
                    font-size: 13px;
                    margin: 8px 0 6px;
                }

                .episode-progress-bar {
                    height: 7px;
                    background: #2b2f36;
                    border-radius: 999px;
                    overflow: hidden;
                }

                .episode-progress-fill {
                    height: 100%;
                    background: #e50914;
                }

                .related-section {
                    margin-top: 55px;
                }

                .related-section h2 {
                    font-size: 30px;
                    margin-bottom: 6px;
                }

                .related-subtitle {
                    color: #aaa;
                    font-size: 17px;
                    margin-bottom: 22px;
                }

                .related-carousel {
                    display: flex;
                    gap: 20px;
                    overflow-x: auto;
                    padding: 10px 0 30px;
                    scroll-snap-type: x mandatory;
                }

                .related-card {
                    min-width: 230px;
                    max-width: 230px;
                    background: #11151c;
                    border-radius: 12px;
                    overflow: hidden;
                    scroll-snap-align: start;
                    transition: 0.25s ease;
                    box-shadow: 0 15px 35px rgba(0,0,0,0.45);
                }

                .related-card:hover {
                    transform: scale(1.06);
                    background: #181c24;
                }

                .related-card img {
                    width: 100%;
                    height: 315px;
                    object-fit: cover;
                    display: block;
                }

                .related-info {
                    padding: 13px;
                }

                .related-info h3 {
                    font-size: 18px;
                    margin-bottom: 6px;
                    line-height: 1.15;
                }

                .related-info p {
                    color: #aaa;
                    font-size: 14px;
                    margin-bottom: 4px;
                }

                .reviews-section {
                    margin-top: 55px;
                    background: #101319;
                    border-radius: 14px;
                    border-left: 5px solid #e50914;
                    padding: 24px;
                }

                .review-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 20px;
                }

                .review-header h2 {
                    font-size: 28px;
                }

                .review-header a {
                    background: #e50914;
                    padding: 9px 13px;
                    border-radius: 6px;
                    font-weight: 800;
                }

                .review-header a:hover {
                    background: #b20710;
                }

                .review-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
                    gap: 16px;
                }

                .review-card {
                    background: #181c24;
                    border-radius: 10px;
                    padding: 16px;
                }

                .review-card h3 {
                    margin-bottom: 4px;
                }

                .rating {
                    color: #e50914;
                    font-weight: 900;
                    margin-bottom: 7px;
                }

                .review-card p {
                    color: #ddd;
                    line-height: 1.45;
                }

                .review-card span {
                    display: block;
                    color: #777;
                    font-size: 12px;
                    margin-top: 10px;
                }

                @media (max-width: 1100px) {
                    .hero-content {
                        grid-template-columns: 1fr;
                    }

                    .right-info {
                        margin-top: 0;
                    }

                    .episode-carousel {
                        grid-template-columns: repeat(2, minmax(230px, 1fr));
                    }
                }

                @media (max-width: 700px) {
                    .sv-navbar {
                        width: 94%;
                        padding: 0 18px;
                    }

                    .sv-links {
                        gap: 12px;
                        font-size: 13px;
                    }

                    h1 {
                        font-size: 38px;
                    }

                    .description {
                        font-size: 18px;
                    }

                    .play-box,
                    .progress-section {
                        width: 100%;
                    }

                    .episode-carousel {
                        grid-template-columns: 1fr;
                    }

                    .related-card {
                        min-width: 190px;
                        max-width: 190px;
                    }

                    .related-card img {
                        height: 270px;
                    }
                }
                """);
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

    private String getDescription(String title, String type) {
        if (title == null) return "A featured StreamVault title available for subscribers.";

        String t = title.toLowerCase().trim();

        switch (t) {
            case "daredevil":
                return "A blind lawyer fights crime at night as a masked vigilante while protecting his city from powerful enemies.";
            case "spiderman 3":
            case "spider-man 3":
                return "Peter Parker faces new villains, personal conflict, and the dark influence of a mysterious alien suit.";
            case "the maid":
                return "A dramatic series following personal struggles, hidden secrets, and emotional challenges faced by its characters.";
            case "dune part 2":
            case "dune: part two":
                return "A young leader rises in a desert world filled with political conflict, prophecy, and battles for survival.";
            case "loki":
                return "The God of Mischief is pulled into a timeline-bending adventure that changes his understanding of destiny.";
            case "the fall guy":
                return "A stunt performer is pulled into a dangerous mystery while trying to prove himself and protect the people around him.";
            case "it part 2":
            case "it: chapter two":
                return "A group of childhood friends reunite to face a terrifying force that returns to haunt their town.";
            case "jumanji":
            case "jumanji: welcome to the jungle":
                return "A group of players are trapped inside a dangerous game world and must survive its challenges to escape.";
            case "starwars return of the sith":
            case "star wars return of the sith":
            case "star wars: return of the sith":
            case "star wars: revenge of the sith":
                return "A galaxy falls into darkness as a powerful Jedi faces betrayal, war, and the rise of a Sith empire.";
            case "avatar the last airbender":
            case "avatar: the last airbender":
                return "A young hero must master the elements and restore balance to a world divided by war.";
            default:
                return "This " + type.toLowerCase() + " is available on StreamVault with viewing history, reviews, and subscriber tracking.";
        }
    }

    private String getEpisodeDescription(String title) {
        if (title == null) return "Continue watching this episode and track your progress.";
        return "Continue this episode from your latest progress and keep watching on StreamVault.";
    }

    private String getGenreLine(String title, String type) {
        if (title == null) return "Drama";

        String t = title.toLowerCase().trim();

        switch (t) {
            case "daredevil":
                return "Action, Thriller, Superhero";
            case "spiderman 3":
            case "spider-man 3":
                return "Action, Adventure, Superhero";
            case "the maid":
                return "Drama";
            case "dune part 2":
            case "dune: part two":
                return "Sci-Fi, Adventure, Drama";
            case "loki":
                return "Fantasy, Sci-Fi, Adventure";
            case "the fall guy":
                return "Action, Comedy";
            case "it part 2":
            case "it: chapter two":
                return "Horror, Thriller";
            case "jumanji":
            case "jumanji: welcome to the jungle":
                return "Adventure, Comedy, Fantasy";
            case "starwars return of the sith":
            case "star wars return of the sith":
            case "star wars: return of the sith":
            case "star wars: revenge of the sith":
                return "Sci-Fi, Action, Adventure";
            case "avatar the last airbender":
            case "avatar: the last airbender":
                return "Fantasy, Adventure";
            default:
                return type + ", Featured";
        }
    }

    private String getMoodLine(String title) {
        if (title == null) return "Exciting, engaging, and popular";

        String t = title.toLowerCase().trim();

        if (t.contains("it")) return "Scary, suspenseful, dark";
        if (t.contains("daredevil")) return "Gritty, action-packed, intense";
        if (t.contains("jumanji")) return "Funny, adventurous, exciting";
        if (t.contains("dune")) return "Epic, dramatic, cinematic";
        if (t.contains("loki")) return "Mysterious, clever, time-bending";
        if (t.contains("avatar")) return "Adventurous, emotional, heroic";

        return "Exciting, engaging, and popular";
    }

    private String formatDuration(int minutes) {
        if (minutes < 60) return minutes + "m";

        int hours = minutes / 60;
        int mins = minutes % 60;

        if (mins == 0) return hours + "h";

        return hours + "h " + mins + "m";
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