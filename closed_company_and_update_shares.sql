CREATE OR REPLACE PROCEDURE CleanupCompanies(
    IN p_company_status VARCHAR(20) DEFAULT 'Closed'
)
LANGUAGE SQL
BEGIN
    -- Track whether we've finished processing all companies
    DECLARE done INT DEFAULT 0;
    
    -- Variables to store company details during processing
    DECLARE companyName VARCHAR(50);
    DECLARE companyCUSIP VARCHAR(20);
    DECLARE shareID_var INT;
    DECLARE tickerSymbol_var VARCHAR(10);
    DECLARE portfolioID_var INT;
    
    -- Find all companies matching the specified status
    DECLARE company_cursor CURSOR FOR
        SELECT CUSIP, name FROM Company WHERE status = p_company_status;
    
    -- Find all shares associated with each company we're processing
    DECLARE share_cursor CURSOR FOR
        SELECT s.shareID, s.tickerSymbol, a.portfolioID
        FROM Shares s
        JOIN AccountAndShares a ON s.shareID = a.shareID
        WHERE s.tickerSymbol IN (
            SELECT tickerSymbol FROM Stock WHERE Company = companyName
        );
    
    -- Stop the loop when we've processed all companies
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    
    -- Make sure we're only cleaning up valid company statuses
    IF p_company_status NOT IN ('Active', 'Closed') THEN
        SIGNAL SQLSTATE '45000' 
        SET MESSAGE_TEXT = 'Invalid company status. Must be "Active" or "Closed".';
    END IF;
    
    -- Start processing companies
    OPEN company_cursor;
    
    company_loop: LOOP
        -- Get the next company to process
        FETCH company_cursor INTO companyCUSIP, companyName;
        IF done = 1 THEN
            LEAVE company_loop;
        END IF;
        
        -- Zero out share prices for this company
        UPDATE Shares
        SET currentPrice = 0, openingPrice = 0
        WHERE tickerSymbol IN (
            SELECT tickerSymbol FROM Stock WHERE Company = companyName
        );
        
        -- Reset tracking for share processing
        SET done = 0;
        
        -- Start processing shares for this company
        OPEN share_cursor;
        share_loop: LOOP
            FETCH share_cursor INTO shareID_var, tickerSymbol_var, portfolioID_var;
            IF done = 1 THEN
                LEAVE share_loop;
            END IF;
            
            -- Clear out the total value of portfolios with these shares
            UPDATE Account
            SET totalValue = 0
            WHERE portfolioID = portfolioID_var;
            
            -- Remove these shares from all accounts
            DELETE FROM AccountAndShares WHERE shareID = shareID_var;
        END LOOP;
        
        -- Close the share cursor
        CLOSE share_cursor;
        
        -- Remove all stock records for this company
        DELETE FROM Stock WHERE Company = companyName;
        
        -- Finally, delete the company itself
        DELETE FROM Company WHERE CUSIP = companyCUSIP;
        
        -- Reset for next company
        SET done = 0;
    END LOOP;
    
    -- Close the company cursor
    CLOSE company_cursor;
END;

