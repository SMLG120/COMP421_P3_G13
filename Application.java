package P3;

import java.util.Scanner;

public class Application {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        StockDatabaseApp app = new StockDatabaseApp();

        while (true) {
            System.out.println("\nStock Management System - Main Menu:");
            System.out.println("1. View all shares from a user");
            System.out.println("2. Buy shares (Investment Transaction)");
            System.out.println("3. Sell shares (Investment Transaction)");
            System.out.println("4. Approve pending selling transactions");
            System.out.println("5. Identify users at risk (single-sector investment)");
            System.out.println("6. Quit");
            System.out.print("Enter your choice: ");

            int choice = scanner.nextInt();
            scanner.nextLine();  // Consume newline

            switch (choice) {
                case 1:
                    System.out.print("Enter user email: ");
                    String userEmail = scanner.nextLine();
                    app.viewUserPortfolio(userEmail);
                    break;
                case 2:
                    System.out.print("Enter user email: ");
                    userEmail = scanner.nextLine();
                    System.out.print("Enter ticker symbol: ");
                    String tickerSymbol = scanner.nextLine();
                    System.out.print("Enter share ID: ");
                    int shareID = scanner.nextInt();
                    System.out.print("Enter amount: ");
                    double amount = scanner.nextDouble();
                    System.out.print("Enter portfolio ID: ");
                    int portfolioID = scanner.nextInt();
                    app.buyShares(userEmail, tickerSymbol, shareID, amount, portfolioID);
                    break;
                case 3:
                    System.out.print("Enter user email: ");
                    userEmail = scanner.nextLine();
                    System.out.print("Enter ticker symbol: ");
                    tickerSymbol = scanner.nextLine();
                    System.out.print("Enter share ID: ");
                    shareID = scanner.nextInt();
                    System.out.print("Enter amount: ");
                    amount = scanner.nextDouble();
                    app.sellShares(userEmail, tickerSymbol, shareID, amount);
                    break;
                case 4:
                    app.completePendingSellTransactions();
                    break;
                case 5:
                    app.identifyAtRiskUsers();
                    break;
                case 6:
                    System.out.println("Exiting...");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
                    try { Thread.sleep(2000); } catch (Exception e) {}
            }
        }
    }

}
