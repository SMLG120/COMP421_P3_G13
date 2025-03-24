CREATE OR REPLACE PROCEDURE UpdatePortfolioTotalValue (IN user_email VARCHAR(255))
LANGUAGE SQL
BEGIN
    -- Declare local variables
    DECLARE shareID INT;
    DECLARE tickerSymbol VARCHAR(10);
    DECLARE quantity INT;
    DECLARE currentPrice DECIMAL(10, 2);
    DECLARE totalValue DECIMAL(10, 2) DEFAULT 0;
    DECLARE done INT DEFAULT 0;

    -- Declare cursor
    DECLARE SHARE_CURSOR CURSOR FOR
        SELECT s.shareID, s.tickerSymbol, aas.quantity, s.currentPrice
        FROM Shares s
        JOIN AccountAndShares aas ON s.shareID = aas.shareID AND s.tickerSymbol = aas.tickerSymbol
        JOIN Account a ON aas.portfolioID = a.portfolioID
        WHERE a.User = :user_email;

    -- Declare handler for no more records
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- Open the cursor
    OPEN SHARE_CURSOR;

    -- Loop through the cursor
    fetch_loop: LOOP
        FETCH SHARE_CURSOR INTO shareID, tickerSymbol, quantity, currentPrice;

        -- Exit loop if no more records
        IF done = 1 THEN
            LEAVE fetch_loop;
        END IF;

        -- Calculate the total value
        SET totalValue = totalValue + (quantity * currentPrice);
    END LOOP;

    -- Close the cursor
    CLOSE SHARE_CURSOR;

    -- Update the total value in the Account table
    UPDATE Account SET totalValue = totalValue WHERE User = :user_email;

    -- Optionally, return the total value
    SELECT totalValue;

END;
