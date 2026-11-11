IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Psy')
    INSERT INTO categories (name) VALUES ('Psy');

IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Koty')
    INSERT INTO categories (name) VALUES ('Koty');

IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Gryzonie')
    INSERT INTO categories (name) VALUES ('Gryzonie');

IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Ptaki')
    INSERT INTO categories (name) VALUES ('Ptaki');

IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Gady i płazy')
    INSERT INTO categories (name) VALUES ('Gady i płazy');

IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Ryby')
    INSERT INTO categories (name) VALUES ('Ryby');

IF NOT EXISTS(SELECT 1 FROM categories WHERE name = 'Inne')
    INSERT INTO categories (name) VALUES ('Inne');

DECLARE @ck NVARCHAR(256)
SELECT @ck = cc.name
FROM sys.check_constraints cc
JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
WHERE cc.parent_object_id = OBJECT_ID('ads') AND c.name = 'status' AND cc.definition NOT LIKE '%REMOVED%'
IF @ck IS NOT NULL
BEGIN
    EXEC('ALTER TABLE ads DROP CONSTRAINT ' + @ck)
    ALTER TABLE ads ADD CONSTRAINT CK_ads_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'ADOPTED', 'ARCHIVED', 'REJECTED', 'REMOVED'))
END;
