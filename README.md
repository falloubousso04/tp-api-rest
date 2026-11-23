# TP — API REST sécurisée avec Spring Boot

API JSON de gestion des **produits** et des **utilisateurs**, persistée dans **PostgreSQL** et protégée par
une authentification **JWT** (rôles `USER` et `ADMIN`). La démonstration se fait depuis **Swagger UI**.

Le compte rendu du travail se trouve dans [docs/compte-rendu.md](docs/compte-rendu.md).

---

## Sommaire

1. [Versions](#1-versions)
2. [Prérequis](#2-prérequis)
3. [Organisation du projet](#3-organisation-du-projet)
4. [Préparer PostgreSQL](#4-préparer-postgresql)
5. [Variables d'environnement](#5-variables-denvironnement)
6. [Schéma et conservation des données (`ddl-auto=update`)](#6-schéma-et-conservation-des-données-ddl-autoupdate)
7. [Créer le premier ADMIN (profil `bootstrap`)](#7-créer-le-premier-admin-profil-bootstrap)
8. [Démarrer l'application](#8-démarrer-lapplication)
9. [Créer un USER de test](#9-créer-un-user-de-test)
10. [Swagger UI et parcours de démonstration](#10-swagger-ui-et-parcours-de-démonstration)
11. [Routes de l'API](#11-routes-de-lapi)
12. [Règles de validation](#12-règles-de-validation)
13. [Format des erreurs](#13-format-des-erreurs)
14. [Choix de sécurité](#14-choix-de-sécurité)
15. [Tests](#15-tests)
16. [Dépannage](#16-dépannage)

---

## 1. Versions

| Composant | Version | Remarque |
|---|---|---|
| Java | **25** (LTS) | `<java.version>25</java.version>` dans le `pom.xml` |
| Spring Boot | **4.1.3** | parent Maven |
| Maven | **3.9.16** | fourni par le Maven Wrapper (`mvnw`, `mvnw.cmd`) |
| Spring Web | Spring Framework **7.0.11** (Spring MVC, Tomcat 11.0.27) | `spring-boot-starter-webmvc` |
| Sécurité | Spring Security **7.1.3** | `spring-boot-starter-security`, hachage BCrypt |
| JPA | Spring Data JPA **2026.0.3**, Hibernate **7.4.15.Final**, Jakarta Persistence 3.2 | `spring-boot-starter-data-jpa` |
| Validation | Hibernate Validator **9.1.6.Final** | `spring-boot-starter-validation` |
| PostgreSQL | serveur **17**, pilote JDBC **42.7.14** | |
| JWT | jjwt **0.13.0** | `jjwt-api`, `jjwt-impl`, `jjwt-jackson` |
| Documentation (Swagger) | springdoc-openapi **3.1.1** | `springdoc-openapi-starter-webmvc-ui` |
| Tests | JUnit 6, Mockito, Testcontainers **2.0.5** | PostgreSQL jetable dans Docker |

Les versions gérées par Spring Boot sont rappelées en commentaire dans le `pom.xml`.

## 2. Prérequis

- **JDK 25**. Vérifier que Maven utilise bien ce JDK :
  ```bash
  ./mvnw -v        # doit afficher « Java version: 25… »
  ```
  Si une autre version apparaît, définir `JAVA_HOME` vers le JDK 25.
- **PostgreSQL 17** installé localement, **ou** Docker pour le lancer avec `compose.yaml`.
- **Docker** pour exécuter les tests d'intégration (Testcontainers).
- Maven n'a pas besoin d'être installé : le wrapper le télécharge.

> Sous Windows, remplacer `./mvnw` par `.\mvnw.cmd` dans PowerShell.

## 3. Organisation du projet

```
src/main/java/sn/ucak/tpapirest
├── bootstrap/     Initialisation du premier ADMIN (profil bootstrap)
├── config/        OpenAPI / Swagger, contrôle des variables obligatoires au démarrage
├── controller/    Contrôleurs REST : reçoivent les requêtes HTTP et appellent les services
├── dto/           Modèles de requête et de réponse (auth, user, product, error)
├── entity/        Entités JPA User, Product et énumération Role
├── exception/     Exceptions métier et gestionnaire global des erreurs
├── mapper/        Conversion entité → DTO (sans mot de passe ni hachage)
├── repository/    Accès aux données (Spring Data JPA)
├── security/      Configuration Spring Security, service JWT, filtre Bearer, réponses 401/403
├── service/       Règles métier (utilisateurs, produits, connexion)
└── validation/    Normalisation et contraintes de validation personnalisées
database/          Scripts SQL (création du rôle et de la base, complément, inspection)
scripts/           Chargement du fichier .env et génération de la clé JWT
```

Les contrôleurs ne manipulent jamais d'entités JPA : ils reçoivent et renvoient des DTO. Les identifiants sont
générés par la base (`IDENTITY`) et ne peuvent pas être fournis par le client.

## 4. Préparer PostgreSQL

L'application se connecte avec le rôle **`tp_api`**, propriétaire de la base **`tp_rest`**. Ce rôle n'est
ni superutilisateur, ni autorisé à créer des bases ou des rôles. Le compte d'administration PostgreSQL
(`postgres`) sert uniquement à exécuter le script de création ; Spring Boot ne l'utilise jamais.

### Option A — PostgreSQL installé sur le poste

```bash
psql -U postgres -h localhost \
     -v tp_api_password='<mot_de_passe_local_a_definir>' \
     -f database/01-create-role-and-database.sql
```

### Option B — PostgreSQL dans Docker

1. Dans le fichier `.env` (voir [section 5](#5-variables-denvironnement)), définir
   `POSTGRES_ADMIN_PASSWORD=<mot_de_passe_admin_postgres_a_definir>`.
2. Démarrer le conteneur puis exécuter le script avec le compte d'administration du conteneur :
   ```bash
   docker compose up -d
   docker exec tp-rest-postgres psql -U postgres \
          -v tp_api_password='<mot_de_passe_local_a_definir>' \
          -f /scripts/01-create-role-and-database.sql
   ```
   (Sous Git Bash, préfixer la commande `docker exec` par `MSYS_NO_PATHCONV=1`.)

### Ce que fait `database/01-create-role-and-database.sql`

Le script est **rejouable** : il ne recrée ni le rôle ni la base s'ils existent déjà.

```sql
CREATE ROLE tp_api LOGIN PASSWORD '…' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE DATABASE tp_rest OWNER tp_api ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE tp_rest FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE tp_rest TO tp_api;
-- dans tp_rest : schéma public réservé à tp_api
ALTER SCHEMA public OWNER TO tp_api;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO tp_api;
```

Le mot de passe passe par une variable `psql` (`-v tp_api_password=…`) : il n'est écrit dans aucun fichier du
dépôt. Pour ne pas le laisser dans l'historique du terminal, on peut aussi créer le rôle puis exécuter
`\password tp_api` dans `psql`.

## 5. Variables d'environnement

| Variable | Exemple documentaire (sans secret réel) | Usage |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tp_rest` | URL JDBC → `spring.datasource.url` |
| `DB_USERNAME` | `tp_api` | Identifiant PostgreSQL → `spring.datasource.username` |
| `DB_PASSWORD` | `<mot_de_passe_local_a_definir>` | Secret PostgreSQL → `spring.datasource.password` |
| `JWT_SECRET_BASE64` | `<cle_base64_generee_localement>` | Clé de signature JWT (HS256) |
| `JWT_TTL_SECONDS` | `900` | Validité des tokens : 15 minutes |
| `SPRING_PROFILES_ACTIVE` | `tp` | Profil d'exécution du TP |

Aucun mot de passe ni aucune clé JWT de secours n'est écrit dans le code ou dans `application.yml`.
**L'application refuse de démarrer** si `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` ou `JWT_SECRET_BASE64` manque,
ou si la clé JWT décodée fait moins de 32 octets. Le message indique le nom des variables manquantes, jamais
leur valeur.

### Générer la clé JWT

La clé doit provenir d'au moins **32 octets aléatoires**, encodés en Base64. Une phrase ou une suite de
32 caractères choisie à la main ne convient pas.

```powershell
.\scripts\generate-jwt-secret.ps1          # Windows (générateur cryptographique .NET)
```
```bash
sh scripts/generate-jwt-secret.sh          # Linux / macOS / Git Bash (openssl rand -base64 32)
```

### Fichier `.env` local

```bash
cp .env.example .env     # puis remplacer chaque marqueur <...> par une valeur locale
```

`.env` est exclu du suivi Git (`.gitignore`) ; seul `.env.example`, sans secret, est versionné.

**Spring Boot ne lit pas les fichiers `.env`.** Ses valeurs doivent être chargées dans les variables
d'environnement du terminal avant le démarrage :

```powershell
. .\scripts\load-env.ps1        # PowerShell : point + espace pour charger dans la session courante
```
```bash
. scripts/load-env.sh           # bash / zsh / Git Bash : le script doit être « sourcé »
```

Les variables peuvent aussi être définies à la main (`$env:DB_PASSWORD = "..."` en PowerShell,
`export DB_PASSWORD=...` en bash) ou dans la configuration d'exécution de l'IDE.

> Avec `load-env.sh`, une valeur contenant des caractères spéciaux du shell (`$`, espaces, guillemets) doit
> être entourée d'apostrophes dans `.env`.

## 6. Schéma et conservation des données (`ddl-auto=update`)

- Profil `tp` : `spring.jpa.hibernate.ddl-auto=update`. Au démarrage, Hibernate **crée les tables et colonnes
  manquantes sans jamais supprimer de données**. Les données sont donc **conservées entre deux démarrages**.
  À l'inverse, `create` supprime et recrée le schéma à chaque démarrage, et `create-drop` le supprime aussi à
  l'arrêt : toutes les données seraient perdues.
- Hors du profil `tp` : `ddl-auto=validate`. Hibernate vérifie seulement que le schéma correspond aux
  entités, sans rien modifier.
- `update` **ne remplace pas des migrations** : il ne modifie ni ne supprime une colonne existante et n'ajoute
  pas de contrainte sur une table déjà créée. Le script **`database/02-schema-complement.sql`** (idempotent,
  exécuté avec `tp_api` après un premier démarrage) garantit les contraintes attendues :
  ```bash
  psql -U tp_api -h localhost -d tp_rest -f database/02-schema-complement.sql
  ```
- Inspection du schéma obtenu (colonnes, contraintes, rôle, comptes sans hachage) :
  ```bash
  psql -U tp_api -h localhost -d tp_rest -f database/03-inspect-schema.sql
  ```

Schéma obtenu :

| Table | Colonnes | Contraintes |
|---|---|---|
| `users` | `id bigint` (identity), `name varchar(100)`, `email varchar(254)`, `password_hash varchar(100)`, `role varchar(20)` | `uk_users_email` (UNIQUE), `ck_users_email_normalized` (`email = lower(btrim(email))`), `users_role_check` (`USER`/`ADMIN`) |
| `products` | `id bigint` (identity), `name varchar(100)`, `description varchar(1000)` (nullable), `price numeric(12,2)` | `ck_products_price_positive` (`price > 0`) |

## 7. Créer le premier ADMIN (profil `bootstrap`)

Le profil `bootstrap` est **désactivé par défaut**. Il s'active localement, **en complément de `tp`**, le temps
d'un démarrage. Il n'existe **aucun endpoint public d'initialisation ni mot de passe par défaut**.

1. Définir les variables temporaires (dans `.env` ou dans le terminal), avec des valeurs fictives :
   ```
   BOOTSTRAP_ADMIN_NAME=Administrateur
   BOOTSTRAP_ADMIN_EMAIL=admin@exemple.test
   BOOTSTRAP_ADMIN_PASSWORD=<mot_de_passe_admin_a_definir>
   ```
2. Démarrer **une seule instance** avec les deux profils :
   ```powershell
   . .\scripts\load-env.ps1
   $env:SPRING_PROFILES_ACTIVE = "tp,bootstrap"
   .\mvnw.cmd spring-boot:run
   ```
   ```bash
   . scripts/load-env.sh && SPRING_PROFILES_ACTIVE=tp,bootstrap ./mvnw spring-boot:run
   ```
   Le journal affiche : `Premier administrateur créé (id=…, email=admin@exemple.test)…`
3. **Arrêter l'application** (Ctrl+C), **retirer le profil `bootstrap` et les variables `BOOTSTRAP_ADMIN_*`**
   (les recommenter dans `.env`, puis ouvrir un nouveau terminal ou exécuter
   `Remove-Item Env:BOOTSTRAP_ADMIN_*` / `unset BOOTSTRAP_ADMIN_NAME BOOTSTRAP_ADMIN_EMAIL BOOTSTRAP_ADMIN_PASSWORD`).
4. Redémarrer avec `SPRING_PROFILES_ACTIVE=tp` seul (section 8).

Comportement :

| Situation | Résultat |
|---|---|
| Aucun ADMIN, email libre, valeurs valides | Compte ADMIN créé (mêmes normalisations, validations et hachage BCrypt que les autres créations) |
| Un ADMIN existe déjà | **Aucun compte créé ni modifié** ; message dans le journal |
| Aucun ADMIN mais email déjà pris | **Démarrage arrêté** avec un message explicite ; le compte existant n'est ni promu ni modifié |
| Variable manquante ou valeur invalide | Démarrage arrêté ; le message cite le champ et la règle, jamais le mot de passe |

## 8. Démarrer l'application

```powershell
. .\scripts\load-env.ps1          # SPRING_PROFILES_ACTIVE=tp dans .env
.\mvnw.cmd spring-boot:run
```
```bash
. scripts/load-env.sh && ./mvnw spring-boot:run
```

Alternative : `./mvnw package` puis `java -jar target/tp-api-rest-1.0.0.jar` (avec les mêmes variables).

L'API écoute sur `http://localhost:8080`. Sans configuration, le démarrage échoue avec un message listant les
variables manquantes. C'est le comportement attendu.

## 9. Créer un USER de test

Par l'URI publique d'inscription (depuis Swagger UI ou en ligne de commande) :

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{"name":"Awa Diop","email":"awa.diop@exemple.test","password":"<mot_de_passe_user_a_definir>"}'
```

Réponse `201 Created`, en-tête `Location: /api/users/{id}`, corps `{"id":…, "name":"Awa Diop",
"email":"awa.diop@exemple.test", "role":"USER"}`. Le rôle est **toujours attribué par le serveur** : envoyer
un champ `role` à l'inscription renvoie `400` et ne crée aucun compte.

Pour recréer tous les comptes sur une base vierge : section 4 (base et rôle), puis section 7 (premier ADMIN),
puis cette section (USER de test).

## 10. Swagger UI et parcours de démonstration

- **Swagger UI** : <http://localhost:8080/swagger-ui/index.html> (`/swagger-ui.html` y redirige).
- **Document OpenAPI** : <http://localhost:8080/v3/api-docs> (JSON) et `/v3/api-docs.yaml`.
- La documentation n'est exposée qu'avec le profil `tp`. Ses ressources (`/swagger-ui.html`, `/swagger-ui/**`,
  `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml`) sont accessibles sans token, tandis que les routes
  métier gardent leurs règles d'accès.
- Le schéma de sécurité **`bearerAuth`** (HTTP, `bearer`, format `JWT`) est associé aux opérations produits
  et utilisateurs ; l'inscription et la connexion sont explicitement publiques (sans cadenas). Le cadenas
  n'est qu'une indication : les droits réels sont appliqués par Spring Security.

### Parcours

1. Ouvrir Swagger UI sans token. `POST /api/auth/register` : créer un compte. Le rôle retourné est `USER`.
2. `POST /api/auth/login` avec ce compte : copier la valeur de `accessToken`.
3. Cliquer sur **Authorize**, coller **le JWT seul** (sans guillemets ni préfixe `Bearer`), valider, fermer.
   Swagger ajoute lui-même le préfixe `Bearer`.
4. `GET /api/products` → `200`. `POST /api/products` → **`403`**. `GET /api/users` → **`403`**.
5. **Logout** dans Authorize, se connecter avec l'ADMIN, coller le nouveau token. Créer, consulter, modifier puis
   supprimer un produit ; faire de même avec un compte utilisateur (`/api/users`).
6. Retirer l'autorisation : `GET /api/products` → **`401`**. Avec un compte autorisé, montrer une erreur de
   validation (ex. `price: 1.230` → `400`) et un conflit d'email (`409`).

Dans la commande `curl` générée par Swagger ou dans l'onglet Réseau du navigateur, on vérifie la présence
d'**un seul** en-tête `Authorization: Bearer <token>`. Masquer le token et les mots de passe dans les
captures conservées, et ne jamais remettre d'export réseau brut.

## 11. Routes de l'API

| Méthode | Chemin | Fonction | Accès | Succès |
|---|---|---|---|---|
| POST | `/api/auth/register` | Inscrire un compte USER | Public | 201 |
| POST | `/api/auth/login` | Authentifier et émettre un JWT | Public | 200 |
| GET | `/api/products` | Lister les produits | USER ou ADMIN | 200 |
| GET | `/api/products/{id}` | Consulter un produit | USER ou ADMIN | 200 |
| POST | `/api/products` | Créer un produit | ADMIN | 201 |
| PUT | `/api/products/{id}` | Modifier un produit | ADMIN | 200 |
| DELETE | `/api/products/{id}` | Supprimer un produit | ADMIN | 204 |
| GET | `/api/users` | Lister les utilisateurs | ADMIN | 200 |
| GET | `/api/users/{id}` | Consulter un utilisateur | ADMIN | 200 |
| POST | `/api/users` | Créer un utilisateur et son rôle | ADMIN | 201 |
| PUT | `/api/users/{id}` | Modifier un utilisateur | ADMIN | 200 |
| DELETE | `/api/users/{id}` | Supprimer un utilisateur | ADMIN | 204 |

Formats :

- Réponse utilisateur `{id, name, email, role}`. Réponse produit `{id, name, description, price}`.
- Inscription `{name, email, password}`. Création ADMIN `{name, email, password, role}`.
- Connexion `{email, password}` → `{accessToken, tokenType: "Bearer", expiresIn: 900}`.
- Une création renvoie le DTO et un en-tête `Location`. Une suppression renvoie `204` sans corps. Une liste
  vide renvoie `200` et `[]`. Un `PUT` ne crée jamais de ressource absente (`404`).
- `PUT /api/products/{id}` : `name` et `price` obligatoires, `description` absente ou `null` possible.
- `PUT /api/users/{id}` : `name`, `email` et `role` obligatoires. `password` **omis** conserve le hachage
  existant ; s'il est fourni, il doit être non `null` et valide.
- Les routes `/api/users/**` sont réservées à ADMIN, **même pour consulter son propre compte**.
- Les réponses ne contiennent jamais `password` ni le hachage.

## 12. Règles de validation

Les noms et emails sont normalisés **avant** la validation, dans le constructeur des DTO. Le mot de passe
n'est jamais modifié.

| Champ | Règles |
|---|---|
| `name` (utilisateur, produit) | Obligatoire, pas uniquement des espaces, 2 à 100 caractères **après suppression des espaces aux extrémités** ; valeur nettoyée stockée |
| `email` | Obligatoire, format valide (partie locale ≤ 64, labels ≤ 63), ≤ 254 caractères ; espaces retirés et minuscules (`Locale.ROOT`) avant validation et stockage ; unique (service + contrainte `uk_users_email`) |
| `password` | Obligatoire à la création ; 8 caractères Unicode minimum et 72 maximum, contrôlés séparément ; contenu conservé à l'identique (espaces compris) ; refusé au-delà de 72 octets UTF-8 (limite de BCrypt, plutôt que de tronquer) |
| `role` | `USER` ou `ADMIN`, obligatoire en création et modification administratives, refusé à l'inscription publique ; stocké par son libellé |
| `description` | Facultative, ≤ 1000 caractères, jamais tronquée |
| `price` | Obligatoire, `BigDecimal` strictement positif, au plus deux décimales (`1.234` **et** `1.230` refusés, sans arrondi), point décimal |

Le JSON est strict : une propriété inconnue (`role` à l'inscription, `id` à la création…) est refusée
(`400`) au lieu d'être ignorée.

## 13. Format des erreurs

Toutes les erreurs ont le même format, qu'elles viennent des contrôleurs ou de Spring Security :

```json
{"status":400,"message":"Données invalides","errors":{"price":"Le prix doit être strictement positif"},
 "path":"/api/products","timestamp":"2026-11-24T10:15:30Z"}
```

| Statut | Situation |
|---|---|
| 400 | Donnée invalide, rôle inconnu, identifiant mal formé (`/api/products/abc`), corps JSON illisible ou non conforme |
| 401 | Connexion incorrecte (message générique identique pour email inconnu et mauvais mot de passe) ; token absent, invalide ou expiré sur une route protégée |
| 403 | Compte authentifié sans le rôle requis |
| 404 | Produit ou utilisateur inexistant, pour un appelant autorisé |
| 409 | Email normalisé déjà utilisé (inscription, création, modification) |

`errors` n'apparaît que pour les erreurs portant sur des champs. Aucune réponse ne contient de mot de passe,
de token, de hachage, de détail SQL ni de trace technique. Un token invalide produit toujours un `401` JSON,
jamais une page HTML ni une erreur 500.

## 14. Choix de sécurité

- **Mots de passe** hachés avec **BCrypt** à chaque création ou changement, une seule fois. Ils n'apparaissent
  ni dans les réponses, ni dans les erreurs, ni dans les journaux (les `toString()` des DTO les masquent).
- **Connexion** : email normalisé, mot de passe comparé tel quel au hachage. Un email inconnu et un mauvais mot
  de passe donnent le même `401` ; un hachage factice est calculé pour un email inconnu afin que le temps de
  réponse ne révèle pas l'existence du compte.
- **JWT** signé en **HS256**, valable **900 secondes** (profil tp). Il contient uniquement l'identifiant de
  l'utilisateur (`sub`), `iat` et `exp`, sans email, rôle ni secret. Avant toute authentification, le filtre
  vérifie la signature, l'algorithme (`HS256` exigé), la présence de `exp`/`iat` et l'expiration. Les tokens
  non signés (`alg: none`), modifiés ou mal formés sont refusés. Les tokens ne sont jamais journalisés.
- **Clé** externalisée (`JWT_SECRET_BASE64`), générée localement à partir d'au moins 32 octets aléatoires.
  Sa taille est vérifiée après décodage au démarrage.
- **Rôle relu en base à chaque requête** à partir du sujet du token : un compte supprimé est refusé (`401`)
  et un compte rétrogradé perd ses droits ADMIN dès la requête suivante. Correspondance des autorités :
  `USER` → `ROLE_USER`, `ADMIN` → `ROLE_ADMIN` (préfixe `ROLE_` attendu par `hasRole`).
- **Sans état** : `SessionCreationPolicy.STATELESS`, pas de formulaire de connexion, pas de HTTP Basic, pas de
  cookie. La lecture du compte en base ne crée aucune session.
- **CSRF désactivé** : une attaque CSRF exploite le fait que le navigateur joint **automatiquement** un cookie
  de session aux requêtes vers le site. Ici, aucun cookie d'authentification n'existe : le token est transmis
  uniquement dans l'en-tête `Authorization`, que le client doit ajouter explicitement et qu'un site tiers ne
  peut pas faire envoyer à l'insu de l'utilisateur. La protection CSRF n'apporterait donc rien.
- **Limites retenues pour le TP** : ni renouvellement (refresh token) ni révocation. Après un changement de
  mot de passe, un token déjà émis **reste valide jusqu'à son expiration** (15 minutes au plus), à condition
  que le compte existe toujours. Les droits utilisés sont toujours ceux actuellement en base.

## 15. Tests

```bash
./mvnw test          # Windows : .\mvnw.cmd test
```

- **Docker doit être démarré** : les tests d'intégration lancent un PostgreSQL 17 jetable avec Testcontainers,
  **séparé de la base de démonstration** (aucune donnée de `tp_rest` n'est touchée). Les tests ne nécessitent
  aucune variable d'environnement : la connexion est fournie par le conteneur et une clé JWT aléatoire est
  générée à chaque exécution.
- **Tests unitaires** : validation aux limites (noms, emails de 254/255 caractères, mots de passe de 7/8/72/73
  caractères, prix, description), service JWT (expiré, modifié, non signé, autre clé, autre algorithme, clé
  trop courte), règles du service utilisateur, refus de démarrage si une variable manque.
- **Tests d'intégration** (configuration de sécurité réelle + PostgreSQL) : deux CRUD complets, inscription
  `USER`, connexions réussies et refusées, tokens absents/invalides/expirés, accès de chaque rôle,
  documentation publique, absence de session, changement de mot de passe, conservation du hachage, compte
  supprimé et changement de rôle avec un token déjà émis, `409` après normalisation, `404`, `400` (JSON mal
  formé, identifiant), absence de `password`/hachage dans les réponses, et un test qui contourne le service
  pour vérifier que **PostgreSQL refuse un doublon d'email**.

## 16. Dépannage

| Symptôme | Cause probable |
|---|---|
| `Démarrage refusé : variable(s) d'environnement obligatoire(s) non définie(s)` | `.env` non chargé dans ce terminal (section 5) |
| `JWT_SECRET_BASE64 doit contenir au moins 32 octets` | Clé trop courte ou choisie à la main : la régénérer |
| `password authentication failed for user "tp_api"` | `DB_PASSWORD` différent du mot de passe défini dans le script de la section 4 |
| `Schema-validation: missing table` | Démarrage sans le profil `tp` sur une base vierge : définir `SPRING_PROFILES_ACTIVE=tp` |
| Tests : `Could not find a valid Docker environment` | Docker Desktop n'est pas démarré |
| Swagger UI renvoie 404 | Profil `tp` non actif |
