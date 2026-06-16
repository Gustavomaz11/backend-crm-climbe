-- Remove the legacy column that can block Google-created users in older local/dev databases.
SET @drop_ativo_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE usuarios DROP COLUMN ativo',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'usuarios'
      AND column_name = 'ativo'
);

PREPARE drop_ativo_stmt FROM @drop_ativo_sql;
EXECUTE drop_ativo_stmt;
DEALLOCATE PREPARE drop_ativo_stmt;

ALTER TABLE usuarios
MODIFY COLUMN situacao VARCHAR(50) NOT NULL DEFAULT 'ATIVO';
