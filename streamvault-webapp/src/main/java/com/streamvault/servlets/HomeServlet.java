package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import com.streamvault.mongo.MongoAnalyticsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

@WebServlet({"/home", "/StreamVault Home"})
public class HomeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/views/login.html");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        String search = request.getParameter("search");
        String type = request.getParameter("type");
        String genre = request.getParameter("genre");
        String sort = request.getParameter("sort");
        String section = request.getParameter("section");

        if ((search != null && !search.isBlank()) ||
                (type != null && !type.isBlank()) ||
                (genre != null && !genre.isBlank()) ||
                (sort != null && !sort.isBlank())) {

            MongoAnalyticsService.logSearchEvent(userId, search, type, genre, sort);
        }

        response.setContentType("text/html");
        var out = response.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'>");

        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<title>StreamVault Home</title>");
        out.println("<link rel='stylesheet' href='" + request.getContextPath() + "/static/css/style.css'>");

        /*
         * Premium header CSS is included here directly so the header works
         * even if style.css is cached or not refreshed by Tomcat.
         */
        out.println("<style>");
        out.println("""
                .sv-header-wrap {
                    width: 100%;
                    padding: 22px 0 0;
                    position: sticky;
                    top: 0;
                    z-index: 2000;
                    background: linear-gradient(to bottom, rgba(0, 0, 0, 0.85), rgba(20, 20, 20, 0));
                }

                .sv-navbar-premium {
                    width: calc(100% - 90px);
                    height: 76px;
                    margin: 0 auto;
                    padding: 0 34px;
                    background: rgba(15, 18, 24, 0.96);
                    border-radius: 14px;
                    display: flex;
                    align-items: center;
                    gap: 34px;
                    box-shadow: 0 18px 45px rgba(0, 0, 0, 0.45);
                }

                .sv-logo-premium {
                    font-size: 30px;
                    font-weight: 900;
                    color: #e50914;
                    letter-spacing: 1px;
                    white-space: nowrap;
                }

                .sv-left-links {
                    display: flex;
                    gap: 22px;
                    align-items: center;
                }

                .sv-left-links a {
                    color: #dcdcdc;
                    font-size: 16px;
                    font-weight: 700;
                    text-decoration: none;
                    transition: 0.25s ease;
                }

                .sv-left-links a:hover {
                    color: #e50914;
                }

                .sv-right-links {
                    margin-left: auto;
                    display: flex;
                    gap: 28px;
                    align-items: center;
                }

                .sv-right-links a {
                    color: #f1f1f1;
                    font-size: 16px;
                    font-weight: 800;
                    text-decoration: none;
                    transition: 0.25s ease;
                }

                .sv-right-links a:hover {
                    color: #e50914;
                }

                @media (max-width: 900px) {
                    .sv-navbar-premium {
                        width: 94%;
                        height: auto;
                        min-height: 76px;
                        padding: 18px;
                        flex-direction: column;
                        gap: 14px;
                    }

                    .sv-left-links,
                    .sv-right-links {
                        margin-left: 0;
                        flex-wrap: wrap;
                        justify-content: center;
                    }

                    .sv-logo-premium {
                        font-size: 26px;
                    }
                }
                """);
        out.println("</style>");

        out.println("</head>");
        out.println("<body>");

        printHeader(out, request, session);

        out.println("<div class='hero-banner'>");
        out.println("<div>");
        out.println("<h1>StreamVault</h1>");
        out.println("<p>Browse movies and series from your StreamVault catalog.</p>");
        out.println("</div>");
        out.println("</div>");

        out.println("<div class='container'>");

        printSearchBar(out, request, search, type, genre, sort);

        try (Connection conn = DatabaseConnection.getConnection()) {

            if (hasAnyFilter(search, type, genre, sort, section)) {
                out.println("<h2>Browse Results</h2>");
                printFilteredCarousel(conn, out, request, search, type, genre, sort, section);
            } else {
                out.println("<h2>Popular on StreamVault</h2>");
                printCarousel(conn, out, request, "Popular on StreamVault", null,
                        "ORDER BY ci.release_year DESC, ci.title ASC", 12);

                out.println("<h2 id='movies'>Movies</h2>");
                printCarousel(conn, out, request, "Movies", "Movie",
                        "ORDER BY ci.title ASC", 12);

                out.println("<h2 id='series'>TV Shows</h2>");
                printCarousel(conn, out, request, "TV Shows", "Series",
                        "ORDER BY ci.title ASC", 12);
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("<p>Error loading home page: " + escape(e.getMessage()) + "</p>");
        }

        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }

    private void printHeader(java.io.PrintWriter out, HttpServletRequest request, HttpSession session) {
        out.println("<div class='sv-header-wrap'>");
        out.println("<nav class='sv-navbar-premium'>");

        out.println("<div class='sv-logo-premium'>STREAMVAULT</div>");

        out.println("<div class='sv-left-links'>");
        out.println("<a href='" + request.getContextPath() + "/home?section=movies'>Movies</a>");
        out.println("<a href='" + request.getContextPath() + "/home?section=series'>TV Shows</a>");
        out.println("</div>");

        out.println("<div class='sv-right-links'>");
        out.println("<a href='" + request.getContextPath() + "/home'>Home</a>");
        out.println("<a href='" + request.getContextPath() + "/dashboard'>Dashboard</a>");

        String role = (String) session.getAttribute("role");
        if ("admin".equalsIgnoreCase(role)) {
            out.println("<a href='" + request.getContextPath() + "/admin'>Admin</a>");
        }

        out.println("<a href='" + request.getContextPath() + "/logout'>Logout</a>");
        out.println("</div>");

        out.println("</nav>");
        out.println("</div>");
    }

    private void printSearchBar(java.io.PrintWriter out, HttpServletRequest request,
                                String search, String type, String genre, String sort) {

        out.println("<form class='search-bar' action='" + request.getContextPath() + "/home' method='get'>");

        out.println("<input type='text' name='search' placeholder='Search titles...' value='" + escapeAttr(search) + "'>");

        out.println("<select name='type'>");
        out.println("<option value=''>All Types</option>");
        out.println("<option value='Movie' " + selected(type, "Movie") + ">Movies</option>");
        out.println("<option value='Series' " + selected(type, "Series") + ">TV Shows</option>");
        out.println("</select>");

        out.println("<select name='genre'>");
        out.println("<option value=''>All Genres</option>");
        out.println("<option value='Action' " + selected(genre, "Action") + ">Action</option>");
        out.println("<option value='Adventure' " + selected(genre, "Adventure") + ">Adventure</option>");
        out.println("<option value='Comedy' " + selected(genre, "Comedy") + ">Comedy</option>");
        out.println("<option value='Drama' " + selected(genre, "Drama") + ">Drama</option>");
        out.println("<option value='Fantasy' " + selected(genre, "Fantasy") + ">Fantasy</option>");
        out.println("<option value='Horror' " + selected(genre, "Horror") + ">Horror</option>");
        out.println("<option value='Sci-Fi' " + selected(genre, "Sci-Fi") + ">Sci-Fi</option>");
        out.println("<option value='Thriller' " + selected(genre, "Thriller") + ">Thriller</option>");
        out.println("</select>");

        out.println("<select name='sort'>");
        out.println("<option value=''>Sort By</option>");
        out.println("<option value='az' " + selected(sort, "az") + ">A-Z</option>");
        out.println("<option value='za' " + selected(sort, "za") + ">Z-A</option>");
        out.println("<option value='newest' " + selected(sort, "newest") + ">Newest</option>");
        out.println("<option value='oldest' " + selected(sort, "oldest") + ">Oldest</option>");
        out.println("<option value='genre' " + selected(sort, "genre") + ">Genre</option>");
        out.println("</select>");

        out.println("<button type='submit'>Search</button>");
        out.println("</form>");
    }

    private void printFilteredCarousel(Connection conn, java.io.PrintWriter out, HttpServletRequest request,
                                       String search, String type, String genre, String sort, String section)
            throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT ci.content_id, ci.title, ci.type, ci.release_year, ci.language, ci.age_rating, " +
                        "COALESCE(GROUP_CONCAT(DISTINCT g.name ORDER BY g.name SEPARATOR ', '), '') AS genres " +
                        "FROM content_items ci " +
                        "LEFT JOIN content_genre cg ON ci.content_id = cg.content_id " +
                        "LEFT JOIN genres g ON cg.genre_id = g.genre_id " +
                        "WHERE 1=1 "
        );

        if (search != null && !search.isBlank()) {
            sql.append("AND ci.title LIKE ? ");
        }

        if (type != null && !type.isBlank()) {
            sql.append("AND ci.type = ? ");
        }

        if (section != null && section.equalsIgnoreCase("movies")) {
            sql.append("AND ci.type = 'Movie' ");
        }

        if (section != null && section.equalsIgnoreCase("series")) {
            sql.append("AND ci.type = 'Series' ");
        }

        if (genre != null && !genre.isBlank()) {
            sql.append("AND g.name = ? ");
        }

        sql.append("GROUP BY ci.content_id, ci.title, ci.type, ci.release_year, ci.language, ci.age_rating ");
        sql.append(getOrderBy(sort));

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;

            if (search != null && !search.isBlank()) {
                ps.setString(index++, "%" + search + "%");
            }

            if (type != null && !type.isBlank()) {
                ps.setString(index++, type);
            }

            if (genre != null && !genre.isBlank()) {
                ps.setString(index++, genre);
            }

            try (ResultSet rs = ps.executeQuery()) {
                printCardsFromResultSet(out, request, rs);
            }
        }
    }

    private void printCarousel(Connection conn, java.io.PrintWriter out, HttpServletRequest request,
                               String title, String typeFilter, String orderBy, int limit)
            throws SQLException {

        String sql =
                "SELECT ci.content_id, ci.title, ci.type, ci.release_year, ci.language, ci.age_rating, " +
                        "COALESCE(GROUP_CONCAT(DISTINCT g.name ORDER BY g.name SEPARATOR ', '), '') AS genres " +
                        "FROM content_items ci " +
                        "LEFT JOIN content_genre cg ON ci.content_id = cg.content_id " +
                        "LEFT JOIN genres g ON cg.genre_id = g.genre_id " +
                        "WHERE (? IS NULL OR ci.type = ?) " +
                        "GROUP BY ci.content_id, ci.title, ci.type, ci.release_year, ci.language, ci.age_rating " +
                        orderBy + " " +
                        "LIMIT ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (typeFilter == null) {
                ps.setNull(1, Types.VARCHAR);
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(1, typeFilter);
                ps.setString(2, typeFilter);
            }

            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                printCardsFromResultSet(out, request, rs);
            }
        }
    }

    private void printCardsFromResultSet(java.io.PrintWriter out, HttpServletRequest request, ResultSet rs)
            throws SQLException {

        boolean hasResults = false;

        out.println("<div class='horizontal-row'>");

        while (rs.next()) {
            hasResults = true;

            int contentId = rs.getInt("content_id");
            String title = rs.getString("title");
            String type = rs.getString("type");
            int releaseYear = rs.getInt("release_year");
            String language = rs.getString("language");
            String ageRating = rs.getString("age_rating");
            String posterUrl = getPosterUrl(title);

            out.println("<div class='title-card'>");

            out.println("<img class='poster-img' src='" + posterUrl + "' alt='" + escape(title) + " poster' " +
                    "onerror=\"this.onerror=null;this.src='https://image.tmdb.org/t/p/w500/8UlWHLMpgZm9bx6QYh0NFoq67TZ.jpg';\">");

            out.println("<div class='card-info'>");
            out.println("<h3>" + escape(title) + "</h3>");
            out.println("<p>" + escape(type) + " • " + releaseYear + "</p>");
            out.println("<p>" + escape(language) + " • " + escape(ageRating) + "</p>");
            out.println("<a class='watch-link' href='" + request.getContextPath() + "/content?id=" + contentId + "'>View Details</a>");
            out.println("</div>");

            out.println("</div>");
        }

        out.println("</div>");

        if (!hasResults) {
            out.println("<p>No titles found.</p>");
        }
    }

    private String getOrderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return "ORDER BY ci.release_year DESC, ci.title ASC";
        }

        switch (sort.toLowerCase()) {
            case "az":
                return "ORDER BY ci.title ASC";
            case "za":
                return "ORDER BY ci.title DESC";
            case "newest":
                return "ORDER BY ci.release_year DESC";
            case "oldest":
                return "ORDER BY ci.release_year ASC";
            case "genre":
                return "ORDER BY genres ASC, ci.title ASC";
            default:
                return "ORDER BY ci.release_year DESC, ci.title ASC";
        }
    }

    private boolean hasAnyFilter(String search, String type, String genre, String sort, String section) {
        return (search != null && !search.isBlank()) ||
                (type != null && !type.isBlank()) ||
                (genre != null && !genre.isBlank()) ||
                (sort != null && !sort.isBlank()) ||
                (section != null && !section.isBlank());
    }

    private String selected(String current, String value) {
        if (current != null && current.equalsIgnoreCase(value)) {
            return "selected";
        }

        return "";
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

    private String escapeAttr(String value) {
        return escape(value);
    }
}