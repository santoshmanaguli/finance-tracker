-- migrate:up
-- Reference data: categories + SYSTEM merchant rules.
-- 'Uncategorized' is required: the app falls back to it when no rule matches.

INSERT INTO categories (name) VALUES
    ('Uncategorized'),
    ('Food'),
    ('Groceries'),
    ('Fuel'),
    ('Shopping'),
    ('Bills'),
    ('Rent'),
    ('Travel'),
    ('Health'),
    ('Entertainment'),
    ('Transfers'),
    ('Salary'),
    ('Investments');

-- Category ids are looked up by name; identity values are not guaranteed to be 1, 2, 3...
-- Patterns are matched against the NORMALIZED merchant (uppercase, punctuation -> spaces).
-- CONTAINS 'AMAZON' is deliberately absent: Amazon Pay covers bills, recharges AND shopping,
-- so one rule would be wrong half the time. Leave it Uncategorized until splits/notes exist.
INSERT INTO merchant_rules (pattern, match_type, category_id, priority, created_by)
SELECT v.pattern, v.match_type, c.id, 100, 'SYSTEM'
FROM (VALUES
        ('SWIGGY',      'CONTAINS', 'Food'),
        ('ZOMATO',      'CONTAINS', 'Food'),
        ('BIGBASKET',   'CONTAINS', 'Groceries'),
        ('BLINKIT',     'CONTAINS', 'Groceries'),
        ('ZEPTO',       'CONTAINS', 'Groceries'),
        ('DMART',       'CONTAINS', 'Groceries'),
        ('INDIAN OIL',  'CONTAINS', 'Fuel'),
        ('IOCL',        'CONTAINS', 'Fuel'),
        ('HPCL',        'CONTAINS', 'Fuel'),
        ('BPCL',        'CONTAINS', 'Fuel'),
        ('AIRTEL',      'CONTAINS', 'Bills'),
        ('JIO',         'CONTAINS', 'Bills'),
        ('MSEDCL',      'CONTAINS', 'Bills'),
        ('BESCOM',      'CONTAINS', 'Bills'),
        ('IRCTC',       'CONTAINS', 'Travel'),
        ('UBER',        'CONTAINS', 'Travel'),
        ('OLA',         'EXACT',    'Travel'),   -- EXACT: CONTAINS 'OLA' would match 'COCA COLA'
        ('NETFLIX',     'CONTAINS', 'Entertainment'),
        ('BOOKMYSHOW',  'CONTAINS', 'Entertainment'),
        ('ZERODHA',     'CONTAINS', 'Investments'),
        ('APOLLO',      'CONTAINS', 'Health')
     ) AS v(pattern, match_type, category_name)
JOIN categories c ON c.name = v.category_name;

-- migrate:down
DELETE FROM merchant_rules WHERE created_by = 'SYSTEM';
DELETE FROM categories;
