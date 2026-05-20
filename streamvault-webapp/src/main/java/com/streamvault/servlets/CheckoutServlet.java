package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("pendingUserId") == null ||
                session.getAttribute("pendingPaymentId") == null) {

            response.sendRedirect(request.getContextPath() + "/views/register.html");
            return;
        }

        int userId = (int) session.getAttribute("pendingUserId");
        int paymentId = (int) session.getAttribute("pendingPaymentId");

        String sql =
                "SELECT u.full_name, u.email, sp.plan_name, sp.monthly_price, " +
                        "sp.resolution_limit, sp.max_streams, p.amount, p.currency, p.status " +
                        "FROM payments p " +
                        "JOIN subscriptions s ON p.subscription_id = s.subscription_id " +
                        "JOIN users u ON s.user_id = u.user_id " +
                        "JOIN subscription_plans sp ON s.plan_id = sp.plan_id " +
                        "WHERE p.payment_id = ? AND u.user_id = ?";

        response.setContentType("text/html");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, paymentId);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    response.sendRedirect(request.getContextPath() + "/views/register.html?error=checkout");
                    return;
                }

                String fullName = rs.getString("full_name");
                String email = rs.getString("email");
                String planName = rs.getString("plan_name");
                String resolution = rs.getString("resolution_limit");
                int streams = rs.getInt("max_streams");
                double amount = rs.getDouble("amount");
                String currency = rs.getString("currency");
                String status = rs.getString("status");

                var out = response.getWriter();

                out.println("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <title>Checkout - StreamVault</title>
                            <style>
                                * {
                                    box-sizing: border-box;
                                }

                                body {
                                    margin: 0;
                                    font-family: Arial, sans-serif;
                                    background: #141414;
                                    color: #f5f5f5;
                                }

                                .top-bar {
                                    height: 72px;
                                    background: linear-gradient(to bottom, #000, #141414);
                                    border-bottom: 1px solid #222;
                                    display: flex;
                                    align-items: center;
                                    justify-content: space-between;
                                    padding: 0 50px;
                                }

                                .logo {
                                    color: #e50914;
                                    font-size: 31px;
                                    font-weight: 900;
                                    letter-spacing: 2px;
                                }

                                .help {
                                    color: #aaa;
                                    font-size: 14px;
                                }

                                .page-title {
                                    text-align: center;
                                    font-size: 38px;
                                    font-weight: 900;
                                    letter-spacing: 4px;
                                    margin: 38px 0 28px;
                                    color: #fff;
                                }

                                .checkout-wrapper {
                                    max-width: 1180px;
                                    margin: 0 auto 70px;
                                    padding: 0 30px;
                                    display: grid;
                                    grid-template-columns: 2fr 360px;
                                    gap: 28px;
                                }

                                .left-column {
                                    display: flex;
                                    flex-direction: column;
                                    gap: 24px;
                                }

                                .section-box {
                                    background: #181818;
                                    border: 1px solid #2d2d2d;
                                    box-shadow: 0 15px 35px rgba(0,0,0,0.45);
                                }

                                .section-title {
                                    background: #050505;
                                    color: white;
                                    padding: 18px 24px;
                                    font-weight: 900;
                                    font-size: 17px;
                                    letter-spacing: 1px;
                                    border-left: 5px solid #e50914;
                                }

                                .section-body {
                                    padding: 24px;
                                }

                                .two-col {
                                    display: grid;
                                    grid-template-columns: 1fr 1fr;
                                    gap: 18px;
                                }

                                label {
                                    display: block;
                                    color: #ddd;
                                    font-size: 13px;
                                    font-weight: 700;
                                    margin-bottom: 8px;
                                }

                                input {
                                    width: 100%;
                                    padding: 14px;
                                    background: #2b2b2b;
                                    border: 1px solid #444;
                                    color: white;
                                    margin-bottom: 18px;
                                    font-size: 14px;
                                    border-radius: 4px;
                                }

                                input:focus {
                                    outline: none;
                                    border-color: #e50914;
                                    background: #333;
                                }

                                .tabs {
                                    display: grid;
                                    grid-template-columns: 1fr 1fr;
                                    border: 1px solid #333;
                                    margin-bottom: 24px;
                                    background: #111;
                                }

                                .tab {
                                    text-align: center;
                                    padding: 16px;
                                    font-size: 13px;
                                    font-weight: 900;
                                    color: #777;
                                    letter-spacing: 1px;
                                }

                                .tab.active {
                                    color: white;
                                    border-bottom: 3px solid #e50914;
                                }

                                .pay-btn {
                                    width: 100%;
                                    background: #e50914;
                                    color: white;
                                    border: none;
                                    padding: 16px;
                                    font-size: 14px;
                                    font-weight: 900;
                                    border-radius: 5px;
                                    cursor: pointer;
                                    letter-spacing: 0.5px;
                                }

                                .pay-btn:hover {
                                    background: #b20710;
                                }

                                .summary-box {
                                    background: #181818;
                                    border: 1px solid #2d2d2d;
                                    height: fit-content;
                                    padding: 24px;
                                    box-shadow: 0 15px 35px rgba(0,0,0,0.45);
                                }

                                .summary-title {
                                    background: #050505;
                                    color: white;
                                    margin: -24px -24px 22px;
                                    padding: 18px 24px;
                                    font-size: 17px;
                                    font-weight: 900;
                                    letter-spacing: 1px;
                                    border-left: 5px solid #e50914;
                                }

                                .plan-card {
                                    display: flex;
                                    gap: 14px;
                                    padding-bottom: 22px;
                                    border-bottom: 1px solid #333;
                                    margin-bottom: 20px;
                                }

                                .plan-icon {
                                    width: 72px;
                                    height: 72px;
                                    background: linear-gradient(135deg, #e50914, #050505);
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    color: white;
                                    font-weight: 900;
                                    border-radius: 8px;
                                    box-shadow: 0 0 20px rgba(229,9,20,0.35);
                                }

                                .plan-card h3 {
                                    margin: 0 0 8px;
                                    font-size: 18px;
                                    color: white;
                                }

                                .plan-card p {
                                    margin: 4px 0;
                                    color: #bbb;
                                    font-size: 13px;
                                }

                                .summary-row {
                                    display: flex;
                                    justify-content: space-between;
                                    margin: 13px 0;
                                    color: #ccc;
                                    font-size: 14px;
                                }

                                .pending {
                                    color: #e50914;
                                    font-weight: 900;
                                }

                                .summary-total {
                                    display: flex;
                                    justify-content: space-between;
                                    border-top: 1px solid #333;
                                    padding-top: 18px;
                                    margin-top: 18px;
                                    color: white;
                                    font-size: 19px;
                                    font-weight: 900;
                                }

                                .note {
                                    margin-top: 24px;
                                    background: #111;
                                    border-left: 4px solid #e50914;
                                    padding: 14px;
                                    color: #aaa;
                                    font-size: 13px;
                                    line-height: 1.5;
                                }

                                @media (max-width: 900px) {
                                    .checkout-wrapper {
                                        grid-template-columns: 1fr;
                                    }

                                    .two-col {
                                        grid-template-columns: 1fr;
                                    }

                                    .top-bar {
                                        padding: 0 22px;
                                    }
                                }
                            </style>
                        </head>
                        <body>
                        """);

                out.println("<div class='top-bar'>");
                out.println("<div class='logo'>STREAMVAULT</div>");
                out.println("<div class='help'>Need help? 1-800-STREAM</div>");
                out.println("</div>");

                out.println("<div class='page-title'>CHECKOUT</div>");

                out.println("<div class='checkout-wrapper'>");

                out.println("<div class='left-column'>");

                out.println("<div class='section-box'>");
                out.println("<div class='section-title'>1. ACCOUNT DETAILS</div>");
                out.println("<div class='section-body two-col'>");

                out.println("<div><label>Full Name</label><input type='text' value='" + escape(fullName) + "' readonly></div>");
                out.println("<div><label>Email</label><input type='text' value='" + escape(email) + "' readonly></div>");
                out.println("<div><label>Selected Plan</label><input type='text' value='" + escape(planName) + "' readonly></div>");
                out.println("<div><label>Account Status</label><input type='text' value='Ready for activation' readonly></div>");

                out.println("</div>");
                out.println("</div>");

                out.println("<div class='section-box'>");
                out.println("<div class='section-title'>2. PAYMENT</div>");
                out.println("<div class='section-body'>");

                out.println("<form action='" + request.getContextPath() + "/checkout' method='post'>");

                out.println("<div class='tabs'>");
                out.println("<div class='tab active'>CARD</div>");
                out.println("<div class='tab'>SIMULATED PAYMENT</div>");
                out.println("</div>");

                out.println("<label>Card Number</label>");
                out.println("<input type='text' value='4242 4242 4242 4242' required>");

                out.println("<div class='two-col'>");
                out.println("<div><label>Expiry Date</label><input type='text' value='12/28' required></div>");
                out.println("<div><label>CVV</label><input type='password' value='123' required></div>");
                out.println("</div>");

                out.println("<label>Name on Card</label>");
                out.println("<input type='text' value='" + escape(fullName) + "' required>");

                out.println("<button class='pay-btn' type='submit'>PAY SUCCESSFULLY & START WATCHING</button>");
                out.println("</form>");

                out.println("</div>");
                out.println("</div>");

                out.println("</div>");

                out.println("<div class='summary-box'>");
                out.println("<div class='summary-title'>IN YOUR PLAN</div>");

                out.println("<div class='plan-card'>");
                out.println("<div class='plan-icon'>SV</div>");
                out.println("<div>");
                out.println("<h3>" + escape(planName) + " Plan</h3>");
                out.println("<p>" + escape(resolution) + " streaming</p>");
                out.println("<p>" + streams + " simultaneous stream(s)</p>");
                out.println("</div>");
                out.println("</div>");

                out.println("<div class='summary-row'><span>Subtotal</span><span>" + amount + " " + escape(currency) + "</span></div>");
                out.println("<div class='summary-row'><span>Estimated Tax</span><span>0.00 " + escape(currency) + "</span></div>");
                out.println("<div class='summary-row'><span>Payment Status</span><span class='pending'>" + escape(status) + "</span></div>");

                out.println("<div class='summary-total'><span>TOTAL</span><span>" + amount + " " + escape(currency) + "</span></div>");

                out.println("<div class='note'>This simulated checkout updates the payment from Pending to Completed and automatically logs in the new StreamVault user.</div>");

                out.println("</div>");
                out.println("</div>");
                out.println("</body></html>");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().println("Checkout error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("pendingUserId") == null ||
                session.getAttribute("pendingPaymentId") == null) {

            response.sendRedirect(request.getContextPath() + "/views/register.html");
            return;
        }

        int userId = (int) session.getAttribute("pendingUserId");
        int paymentId = (int) session.getAttribute("pendingPaymentId");

        String updatePaymentSql =
                "UPDATE payments SET status = 'Completed', payment_date = NOW() WHERE payment_id = ?";

        String userSql =
                "SELECT user_id, full_name, email, role FROM users WHERE user_id = ?";

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try (
                    PreparedStatement updatePaymentStatement =
                            connection.prepareStatement(updatePaymentSql);

                    PreparedStatement userStatement =
                            connection.prepareStatement(userSql)
            ) {

                updatePaymentStatement.setInt(1, paymentId);
                updatePaymentStatement.executeUpdate();

                userStatement.setInt(1, userId);

                try (ResultSet rs = userStatement.executeQuery()) {

                    if (!rs.next()) {
                        connection.rollback();
                        response.sendRedirect(request.getContextPath() + "/views/login.html?error=notfound");
                        return;
                    }

                    connection.commit();

                    session.removeAttribute("pendingUserId");
                    session.removeAttribute("pendingPaymentId");

                    session.setAttribute("userId", rs.getInt("user_id"));
                    session.setAttribute("fullName", rs.getString("full_name"));
                    session.setAttribute("email", rs.getString("email"));
                    session.setAttribute("role", rs.getString("role"));

                    response.sendRedirect(request.getContextPath() + "/home");
                }

            } catch (Exception e) {
                connection.rollback();
                e.printStackTrace();
                response.sendRedirect(request.getContextPath() + "/checkout?error=payment");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/checkout?error=db");
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