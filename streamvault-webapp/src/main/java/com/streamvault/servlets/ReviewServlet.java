package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

@WebServlet("/review")
public class ReviewServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/views/login.html");
            return;
        }

        String contentIdValue = request.getParameter("content_id");

        if (contentIdValue == null || contentIdValue.isBlank()) {
            contentIdValue = request.getParameter("id");
        }

        if (contentIdValue == null || contentIdValue.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        int contentId;

        try {
            contentId = Integer.parseInt(contentIdValue);
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String title = "Content";

        String titleSql = "SELECT title FROM content_items WHERE content_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(titleSql)) {

            ps.setInt(1, contentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    title = rs.getString("title");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        response.setContentType("text/html");
        var out = response.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'>");

        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<title>Add Review - StreamVault</title>");

        out.println("<style>");
        out.println("""
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }

                body {
                    font-family: Arial, Helvetica, sans-serif;
                    background: #141414;
                    color: #f5f5f5;
                    min-height: 100vh;
                }

                a {
                    color: white;
                    text-decoration: none;
                }

                .navbar {
                    width: 100%;
                    height: 70px;
                    background: linear-gradient(to bottom, #000, #141414);
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 0 4%;
                    position: sticky;
                    top: 0;
                    z-index: 1000;
                    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
                }

                .logo {
                    font-size: 28px;
                    font-weight: 900;
                    color: #e50914;
                    letter-spacing: 1px;
                }

                .nav-links {
                    display: flex;
                    gap: 24px;
                }

                .nav-links a {
                    color: #ddd;
                    font-weight: 600;
                }

                .nav-links a:hover {
                    color: #e50914;
                }

                .review-page {
                    width: 92%;
                    max-width: 950px;
                    margin: 0 auto;
                    padding: 55px 0 90px;
                }

                .review-title {
                    font-size: 48px;
                    font-weight: 900;
                    margin-bottom: 8px;
                    color: #fff;
                }

                .content-title {
                    font-size: 26px;
                    color: #ccc;
                    margin-bottom: 26px;
                }

                .review-card {
                    background: linear-gradient(145deg, #181818, #0c0c0c);
                    border: 1px solid #2a2a2a;
                    border-left: 5px solid #e50914;
                    border-radius: 16px;
                    padding: 32px;
                    box-shadow: 0 22px 55px rgba(0, 0, 0, 0.65);
                }

                .form-group {
                    margin-bottom: 22px;
                }

                label {
                    display: block;
                    font-size: 16px;
                    font-weight: 800;
                    color: #f5f5f5;
                    margin-bottom: 9px;
                }

                select {
                    width: 100%;
                    background: #242424;
                    color: white;
                    border: 1px solid #3a3a3a;
                    border-radius: 10px;
                    padding: 14px;
                    font-size: 15px;
                    outline: none;
                }

                select:focus {
                    border-color: #e50914;
                    box-shadow: 0 0 0 3px rgba(229, 9, 20, 0.18);
                }

                textarea {
                    width: 100%;
                    height: 230px;
                    min-height: 230px;
                    max-height: 230px;
                    resize: none;

                    background: #202020;
                    color: white;
                    border: 1px solid #3a3a3a;
                    border-radius: 12px;

                    padding: 18px;
                    font-family: Arial, Helvetica, sans-serif;
                    font-size: 16px;
                    line-height: 1.55;
                    outline: none;
                }

                textarea::placeholder {
                    color: #888;
                }

                textarea:focus {
                    border-color: #e50914;
                    background: #262626;
                    box-shadow: 0 0 0 3px rgba(229, 9, 20, 0.18);
                }

                .actions {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    gap: 18px;
                    margin-top: 10px;
                }

                .back-link {
                    color: #ddd;
                    font-weight: 700;
                }

                .back-link:hover {
                    color: #e50914;
                }

                .submit-btn {
                    width: 220px;
                    background: #e50914;
                    color: white;
                    border: none;
                    border-radius: 10px;
                    padding: 14px 20px;
                    font-size: 15px;
                    font-weight: 900;
                    cursor: pointer;
                    transition: 0.25s ease;
                }

                .submit-btn:hover {
                    background: #b20710;
                    transform: translateY(-2px);
                    box-shadow: 0 12px 28px rgba(229, 9, 20, 0.25);
                }

                @media (max-width: 700px) {
                    .navbar {
                        flex-direction: column;
                        height: auto;
                        padding: 18px;
                        gap: 12px;
                    }

                    .review-page {
                        padding-top: 35px;
                    }

                    .review-title {
                        font-size: 36px;
                    }

                    .review-card {
                        padding: 22px;
                    }

                    .actions {
                        flex-direction: column-reverse;
                        align-items: stretch;
                    }

                    .submit-btn {
                        width: 100%;
                    }
                }
                """);
        out.println("</style>");

        out.println("</head>");

        out.println("<body>");

        out.println("<div class='navbar'>");
        out.println("<div class='logo'>STREAMVAULT</div>");
        out.println("<div class='nav-links'>");
        out.println("<a href='" + request.getContextPath() + "/home'>Home</a>");
        out.println("<a href='" + request.getContextPath() + "/dashboard'>Dashboard</a>");
        out.println("<a href='" + request.getContextPath() + "/logout'>Logout</a>");
        out.println("</div>");
        out.println("</div>");

        out.println("<main class='review-page'>");

        out.println("<h1 class='review-title'>Add Review</h1>");
        out.println("<h2 class='content-title'>" + escape(title) + "</h2>");

        out.println("<section class='review-card'>");

        out.println("<form action='" + request.getContextPath() + "/review' method='post'>");
        out.println("<input type='hidden' name='content_id' value='" + contentId + "'>");

        out.println("<div class='form-group'>");
        out.println("<label>Rating out of 10</label>");
        out.println("<select name='rating' required>");

        for (int i = 10; i >= 1; i--) {
            out.println("<option value='" + i + "'>" + i + "/10</option>");
        }

        out.println("</select>");
        out.println("</div>");

        out.println("<div class='form-group'>");
        out.println("<label>Your Review</label>");
        out.println("<textarea name='review_text' placeholder='Share your thoughts about this title. What did you like? Was it worth watching?' required></textarea>");
        out.println("</div>");

        out.println("<div class='actions'>");
        out.println("<a class='back-link' href='" + request.getContextPath() + "/content?id=" + contentId + "'>← Back to Content</a>");
        out.println("<button class='submit-btn' type='submit'>Submit Review</button>");
        out.println("</div>");

        out.println("</form>");

        out.println("</section>");
        out.println("</main>");

        out.println("</body>");
        out.println("</html>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/views/login.html");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        String contentIdValue = request.getParameter("content_id");
        String ratingValue = request.getParameter("rating");
        String reviewText = request.getParameter("review_text");

        if (contentIdValue == null || ratingValue == null || reviewText == null ||
                contentIdValue.isBlank() || ratingValue.isBlank() || reviewText.isBlank()) {

            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        int contentId;
        int rating;

        try {
            contentId = Integer.parseInt(contentIdValue);
            rating = Integer.parseInt(ratingValue);
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        if (rating < 1 || rating > 10) {
            response.sendRedirect(request.getContextPath() + "/review?content_id=" + contentId);
            return;
        }

        String insertSql =
                "INSERT INTO reviews_ratings " +
                        "(user_id, content_id, rating, review_text, posted_at, helpful_votes) " +
                        "VALUES (?, ?, ?, ?, NOW(), 0)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            ps.setInt(1, userId);
            ps.setInt(2, contentId);
            ps.setInt(3, rating);
            ps.setString(4, reviewText);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/review?content_id=" + contentId);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/content?id=" + contentId);
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