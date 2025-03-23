CREATE OR REPLACE PROCEDURE CloseCompanyAndUpdateShares (
    IN companyCUSIP VARCHAR(20)  -- Input: Company CUSIP
)
LANGUAGE SQL
BEGIN ATOMIC
    -- Declare variables
    DECLARE done INT DEFAULT 0;
    DECLARE shareID_var INT;
    DECLARE tickerSymbol_var VARCHAR(10);
    DECLARE shareQuantity INT;
    DECLARE accountPortfolioID INT;
    DECLARE totalDeduction DECIMAL(15,2);
    DECLARE sharePrice DECIMAL(15,2);
    DECLARE brokerageFee DECIMAL(15,2) DEFAULT 5.00;

    -- Cursor to fetch affected shares
    DECLARE share_cursor CURSOR FOR
        SELECT s.shareID, s.tickerSymbol, a.portfolioID, a.quantity, s.currentPrice
        FROM Shares s
        JOIN AccountAndShares a ON s.shareID = a.shareID
        WHERE s.tickerSymbol IN (SELECT tickerSymbol FROM Stock WHERE CUSIP = companyCUSIP);

    -- Handle end of cursor fetch
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- Update company status
    UPDATE Company SET status = 'Closed' WHERE CUSIP = companyCUSIP;

    -- Set share prices to 0 for the closed company
    UPDATE Shares SET currentPrice = 0, openingPrice = 0
    WHERE tickerSymbol IN (SELECT tickerSymbol FROM Stock WHERE CUSIP = companyCUSIP);

    -- Open the cursor
    OPEN share_cursor;

    -- Process each row in cursor
    read_loop: LOOP
        FETCH share_cursor INTO shareID_var, tickerSymbol_var, accountPortfolioID, shareQuantity, sharePrice;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;

        -- Calculate total deduction (share price * quantity + brokerage fee)
        SET totalDeduction = (shareQuantity * sharePrice) + brokerageFee;

        -- Deduct value from account
        UPDATE Account SET totalValue = totalValue - totalDeduction WHERE portfolioID = accountPortfolioID;

        -- Set quantity of shares to 0
        UPDATE AccountAndShares SET quantity = 0 WHERE shareID = shareID_var;
    END LOOP;

    -- Close the cursor
    CLOSE share_cursor;
END;
