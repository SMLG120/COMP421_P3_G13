package P3;

import java.sql.*;

public class StockDatabaseApp {
    private Connection conn;
    public StockDatabaseApp() {
        try {
            // Replace with your DB2 database credentials
            String url = "jdbc:db2://winter2025-comp421.cs.mcgill.ca:50000/comp421"; // Double check if it's the right link!!!!
            String user = "cs421g13"; // Not sure
            String password = "COMP421team"; // Also not sure
        
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to DB2 successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void viewUserPortfolio(String userEmail) {
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

            while (rs.next()) {
                System.out.printf("Portfolio: %s | Stock: %s | Share ID: %d | Price: %.2f\n",
                        rs.getString("portfolioName"),
                        rs.getString("tickerSymbol"),
                        rs.getInt("shareID"),
                        rs.getDouble("currentPrice"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void buyShares(String userEmail, String tickerSymbol, int shareID, double amount, int portfolioID) {
        String checkBalance = "SELECT balance FROM User WHERE email = ?";
        String getMaxTransactionID = "SELECT COALESCE(MAX(transactionID), 0) + 1 FROM InvestmentTransactions";
        String validatePortfolio = "SELECT portfolioID FROM Account WHERE portfolioID = ? AND \"USER\" = ?";

        String insertTransaction = "INSERT INTO InvestmentTransactions "
                + "(transactionID, transactionDate, currency, amount, status, transactionOperationType, BrokerageFee, ShareTickerSymbol, ShareID, User) "
                + "VALUES (?, CURRENT_DATE, 'USD', ?, 'Completed', 'Buy', 10.00, ?, ?, ?)";
        String insertAccountShare = "INSERT INTO AccountAndShares (shareID, tickerSymbol, portfolioID) VALUES (?, ?, ?)";

        try (PreparedStatement balanceStmt = conn.prepareStatement(checkBalance);
             PreparedStatement maxIdStmt = conn.prepareStatement(getMaxTransactionID);
             PreparedStatement validatePortfolioStmt = conn.prepareStatement(validatePortfolio);
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


    public void viewStockTransactionHistory(String tickerSymbol) {
        String sql = """
        SELECT transactionID, transactionDate, amount, status, transactionOperationType, User 
        FROM InvestmentTransactions WHERE ShareTickerSymbol = ?
        ORDER BY transactionDate DESC;
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tickerSymbol);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                System.out.printf("Transaction ID: %d | Date: %s | Amount: %.2f | Type: %s | User: %s\n",
                        rs.getInt("transactionID"),
                        rs.getDate("transactionDate"),
                        rs.getDouble("amount"),
                        rs.getString("transactionOperationType"),
                        rs.getString("User"));
            }
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
