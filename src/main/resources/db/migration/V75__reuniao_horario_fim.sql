ALTER TABLE reunioes ADD COLUMN data_hora_fim DATETIME NULL;

UPDATE reunioes SET data_hora_fim = DATE_ADD(TIMESTAMP(data, hora), INTERVAL 1 HOUR)
WHERE data IS NOT NULL AND hora IS NOT NULL;
