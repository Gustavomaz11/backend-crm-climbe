SET @add_google_event_id_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE reunioes ADD COLUMN google_event_id VARCHAR(255) NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'reunioes'
      AND column_name = 'google_event_id'
);

PREPARE add_google_event_id_stmt FROM @add_google_event_id_sql;
EXECUTE add_google_event_id_stmt;
DEALLOCATE PREPARE add_google_event_id_stmt;
