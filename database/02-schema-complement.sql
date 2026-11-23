-- =============================================================================
-- Complément reproductible du schéma généré par Hibernate (profil tp).
--
-- ddl-auto=update crée les tables et colonnes manquantes mais ne modifie ni ne
-- rajoute les contraintes d'une table déjà existante. Ce script garantit les
-- contraintes attendues, quel que soit l'historique de la base.
--
-- À exécuter avec tp_api APRÈS un premier démarrage de l'application :
--   psql -U tp_api -h localhost -d tp_rest -f database/02-schema-complement.sql
--
-- Le script est idempotent : il peut être rejoué sans effet de bord.
-- =============================================================================

\set ON_ERROR_STOP on

DO $$
BEGIN
    -- Unicité de l'email (déjà normalisé en minuscules par l'application).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_email') THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
    END IF;

    -- L'email stocké est toujours normalisé : sans espaces aux extrémités et en minuscules.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_users_email_normalized') THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_email_normalized
            CHECK (email = lower(btrim(email)));
    END IF;

    -- Le rôle est stocké par son libellé.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_users_role') THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
    END IF;

    -- Le prix est strictement positif.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_products_price_positive') THEN
        ALTER TABLE products ADD CONSTRAINT ck_products_price_positive CHECK (price > 0);
    END IF;
END
$$;

\echo 'Contraintes du schéma vérifiées.'
