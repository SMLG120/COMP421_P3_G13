-- Begin creating the stored procedure
CREATE PROCEDURE update_portfolio_total_value(IN user_email VARCHAR(255))
BEGIN
    -- Declare local variables
    DECLARE shareID INT DEFAULT 0;
    DECLARE tickerSymbol VARCHAR(10);
    DECLARE quantity INT;
    DECLARE currentPrice DECIMAL(10, 2);
    DECLARE totalValue DECIMAL(10, 2) DEFAULT 0;
    DECLARE done INT DEFAULT 0;

    -- Declare the cursor for fetching shares of the user
    DECLARE SHARE_CURSOR CURSOR FOR
        SELECT s.share_id, s.ticker_symbol, s.quantity, s.current_price
        FROM shares s
        JOIN portfolio p ON s.portfolio_id = p.portfolio_id
        WHERE p.user_email = user_email;

    -- Declare a continue handler for the cursor to handle the end of the cursor
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- Open the cursor
    OPEN SHARE_CURSOR;

    -- Loop through each share in the cursor
    fetch_loop: LOOP
        FETCH SHARE_CURSOR INTO shareID, tickerSymbol, quantity, currentPrice;

        -- Exit the loop if there are no more rows
        IF done = 1 THEN
            LEAVE fetch_loop;
        END IF;

        -- Calculate the total value of the portfolio (e.g., share quantity * price)
        SET totalValue = totalValue + (quantity * currentPrice);

    END LOOP;

    -- Close the cursor
    CLOSE SHARE_CURSOR;

    -- Update the total value of the portfolio in the portfolio table
    UPDATE portfolio
    SET total_value = totalValue
    WHERE user_email = user_email;

    -- Optionally, signal success or failure (handle errors)
    SIGNAL SQLSTATE '01000' SET MESSAGE_TEXT = 'Procedure executed successfully';
END;
