package P3;

import java.sql.*;

public class StockDatabaseApp {
    private Connection conn;
    public StockDatabaseApp() {
        try {
            //DB2 database credentials
            String url = "jdbc:db2://winter2025-comp421.cs.mcgill.ca:50000/comp421"; // Double check if it's the right link!!!!
            String user = "cs421g13";
            String password = "COMP421team";
        
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to DB2 successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void viewUserPortfolio(String userEmail) {
        // First, check if the user exists in the Account table
        String checkUserSql = "SELECT * FROM Account WHERE \"USER\" = ? LIMIT 1";

        try (PreparedStatement checkUserStmt = conn.prepareStatement(checkUserSql)) {
            checkUserStmt.setString(1, userEmail);
            ResultSet userRs = checkUserStmt.executeQuery();

            if (!userRs.next()) {
                // User not found
                System.out.println("User with email " + userEmail + " is not associated with any portfolio.");
                return; // Exit the method if user is not found
            }

            // Now that we know the user exists, proceed with fetching the portfolio details
            String sql = """
        SELECT a.portfolioName, s.tickerSymbol, s.shareID, s.currentPrice 
        FROM AccountAndShares aas
        JOIN Shares s ON aas.shareID = s.shareID AND aas.tickerSymbol = s.tickerSymbol
        JOIN Account a ON aas.portfolioID = a.portfolioID
        WHERE a.User = ?;
        """;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userEmail);
                ResultSet rs = pstmt.executeQuery();

                // Check if any rows were returned
                boolean hasResults = false;

                while (rs.next()) {
                    hasResults = true; // At least one row exists
                    System.out.printf("Portfolio: %s | Stock: %s | Share ID: %d | Price: %.2f\n",
                            rs.getString("portfolioName"),
                            rs.getString("tickerSymbol"),
                            rs.getInt("shareID"),
                            rs.getDouble("currentPrice"));
                }

                // If no results, print a message
                if (!hasResults) {
                    System.out.println("No shares found for the user: " + userEmail);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public void buyShares(String userEmail, String tickerSymbol, int shareID, double amount, int portfolioID) {
        String checkBalance = "SELECT balance FROM User WHERE email = ?";
        String getMaxTransactionID = "SELECT COALESCE(MAX(transactionID), 0) + 1 FROM InvestmentTransactions";
        String validatePortfolio = "SELECT portfolioID FROM Account WHERE portfolioID = ? AND \"USER\" = ?";

        // Check if the share already exists in AccountAndShares
        String checkShareExists = "SELECT 1 FROM AccountAndShares WHERE shareID = ? AND tickerSymbol = ?";

        String insertTransaction = "INSERT INTO InvestmentTransactions "
                + "(transactionID, transactionDate, currency, amount, status, transactionOperationType, BrokerageFee, ShareTickerSymbol, ShareID, User) "
                + "VALUES (?, CURRENT_DATE, 'USD', ?, 'Completed', 'Buy', 10.00, ?, ?, ?)";

        String insertAccountShare = "INSERT INTO AccountAndShares (shareID, tickerSymbol, portfolioID) VALUES (?, ?, ?)";

        try (PreparedStatement balanceStmt = conn.prepareStatement(checkBalance);
             PreparedStatement maxIdStmt = conn.prepareStatement(getMaxTransactionID);
             PreparedStatement validatePortfolioStmt = conn.prepareStatement(validatePortfolio);
             PreparedStatement checkShareStmt = conn.prepareStatement(checkShareExists);
             PreparedStatement insertTransactionStmt = conn.prepareStatement(insertTransaction);
             PreparedStatement insertAccountShareStmt = conn.prepareStatement(insertAccountShare)) {

            // Check if the user has sufficient balance
            balanceStmt.setString(1, userEmail);
            ResultSet balanceResult = balanceStmt.executeQuery();

            if (balanceResult.next() && balanceResult.getDouble("balance") >= amount) {

                // Check if the portfolioID is valid for this user
                validatePortfolioStmt.setInt(1, portfolioID);
                validatePortfolioStmt.setString(2, userEmail);
                ResultSet portfolioResult = validatePortfolioStmt.executeQuery();

                if (!portfolioResult.next()) {
                    System.out.println("Invalid portfolio ID for the given user.");
                    return;
                }

                // Check if the share already exists in AccountAndShares
                checkShareStmt.setInt(1, shareID);
                checkShareStmt.setString(2, tickerSymbol);
                ResultSet shareExistsResult = checkShareStmt.executeQuery();

                if (shareExistsResult.next()) {
                    System.out.println("This share is already owned by someone.");
                    return; // Do not proceed with the transaction if the share already exists.
                }

                // Get new transaction ID
                ResultSet maxIdResult = maxIdStmt.executeQuery();
                int newTransactionID = 1;
                if (maxIdResult.next()) {
                    newTransactionID = maxIdResult.getInt(1);
                }

                // Insert investment transaction
                insertTransactionStmt.setInt(1, newTransactionID);
                insertTransactionStmt.setDouble(2, amount);
                insertTransactionStmt.setString(3, tickerSymbol);
                insertTransactionStmt.setInt(4, shareID);
                insertTransactionStmt.setString(5, userEmail);
                int rowsAffected = insertTransactionStmt.executeUpdate();

                if (rowsAffected > 0) {
                    // Insert into AccountAndShares table
                    insertAccountShareStmt.setInt(1, shareID);
                    insertAccountShareStmt.setString(2, tickerSymbol);
                    insertAccountShareStmt.setInt(3, portfolioID);
                    insertAccountShareStmt.executeUpdate();

                    System.out.println("Transaction successful! Transaction ID: " + newTransactionID);
                }
            } else {
                System.out.println("Insufficient balance.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





    public void sellShares(String userEmail, String tickerSymbol, int shareID, double amount) {
        String checkOwnership = """
        SELECT COUNT(*) AS owned FROM AccountAndShares aas
        JOIN Account a ON aas.portfolioID = a.portfolioID
        WHERE a.User = ? AND aas.tickerSymbol = ? AND aas.shareID = ?;
    """;
        String insertTransaction = """
        INSERT INTO InvestmentTransactions 
        (transactionDate, currency, amount, status, transactionOperationType, BrokerageFee, ShareTickerSymbol, ShareID, User)
        VALUES (CURRENT_DATE, 'USD', ?, 'Completed', 'Sell', 10.00, ?, ?, ?);
    """;

        try (PreparedStatement ownershipStmt = conn.prepareStatement(checkOwnership);
             PreparedStatement insertStmt = conn.prepareStatement(insertTransaction, Statement.RETURN_GENERATED_KEYS)) {

            ownershipStmt.setString(1, userEmail);
            ownershipStmt.setString(2, tickerSymbol);
            ownershipStmt.setInt(3, shareID);
            ResultSet rs = ownershipStmt.executeQuery();

            if (rs.next() && rs.getInt("owned") > 0) {
                insertStmt.setDouble(1, amount);
                insertStmt.setString(2, tickerSymbol);
                insertStmt.setInt(3, shareID);
                insertStmt.setString(4, userEmail);
                insertStmt.executeUpdate();

                // Get the auto-generated transactionID
                ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int transactionID = generatedKeys.getInt(1);
                    System.out.println("Shares sold successfully! Transaction ID: " + transactionID);
                } else {
                    System.out.println("Shares sold, but could not retrieve transaction ID.");
                }

            } else {
                System.out.println("Error: You do not own this stock.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void completePendingSellTransactions() {
        String checkPendingTransactionsSql = """
        SELECT COUNT(*) AS count FROM InvestmentTransactions
        WHERE status = 'Pending' AND transactionOperationType = 'Sell';
    """;

        String updateTransactionsSql = """
        UPDATE InvestmentTransactions
        SET status = 'Completed'
        WHERE status = 'Pending' AND transactionOperationType = 'Sell';
    """;

        String updateAccountTotalValueSql = """
        UPDATE Account
        SET totalValue = totalValue - COALESCE((
            SELECT SUM(amount) 
            FROM InvestmentTransactions 
            WHERE status = 'Completed' AND transactionOperationType = 'Sell' 
            AND Account.User = InvestmentTransactions.User
        ), 0)
        WHERE EXISTS (
            SELECT 1 FROM InvestmentTransactions 
            WHERE status = 'Completed' AND transactionOperationType = 'Sell' 
            AND Account.User = InvestmentTransactions.User
        );
    """;

        String deleteSharesSql = """
        DELETE FROM AccountAndShares
        WHERE shareID IN (
            SELECT ShareID FROM InvestmentTransactions
            WHERE status = 'Completed' AND transactionOperationType = 'Sell'
        );
    """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkPendingTransactionsSql)) {

            rs.next();
            int pendingTransactions = rs.getInt("count");

            if (pendingTransactions == 0) {
                System.out.println("No pending sell transactions to process.");
                return;
            }

            // Update transactions to "Completed"
            int updatedTransactions = stmt.executeUpdate(updateTransactionsSql);
            System.out.println(updatedTransactions + " pending sell transactions marked as completed.");

            // Deduct the sold amount from associated accounts
            int updatedAccounts = stmt.executeUpdate(updateAccountTotalValueSql);
            System.out.println(updatedAccounts + " accounts updated with reduced total value.");

            // Remove sold shares from AccountAndShares
            int deletedShares = stmt.executeUpdate(deleteSharesSql);
            System.out.println(deletedShares + " shares removed from AccountAndShares.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public void identifyAtRiskUsers() {
        String sql = """
        SELECT 
            u.email AS UserEmail,
            u.name AS UserName,
            (
                SELECT DISTINCT c1.sector
                FROM AccountAndShares aas1
                JOIN Shares s1 ON aas1.shareID = s1.shareID AND aas1.tickerSymbol = s1.tickerSymbol
                JOIN Stock st1 ON s1.tickerSymbol = st1.tickerSymbol
                JOIN Company c1 ON st1.Company = c1.CUSIP
                JOIN Account a1 ON aas1.portfolioID = a1.portfolioID
                WHERE a1.User = u.email
                LIMIT 1
            ) AS SingleSector,
            COUNT(DISTINCT aas.shareID) AS NumberOfShares,
            SUM(s.currentPrice) AS TotalInvestmentValue
        FROM 
            User u
            JOIN Account a ON u.email = a.User
            JOIN AccountAndShares aas ON a.portfolioID = aas.portfolioID
            JOIN Shares s ON aas.shareID = s.shareID AND aas.tickerSymbol = s.tickerSymbol
            JOIN Stock st ON s.tickerSymbol = st.tickerSymbol
            JOIN Company c ON st.Company = c.CUSIP
        GROUP BY 
            u.email, u.name
        HAVING 
            COUNT(DISTINCT c.sector) = 1
            AND COUNT(DISTINCT aas.shareID) > 0
        ORDER BY 
            TotalInvestmentValue DESC;
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            System.out.println("Users at Risk (Single-Sector Investment):\n");
            System.out.printf("%-30s %-20s %-15s %-10s %-15s\n",
                    "User Email", "User Name", "Sector", "Shares", "Total Investment");

            while (rs.next()) {
                System.out.printf("%-30s %-20s %-15s %-10d $%-15.2f\n",
                        rs.getString("UserEmail"),
                        rs.getString("UserName"),
                        rs.getString("SingleSector"),
                        rs.getInt("NumberOfShares"),
                        rs.getDouble("TotalInvestmentValue"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




}
