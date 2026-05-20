package com.streamvault.servlets;

import com.streamvault.db.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.*;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        /*
         * Clear old checkout session data.
         * This prevents an old pending checkout from appearing
         * when a registration attempt fails or uses an existing email.
         */
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.removeAttribute("pendingUserId");
            existingSession.removeAttribute("pendingPaymentId");
        }

        String fullName = request.getParameter("full_name");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String dateOfBirth = request.getParameter("date_of_birth");
        String country = request.getParameter("country");
        String planIdValue = request.getParameter("plan_id");

        if (fullName == null || email == null || password == null ||
                dateOfBirth == null || country == null || planIdValue == null ||
                fullName.isBlank() || email.isBlank() || password.isBlank() ||
                dateOfBirth.isBlank() || country.isBlank() || planIdValue.isBlank()) {

            response.sendRedirect(request.getContextPath() + "/views/register.html?error=missing");
            return;
        }

        if (password.length() < 6) {
            response.sendRedirect(request.getContextPath() + "/views/register.html?error=password");
            return;
        }

        int planId;

        try {
            planId = Integer.parseInt(planIdValue);
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/views/register.html?error=plan");
            return;
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        String checkEmailSql =
                "SELECT user_id FROM users WHERE email = ?";

        String insertUserSql =
                "INSERT INTO users " +
                        "(full_name, email, password_hash, date_of_birth, country, join_date, is_active, role) " +
                        "VALUES (?, ?, ?, ?, ?, CURDATE(), TRUE, 'viewer')";

        String insertSubscriptionSql =
                "INSERT INTO subscriptions " +
                        "(user_id, plan_id, start_date, end_date, auto_renew, status) " +
                        "VALUES (?, ?, CURDATE(), NULL, TRUE, 'Active')";

        String insertPaymentSql =
                "INSERT INTO payments " +
                        "(subscription_id, amount, currency, method, payment_date, status) " +
                        "SELECT ?, monthly_price, 'AED', 'Card', NOW(), 'Pending' " +
                        "FROM subscription_plans WHERE plan_id = ?";

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try (
                    PreparedStatement checkStatement =
                            connection.prepareStatement(checkEmailSql);

                    PreparedStatement userStatement =
                            connection.prepareStatement(
                                    insertUserSql,
                                    Statement.RETURN_GENERATED_KEYS
                            );

                    PreparedStatement subscriptionStatement =
                            connection.prepareStatement(
                                    insertSubscriptionSql,
                                    Statement.RETURN_GENERATED_KEYS
                            )
            ) {

                checkStatement.setString(1, email);

                try (ResultSet resultSet = checkStatement.executeQuery()) {
                    if (resultSet.next()) {
                        connection.rollback();
                        response.sendRedirect(request.getContextPath() + "/views/register.html?error=exists");
                        return;
                    }
                }

                userStatement.setString(1, fullName);
                userStatement.setString(2, email);
                userStatement.setString(3, passwordHash);
                userStatement.setString(4, dateOfBirth);
                userStatement.setString(5, country);

                userStatement.executeUpdate();

                int newUserId;

                try (ResultSet generatedUserKeys = userStatement.getGeneratedKeys()) {
                    if (generatedUserKeys.next()) {
                        newUserId = generatedUserKeys.getInt(1);
                    } else {
                        connection.rollback();
                        response.sendRedirect(request.getContextPath() + "/views/register.html?error=user");
                        return;
                    }
                }

                subscriptionStatement.setInt(1, newUserId);
                subscriptionStatement.setInt(2, planId);
                subscriptionStatement.executeUpdate();

                int newSubscriptionId;

                try (ResultSet generatedSubKeys = subscriptionStatement.getGeneratedKeys()) {
                    if (generatedSubKeys.next()) {
                        newSubscriptionId = generatedSubKeys.getInt(1);
                    } else {
                        connection.rollback();
                        response.sendRedirect(request.getContextPath() + "/views/register.html?error=subscription");
                        return;
                    }
                }

                int newPaymentId;

                try (PreparedStatement paymentStatement =
                             connection.prepareStatement(
                                     insertPaymentSql,
                                     Statement.RETURN_GENERATED_KEYS
                             )) {

                    paymentStatement.setInt(1, newSubscriptionId);
                    paymentStatement.setInt(2, planId);

                    paymentStatement.executeUpdate();

                    try (ResultSet generatedPaymentKeys = paymentStatement.getGeneratedKeys()) {
                        if (generatedPaymentKeys.next()) {
                            newPaymentId = generatedPaymentKeys.getInt(1);
                        } else {
                            connection.rollback();
                            response.sendRedirect(request.getContextPath() + "/views/register.html?error=payment");
                            return;
                        }
                    }
                }

                connection.commit();

                /*
                 * Store temporary checkout data.
                 * User is not fully logged in yet.
                 * CheckoutServlet will complete payment and then create normal login session.
                 */
                HttpSession checkoutSession = request.getSession(true);
                checkoutSession.setAttribute("pendingUserId", newUserId);
                checkoutSession.setAttribute("pendingPaymentId", newPaymentId);

                response.sendRedirect(request.getContextPath() + "/checkout");

            } catch (Exception e) {
                connection.rollback();
                e.printStackTrace();
                response.sendRedirect(request.getContextPath() + "/views/register.html?error=server");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/views/register.html?error=db");
        }
    }
}