-- =============================================================================
-- Création du rôle applicatif tp_api et de la base tp_rest.
--
-- À exécuter UNE FOIS avec un compte d'administration PostgreSQL (ex. postgres).
-- Le mot de passe de tp_api est transmis par une variable psql : il n'est
-- écrit dans aucun fichier du dépôt.
--
--   psql -U postgres -h localhost -v tp_api_password='<mot_de_passe_local_a_definir>' \
--        -f database/01-create-role-and-database.sql
--
-- Le script est rejouable : il ne recrée ni le rôle ni la base s'ils existent.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?tp_api_password}
\else
  \echo 'Erreur : fournissez le mot de passe avec -v tp_api_password=...'
  \quit
\endif

-- 1. Rôle de connexion : ni superutilisateur, ni création de bases ou de rôles.
SELECT format(
         'CREATE ROLE tp_api LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
         :'tp_api_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tp_api')
\gexec

-- Si le rôle existait déjà, on garantit tout de même ses attributs restreints.
ALTER ROLE tp_api NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

-- 2. Base tp_rest dont tp_api est propriétaire.
SELECT 'CREATE DATABASE tp_rest OWNER tp_api ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'tp_rest')
\gexec

ALTER DATABASE tp_rest OWNER TO tp_api;
REVOKE ALL ON DATABASE tp_rest FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE tp_rest TO tp_api;

-- 3. Droits sur le schéma public de tp_rest : seul tp_api peut y créer des objets.
\connect tp_rest
ALTER SCHEMA public OWNER TO tp_api;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO tp_api;

\echo 'Rôle tp_api et base tp_rest prêts.'
