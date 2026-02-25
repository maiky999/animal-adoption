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