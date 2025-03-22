CREATE OR REPLACE PROCEDURE CloseCompanyAndUpdateShares (
    IN companyCUSIP VARCHAR(20)  -- Input: Company CUSIP
)
LANGUAGE SQL
BEGIN
    DECLARE done INT DEFAULT 0;  -- Variable to track cursor completion
    DECLARE shareID_var INT;  -- Variable to hold shareID
    DECLARE tickerSymbol_var VARCHAR(10);  -- Variable to hold tickerSymbol
    DECLARE shareQuantity INT;  -- Variable to hold quantity of shares owned
    DECLARE accountPortfolioID INT;  -- Variable to hold portfolioID
    DECLARE totalDeduction DECIMAL(15,2);  -- Variable to store total deduction amount

    -- Cursor to fetch all affected shares
    DECLARE share_cursor CURSOR FOR
        SELECT s.shareID, s.tickerSymbol, a.portfolioID, a.quantity
        FROM Shares s
        JOIN AccountAndShares a ON s.shareID = a.shareID
        WHERE s.tickerSymbol IN (SELECT tickerSymbol FROM Stock WHERE Company = companyCUSIP);

    -- Declare continue handler to exit loop
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- Step 1: Mark the company as 'Closed'
    UPDATE Company
    SET status = 'Closed'
    WHERE CUSIP = companyCUSIP;





-- Step 2: Set all share values to 0
    UPDATE Shares
    SET currentPrice = 0, openingPrice = 0
    WHERE tickerSymbol IN (SELECT tickerSymbol FROM Stock WHERE Company = companyCUSIP);

    -- Step 3: Open cursor to iterate through affected shares
    OPEN share_cursor;

    read_loop: LOOP
        FETCH share_cursor INTO shareID_var, tickerSymbol_var, accountPortfolioID, shareQuantity;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;

        -- Calculate total deduction for the account
        SET totalDeduction = shareQuantity * 0;  -- Share value is now 0

        -- Update totalValue in Account
        UPDATE Account
        SET totalValue = totalValue - totalDeduction
        WHERE portfolioID = accountPortfolioID;

        -- Update AccountAndShares to reflect 0 quantity
        UPDATE AccountAndShares
        SET quantity = 0
        WHERE shareID = shareID_var;
    END LOOP;

    -- Step 4: Close cursor
    CLOSE share_cursor;
END;