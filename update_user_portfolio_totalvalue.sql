CREATE OR REPLACE PROCEDURE UpdateUserPortfolioValue (
    IN p_user_email VARCHAR(255)
)
LANGUAGE SQL
BEGIN
    DECLARE v_total_investment_value DECIMAL(15, 2) DEFAULT 0;
    DECLARE v_total_account_value DECIMAL(15, 2) DEFAULT 0;
    DECLARE v_total_bank_balance DECIMAL(15, 2) DEFAULT 0;
    DECLARE v_user_exists INT DEFAULT 0;
    
    -- First, let's make sure the user actually exists in our system
    SELECT COUNT(*) INTO v_user_exists 
    FROM "USER" 
    WHERE email = p_user_email;
    
    -- Oops, looks like this user isn't in our database
    IF v_user_exists = 0 THEN
        SIGNAL SQLSTATE '45000' 
        SET MESSAGE_TEXT = 'User does not exist';
    END IF;
    
    -- Let's calculate how much our user's current shares are worth
    SELECT COALESCE(SUM(Shares.currentPrice), 0) INTO v_total_investment_value
    FROM AccountAndShares 
    JOIN Account ON AccountAndShares.portfolioID = Account.portfolioID
    JOIN Shares ON AccountAndShares.shareID = Shares.shareID 
              AND AccountAndShares.tickerSymbol = Shares.tickerSymbol
    WHERE Account.User = p_user_email;
    
    -- Now let's total up the value across all of the user's accounts
    SELECT COALESCE(SUM(totalValue), 0) INTO v_total_account_value
    FROM Account
    WHERE User = p_user_email;
    
    -- Let's grab the total balance from all their bank accounts
    SELECT COALESCE(SUM(balance), 0) INTO v_total_bank_balance
    FROM BankAccount
    WHERE User = p_user_email;
    
    -- Time to update the user's overall balance by combining investments and bank money
    UPDATE "USER"
    SET balance = v_total_investment_value + v_total_bank_balance
    WHERE email = p_user_email;
END;

