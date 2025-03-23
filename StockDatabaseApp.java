package P3;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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





    /**
     * Improves a user's watchlist by displaying current contents and providing recommendations
     * based on similar companies or industry sectors.
     */
    public void improveWatchlist(int portfolioID) {
        Scanner scanner = new Scanner(System.in);

        // First, check if the portfolio exists and get the associated watchlist
        String getWatchlistSql = """
        SELECT w.WID, w.watchlistName, w.notificationMode
        FROM Account a
        JOIN Watchlist w ON a.Watchlist = w.WID
        WHERE a.portfolioID = ?
    """;

        // Get current watchlist contents
        String getWatchlistContentsSql = """
        SELECT s.tickerSymbol, s.shareID, s.currentPrice, c.name AS CompanyName, c.sector
        FROM Watchlist w
        JOIN WatchlistAndShares was ON w.WID = was.WID
        JOIN Shares s ON was.shareID = s.shareID AND was.tickerSymbol = s.tickerSymbol
        JOIN Stock st ON s.tickerSymbol = st.tickerSymbol
        JOIN Company c ON st.Company = c.CUSIP
        WHERE w.WID = ?
    """;

        // Get similar company recommendations
        String getRecommendationsSql = """
        SELECT DISTINCT s.tickerSymbol, s.shareID, s.currentPrice, c.name AS CompanyName, c.sector,
               'Similar Sector' AS RecommendationType
        FROM Watchlist w
        JOIN WatchlistAndShares was ON w.WID = was.WID
        JOIN Shares ws ON was.shareID = ws.shareID AND was.tickerSymbol = ws.tickerSymbol
        JOIN Stock wst ON ws.tickerSymbol = wst.tickerSymbol
        JOIN Company wc ON wst.Company = wc.CUSIP
        JOIN Company c ON c.sector = wc.sector
        JOIN Stock st ON st.Company = c.CUSIP
        JOIN Shares s ON s.tickerSymbol = st.tickerSymbol
        WHERE w.WID = ?
        AND NOT EXISTS (
            SELECT 1
            FROM WatchlistAndShares was2
            WHERE was2.WID = w.WID
            AND was2.shareID = s.shareID
            AND was2.tickerSymbol = s.tickerSymbol
        )
        LIMIT 5
    """;

        // SQL for updating notification mode
        String updateNotificationModeSql = """
        UPDATE Watchlist
        SET notificationMode = 'On'
        WHERE WID = ?
    """;

        // SQL for adding share to watchlist
        String addToWatchlistSql = """
        INSERT INTO WatchlistAndShares (WID, shareID, tickerSymbol)
        VALUES (?, ?, ?)
    """;

        try {
            int watchlistID = -1;
            String watchlistName = "";
            String notificationMode = "";

            // Check if portfolio exists and get associated watchlist
            try (PreparedStatement pstmt = conn.prepareStatement(getWatchlistSql)) {
                pstmt.setInt(1, portfolioID);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    watchlistID = rs.getInt("WID");
                    watchlistName = rs.getString("watchlistName");
                    notificationMode = rs.getString("notificationMode");

                    System.out.println("\nWatchlist: " + watchlistName + " (ID: " + watchlistID + ")");
                    System.out.println("Current Notification Mode: " + notificationMode);
                } else {
                    System.out.println("Portfolio not found or has no associated watchlist.");
                    return;
                }
            }

            // Display current watchlist contents
            System.out.println("\nCurrent Watchlist Contents:");
            System.out.printf("%-10s %-10s %-30s %-15s %-10s\n",
                    "Ticker", "Share ID", "Company", "Sector", "Price ($)");
            System.out.println("-".repeat(80));

            boolean hasContents = false;

            try (PreparedStatement pstmt = conn.prepareStatement(getWatchlistContentsSql)) {
                pstmt.setInt(1, watchlistID);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    hasContents = true;
                    System.out.printf("%-10s %-10d %-30s %-15s $%-10.2f\n",
                            rs.getString("tickerSymbol"),
                            rs.getInt("shareID"),
                            rs.getString("CompanyName"),
                            rs.getString("sector"),
                            rs.getDouble("currentPrice"));
                }

                if (!hasContents) {
                    System.out.println("No shares in watchlist yet.");
                }
            }

            // Get and display recommendations
            System.out.println("\nRecommended Shares Based on Your Watchlist:");
            System.out.printf("%-5s %-10s %-10s %-30s %-15s %-10s %-20s\n",
                    "No.", "Ticker", "Share ID", "Company", "Sector", "Price ($)", "Recommendation Type");
            System.out.println("-".repeat(100));

            String[][] recommendations = new String[5][3]; // To store [tickerSymbol, shareID, price]
            int count = 0;

            try (PreparedStatement pstmt = conn.prepareStatement(getRecommendationsSql)) {
                pstmt.setInt(1, watchlistID);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next() && count < 5) {
                    count++;
                    String ticker = rs.getString("tickerSymbol");
                    int shareID = rs.getInt("shareID");

                    recommendations[count-1][0] = ticker;
                    recommendations[count-1][1] = String.valueOf(shareID);

                    System.out.printf("%-5d %-10s %-10d %-30s %-15s $%-10.2f %-20s\n",
                            count,
                            ticker,
                            shareID,
                            rs.getString("CompanyName"),
                            rs.getString("sector"),
                            rs.getDouble("currentPrice"),
                            rs.getString("RecommendationType"));
                }

                if (count == 0) {
                    System.out.println("No recommendations available.");
                    return;
                }
            }

            // Prompt user to add a recommended share to watchlist
            System.out.print("\nEnter the number of the share to add to your watchlist (1-" + count + ") or 0 to cancel: ");
            int selection = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (selection > 0 && selection <= count) {
                // Get selected recommendation data
                String tickerSymbol = recommendations[selection-1][0];
                int shareID = Integer.parseInt(recommendations[selection-1][1]);

                // First ensure notification mode is 'On'
                if (!notificationMode.equals("On")) {
                    try (PreparedStatement pstmt = conn.prepareStatement(updateNotificationModeSql)) {
                        pstmt.setInt(1, watchlistID);
                        int rowsAffected = pstmt.executeUpdate();

                        if (rowsAffected > 0) {
                            System.out.println("Notification mode set to 'On'.");
                        }
                    }
                }

                // Add share to watchlist
                try (PreparedStatement pstmt = conn.prepareStatement(addToWatchlistSql)) {
                    pstmt.setInt(1, watchlistID);
                    pstmt.setInt(2, shareID);
                    pstmt.setString(3, tickerSymbol);

                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        System.out.println("Share added to watchlist successfully!");
                    } else {
                        System.out.println("Failed to add share to watchlist.");
                    }
                }
            } else if (selection != 0) {
                System.out.println("Invalid selection.");
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

            // Prompt user to select a user for portfolio diversification
            Scanner scanner = new Scanner(System.in);
            System.out.print("\nEnter user email to suggest diversification options (or 'back' to return to menu): ");
            String userEmail = scanner.nextLine();

            if (!userEmail.equalsIgnoreCase("back")) {
                recommendDiversificationStocks(userEmail);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Recommends stocks for diversification based on user's current portfolio
     * and allows the user to purchase a recommended stock
     */
    private void recommendDiversificationStocks(String userEmail) {
        // First, identify the user's current sector
        String getCurrentSectorSql = """
        SELECT DISTINCT c.sector
        FROM AccountAndShares aas
        JOIN Shares s ON aas.shareID = s.shareID AND aas.tickerSymbol = s.tickerSymbol
        JOIN Stock st ON s.tickerSymbol = st.tickerSymbol
        JOIN Company c ON st.Company = c.CUSIP
        JOIN Account a ON aas.portfolioID = a.portfolioID
        WHERE a.User = ?
        LIMIT 1
    """;

        // Query for top 5 stocks from different sectors with highest price growth
        String recommendationSql = """
        SELECT 
            s.tickerSymbol,
            s.shareID,
            s.currentPrice,
            c.name AS CompanyName,
            c.sector,
            (s.currentPrice - ph.price) / ph.price * 100 AS PriceGrowthPercent
        FROM 
            Shares s
            JOIN Stock st ON s.tickerSymbol = st.tickerSymbol
            JOIN Company c ON st.Company = c.CUSIP
            JOIN PriceHistory ph ON s.tickerSymbol = ph.tickerSymbol
        WHERE 
            c.sector != ? 
            AND ph.datetime = (
                SELECT MAX(datetime) 
                FROM PriceHistory 
                WHERE datetime <= CURRENT_DATE - 2 MONTHS
                AND tickerSymbol = s.tickerSymbol
            )
            AND NOT EXISTS (
                SELECT 1\s
                FROM AccountAndShares aas\s
                WHERE aas.shareID = s.shareID\s
                AND aas.tickerSymbol = s.tickerSymbol
            )
        ORDER BY 
            PriceGrowthPercent DESC
        LIMIT 5
    """;

        // Get user portfolios
        String getUserPortfoliosSql = """
        SELECT portfolioID, portfolioName
        FROM Account
        WHERE \"USER\" = ?
    """;

        try {
            // Get user's current sector
            String currentSector = "";
            try (PreparedStatement pstmt = conn.prepareStatement(getCurrentSectorSql)) {
                pstmt.setString(1, userEmail);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    currentSector = rs.getString("sector");
                } else {
                    System.out.println("User not found or has no investments.");
                    return;
                }
            }

            // Get recommended stocks
            try (PreparedStatement pstmt = conn.prepareStatement(recommendationSql)) {
                pstmt.setString(1, currentSector);
                ResultSet rs = pstmt.executeQuery();

                System.out.println("\nRecommended Stocks for Diversification (Different from " + currentSector + "):\n");
                System.out.printf("%-5s %-10s %-30s %-15s %-10s %-15s\n",
                        "No.", "Ticker", "Company", "Sector", "Share ID", "Price ($)");

                int count = 0;
                String[][] recommendations = new String[5][3]; // To store [tickerSymbol, shareID, price]

                while (rs.next() && count < 5) {
                    count++;
                    String ticker = rs.getString("tickerSymbol");
                    int shareID = rs.getInt("shareID");
                    double price = rs.getDouble("currentPrice");

                    recommendations[count-1][0] = ticker;
                    recommendations[count-1][1] = String.valueOf(shareID);
                    recommendations[count-1][2] = String.valueOf(price);

                    System.out.printf("%-5d %-10s %-30s %-15s %-10d $%-15.2f\n",
                            count,
                            ticker,
                            rs.getString("CompanyName"),
                            rs.getString("sector"),
                            shareID,
                            price);
                }

                if (count == 0) {
                    System.out.println("No diversification recommendations available.");
                    return;
                }

                // Prompt user to purchase a recommended stock
                Scanner scanner = new Scanner(System.in);
                System.out.print("\nEnter the number of the stock you want to buy (1-" + count + ") or 0 to cancel: ");
                int selection = scanner.nextInt();
                scanner.nextLine(); // Consume newline

                if (selection > 0 && selection <= count) {
                    // Get user portfolios
                    List<Integer> portfolioIDs = new ArrayList<>();
                    List<String> portfolioNames = new ArrayList<>();

                    try (PreparedStatement pstmt2 = conn.prepareStatement(getUserPortfoliosSql)) {
                        pstmt2.setString(1, userEmail);
                        ResultSet rs2 = pstmt2.executeQuery();

                        System.out.println("\nAvailable Portfolios:");
                        int portfolioCount = 0;

                        while (rs2.next()) {
                            portfolioCount++;
                            int portfolioID = rs2.getInt("portfolioID");
                            String portfolioName = rs2.getString("portfolioName");

                            portfolioIDs.add(portfolioID);
                            portfolioNames.add(portfolioName);

                            System.out.println(portfolioCount + ". " + portfolioName + " (ID: " + portfolioID + ")");
                        }

                        if (portfolioCount == 0) {
                            System.out.println("User has no portfolios. Cannot proceed with purchase.");
                            return;
                        }

                        System.out.print("Enter portfolio ID to buy the stock: ");
                        int portfolioID = scanner.nextInt();
                        scanner.nextLine(); // Consume newline

                        // Get selected recommendation data
                        String tickerSymbol = recommendations[selection-1][0];
                        int shareID = Integer.parseInt(recommendations[selection-1][1]);
                        double price = Double.parseDouble(recommendations[selection-1][2]);

                        // Call buyShares method with the collected data
                        buyShares(userEmail, tickerSymbol, shareID, price, portfolioID);
                    }
                } else if (selection != 0) {
                    System.out.println("Invalid selection.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




}
