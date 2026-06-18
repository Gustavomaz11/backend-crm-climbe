SET @add_descricao_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE relatorios ADD COLUMN descricao TEXT NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'relatorios'
      AND column_name = 'descricao'
);

PREPARE add_descricao_stmt FROM @add_descricao_sql;
EXECUTE add_descricao_stmt;
DEALLOCATE PREPARE add_descricao_stmt;