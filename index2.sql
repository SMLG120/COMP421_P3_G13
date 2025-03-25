-- Create an index on the Account table

CREATE INDEX idx_account_portfolio ON Account(
    type,
    totalValue DESC
)
INCLUDE (portfolioName, Watchlist);

-- Example query using the index
SELECT * FROM Account
WHERE type = 'TFSA'
ORDER BY totalValue DESC