-- CloseCompanyAndUpdateShares.sql

CREATE OR REPLACE PROCEDURE CleanupClosedCompanies()
LANGUAGE SQL
BEGIN ATOMIC
    DECLARE done INT DEFAULT 0;
    DECLARE companyName VARCHAR(50);
    DECLARE companyCUSIP VARCHAR(20);
    DECLARE shareID_var INT;
    DECLARE tickerSymbol_var VARCHAR(10);
    DECLARE portfolioID_var INT;

    -- Cursor to loop through all closed companies
    DECLARE company_cursor CURSOR FOR
        SELECT CUSIP, name FROM Company WHERE status = 'Closed';

    -- Declare cursor for shares of a company
    DECLARE share_cursor CURSOR FOR
        SELECT s.shareID, s.tickerSymbol, a.portfolioID
        FROM Shares s
        JOIN AccountAndShares a ON s.shareID = a.shareID
        WHERE s.tickerSymbol IN (
            SELECT tickerSymbol FROM Stock WHERE Company = companyName
        );

    -- Continue handler to exit loop
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- Open company cursor
    OPEN company_cursor;

    company_loop: LOOP
        FETCH company_cursor INTO companyCUSIP, companyName;
        IF done = 1 THEN
            LEAVE company_loop;
        END IF;

        -- Step 1: Set share prices to 0 for the closed company
        UPDATE Shares
        SET currentPrice = 0, openingPrice = 0
        WHERE tickerSymbol IN (
            SELECT tickerSymbol FROM Stock WHERE Company = companyName
        );

        -- Step 2: Open cursor for shares in the company
        OPEN share_cursor;

        share_loop: LOOP
            FETCH share_cursor INTO shareID_var, tickerSymbol_var, portfolioID_var;
            IF done = 1 THEN
                LEAVE share_loop;
            END IF;

            -- Step 3: Set totalValue in Account to 0 for affected portfolios
            UPDATE Account
            SET totalValue = 0
            WHERE portfolioID = portfolioID_var;

            -- Step 4: Remove shares from AccountAndShares
            DELETE FROM AccountAndShares WHERE shareID = shareID_var;
        END LOOP;

        -- Close share cursor
        CLOSE share_cursor;

        -- Step 5: Remove the company’s stocks
        DELETE FROM Stock WHERE Company = companyName;

        -- Step 6: Delete the company
        DELETE FROM Company WHERE CUSIP = companyCUSIP;
    END LOOP;

    -- Close company cursor
    CLOSE company_cursor;

    -- Success message
    SIGNAL SQLSTATE '01000' SET MESSAGE_TEXT = 'All closed companies and their data have been removed.';
END
@
