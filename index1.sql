-- Create index on BankTransactions table

CREATE INDEX idx_bank_transactions ON BankTransactions(
    bankOperationType,
    transactionDate DESC,
    amount
)
INCLUDE (currency, status);

-- Example query using the index
SELECT * FROM BankTransactions
WHERE bankOperationType = 'Deposit'
  AND transactionDate <= '2024-01-01'
  AND amount > 80000
ORDER BY transactionDate DESC