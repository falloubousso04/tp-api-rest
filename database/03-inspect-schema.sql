-- =============================================================================
-- Inspection du schéma obtenu (lecture seule).
--   psql -U tp_api -h localhost -d tp_rest -f database/03-inspect-schema.sql
-- =============================================================================

-- Colonnes, types et nullabilité des tables de l'application
SELECT table_name, column_name, data_type, character_maximum_length,
       numeric_precision, numeric_scale, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('users', 'products')
ORDER BY table_name, ordinal_position;

-- Contraintes (clés primaires, unicité, vérifications)
SELECT conrelid::regclass AS table_name, conname AS constraint_name,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid IN ('public.users'::regclass, 'public.products'::regclass)
ORDER BY 1, 2;

-- Attributs du rôle applicatif
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin
FROM pg_roles WHERE rolname = 'tp_api';

-- Comptes présents (sans le hachage du mot de passe)
SELECT id, name, email, role FROM users ORDER BY id;
