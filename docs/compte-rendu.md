# Compte rendu — TP API REST sécurisée avec Spring Boot

**Auteur :** Fallou Bousso Mamoun — Licence 3 Informatique, UCAK
**Projet :** `tp-api-rest` (Spring Boot 4.1.3, Java 25, PostgreSQL 17, JWT, Swagger UI)

> Tous les secrets (mots de passe, clé JWT, tokens, hachages) sont masqués dans ce document. Les comptes
> cités utilisent des emails fictifs du domaine `exemple.test`.

---

## 1. Objectif

Concevoir une API REST JSON de gestion des produits et des utilisateurs, persistée dans PostgreSQL, avec une
authentification par email et mot de passe qui délivre un JWT. Les droits dépendent du rôle (`USER` ou
`ADMIN`). L'accent est mis sur la validation des données, la gestion des erreurs, la sécurité des accès et
les tests. La démonstration se fait depuis Swagger UI.

## 2. Architecture

| Couche | Rôle | Exemples |
|---|---|---|
| `controller` | Reçoit les requêtes HTTP, valide les DTO (`@Valid`), appelle les services, fixe les statuts | `ProductController`, `UserController`, `AuthController` |
| `service` | Règles métier : rôle à l'inscription, unicité de l'email, hachage, 404 | `UserService`, `ProductService`, `AuthService` |
| `repository` | Accès aux données (Spring Data JPA) | `UserRepository`, `ProductRepository` |
| `entity` | Mapping JPA et contraintes de la base | `User`, `Product`, `Role` |
| `dto` / `mapper` | Formats d'échange ; les entités ne sortent jamais des services | `UserResponse`, `ProductRequest`… |
| `security` | Spring Security sans état, JWT, filtre Bearer, réponses 401/403 JSON | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter` |
| `exception` | Format d'erreur commun et traduction centralisée des exceptions | `GlobalExceptionHandler` |
| `validation` | Normalisation et contraintes personnalisées | `@ValidName`, `@ValidEmail`, `@ValidPassword` |
| `bootstrap` | Création du premier ADMIN (profil `bootstrap`) | `AdminBootstrapService` |

## 3. Travail réalisé par étape

### I. Environnement

Projet Maven avec Maven Wrapper 3.9.16, Java 25 et Spring Boot 4.1.3. Les dépendances sont Spring Web MVC,
Security, Validation, Data JPA, le pilote PostgreSQL, jjwt 0.13.0 et springdoc 3.1.1. Le profil `tp` est
défini dans `application-tp.yml`. Vérification : `./mvnw -v` affiche bien `Java version: 25.0.2`. Lancée sans
configuration, l'application s'arrête comme attendu :

```
Démarrage refusé : variable(s) d'environnement obligatoire(s) non définie(s) :
DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET_BASE64.
```

### II. PostgreSQL et conservation des données

- Le script `database/01-create-role-and-database.sql`, exécuté avec le compte `postgres`, crée le rôle
  `tp_api` (ni superutilisateur, ni création de bases ou de rôles) et la base `tp_rest`, dont `tp_api` est
  propriétaire. Il lui donne les droits sur le schéma `public`. Il a été rejoué deux fois sans erreur.
- Contrôle effectué :
  ```
   rolname | rolsuper | rolcreatedb | rolcreaterole        datname | owner
   tp_api  | f        | f           | f                    tp_rest | tp_api
  ```
- `DB_URL`, `DB_USERNAME` et `DB_PASSWORD` alimentent `spring.datasource.*`. Aucun secret n'est écrit dans le
  code ; `.env.example` est fourni et `.env` est ignoré par Git.
- Point découvert : lors de la liaison des propriétés, Spring Boot laisse tel quel un marqueur `${DB_PASSWORD}`
  non résolu, sans erreur. Un contrôle exécuté avant la création des beans (`RequiredConfigurationValidator`)
  fait donc échouer le démarrage avec la liste des variables manquantes.
- `ddl-auto=update` n'est actif qu'en profil `tp` (`validate` ailleurs). Après un premier démarrage, les tables
  ont été inspectées (`database/03-inspect-schema.sql`) :
  ```
   products | price   | numeric(12,2) | NOT NULL     ck_products_price_positive  CHECK (price > 0)
   users    | email   | varchar(254)  | NOT NULL     uk_users_email              UNIQUE (email)
   users    | role    | varchar(20)   | NOT NULL     users_role_check            role IN ('USER','ADMIN')
                                                     ck_users_email_normalized   email = lower(btrim(email))
  ```
- `update` ne rajoute pas une contrainte sur une table existante. `database/02-schema-complement.sql` les
  garantit de façon idempotente (rejoué deux fois : aucune contrainte en double). La stratégie Hibernate
  `RECREATE_QUIETLY` évite en plus de supprimer puis recréer la contrainte d'unicité à chaque démarrage.

### III. Organisation et URI

Les packages suivent la séparation demandée : contrôleurs, services, repositories, DTO, sécurité et
exceptions. Les URI retenues sont `/api/auth/register`, `/api/auth/login`, `/api/products[/{id}]` et
`/api/users[/{id}]`. Les créations renvoient `201` et un en-tête `Location`, les suppressions `204` sans corps.
Un `PUT` ne crée jamais de ressource. Les routes `/api/users/**` sont réservées à ADMIN, même pour consulter
son propre compte (vérifié : `403`).

### IV. Validation

- Les noms et emails sont normalisés dans le constructeur des DTO, donc **avant** la validation. L'email est
  mis en minuscules avec `Locale.ROOT`, ce qu'un test vérifie avec la locale turque.
- Contraintes personnalisées :
  - **nom** : 2 à 100 caractères après nettoyage ;
  - **email** : 254 caractères au plus, partie locale de 64 caractères au plus, labels de 63 caractères au plus ;
  - **mot de passe** : 8 et 72 caractères Unicode contrôlés séparément, contenu inchangé.
- Point de vigilance : BCrypt ne prend en compte que **72 octets**, et Spring Security refuse un mot de passe
  plus long. Un mot de passe de 40 « é » (80 octets) aurait provoqué une erreur 500. Il est désormais refusé
  en 400 avec un message explicite.
- Prix : `BigDecimal` avec `@DecimalMin(exclusif)` et `@Digits(fraction = 2)`. Jackson conserve l'échelle de
  `1.230` (3 décimales) : la valeur est refusée sans arrondi.
- L'unicité de l'email est contrôlée dans le service (message lisible) puis par la contrainte
  `uk_users_email`. Une violation de cette contrainte précise, identifiée par son nom, devient un `409` ; les
  autres erreurs de base ne sont pas confondues avec un conflit d'email.

### V. Utilisateurs et premier ADMIN

- L'inscription attribue `USER`. Le JSON étant strict, un champ `role` à l'inscription renvoie `400` sans
  aucune création.
- Le mot de passe est haché avec BCrypt à chaque création ou changement. Un `password` omis en modification
  conserve le hachage. Un `password: null` est refusé.
- Difficulté rencontrée : sur un *record*, l'annotation Jackson `Nulls.FAIL` rejetait aussi l'**absence** du
  champ. `UpdateUserRequest` est donc devenu une classe à setters, qui distingue « absent » de « null ».
  Ce défaut a été trouvé par les tests d'intégration.
- Profil `bootstrap` :
  - premier démarrage avec `tp,bootstrap` → `Premier administrateur créé (id=1, email=admin@exemple.test)` ;
  - relance avec un ADMIN déjà présent → « aucun compte n'a été créé ni modifié ». Les empreintes des comptes
    avant et après sont identiques (comparaison par MD5 des hachages, sans afficher les hachages) ;
  - email déjà pris par un USER → arrêt explicite, compte inchangé (test d'intégration).
- Hachage en base : préfixe `$2a$`, 60 caractères. La valeur n'est pas reproduite ici.

### VI. JWT et rôles

- Le JWT est signé en HS256 et valable 900 s. Il contient uniquement `sub` (identifiant), `iat` et `exp`. La
  clé fait 32 octets aléatoires encodés en Base64, et sa taille est vérifiée au démarrage.
- La vérification porte sur la signature, l'algorithme exigé (un token HS512 signé avec la même clé est
  refusé), la présence de `exp` et `iat`, et l'expiration. `parseSignedClaims` refuse les tokens `alg: none`.
- Le compte est relu en base à chaque requête : un compte supprimé reçoit `401`, et un compte rétrogradé perd
  ses droits dès la requête suivante, avec le même token.
- Sécurité sans état (aucune session ni cookie, vérifié par test). CSRF est désactivé, puisque le token ne
  circule que dans l'en-tête `Authorization`. La connexion renvoie le même message pour un email inconnu et
  pour un mauvais mot de passe.

### VII. CRUD des produits

Le cycle complet a été vérifié avec ADMIN (création, liste, détail, modification, suppression, puis `404`).
Avec USER, la lecture renvoie `200` et les trois écritures `403`, sans modification de la base. Le prix est
stocké exactement (`12.3` devient `12.30`, `0.01` reste `0.01`). Un identifiant non numérique renvoie `400`.

### VIII. Erreurs

Toutes les erreurs suivent le format `{status, message, errors, path, timestamp}`, y compris celles de Spring
Security (401/403 écrits en JSON par des gestionnaires dédiés). Exemples obtenus :

```json
{"status":400,"message":"Données invalides","errors":{"price":"Le prix doit comporter au plus 2 décimales et 10 chiffres avant la virgule"},"path":"/api/products"}
{"status":401,"message":"Token invalide","path":"/api/products"}
{"status":403,"message":"Accès refusé : rôle insuffisant","path":"/api/users"}
{"status":404,"message":"Produit introuvable (id 9999)","path":"/api/products/9999"}
{"status":409,"message":"Cet email est déjà utilisé par un autre compte","errors":{"email":"Cet email est déjà utilisé"},"path":"/api/auth/register"}
```

Problème corrigé : sur une violation de contrainte, PostgreSQL ajoute « Failing row contains (…) », qui
recopiait la ligne refusée, hachage compris, dans les journaux. L'option du pilote
`logServerErrorDetail=false` supprime ce détail. Les journaux complets des tests ont ensuite été contrôlés :
aucun hachage, mot de passe ni token n'y figure.

### IX. Swagger UI

- Le document OpenAPI déclare le titre, la description, la version, le schéma `bearerAuth` (HTTP, bearer,
  JWT) et des réponses d'erreur communes.
- Contrôle du document généré : les 10 opérations produits et utilisateurs portent `bearerAuth`, alors que
  `register` et `login` ont une sécurité vide (publiques). Aucun schéma de réponse ne contient `password`.
- `/swagger-ui.html` redirige (302) vers `/swagger-ui/index.html`. `/v3/api-docs` et `/v3/api-docs.yaml` sont
  accessibles sans token.

### X. Tests

`./mvnw clean verify` : **147 tests, 0 échec**.

| Suite | Tests | Contenu |
|---|---|---|
| `RequestValidationTest` | 53 | Limites des noms, emails, mots de passe, rôles, descriptions, prix |
| `JwtServiceTest` | 13 | Contenu du token, expiré, modifié, non signé, autre clé, autre algorithme, clé courte |
| `UserServiceTest` | 9 | Rôle USER, hachage unique, conservation du hachage, détection précise du conflit d'email |
| `RequiredConfigurationValidatorTest` / `TextNormalizerTest` | 6 | Refus de démarrage, normalisation |
| `AuthIntegrationTest` | 12 | Inscription, connexion, JSON mal formé, absence de session, documentation publique |
| `SecurityIntegrationTest` | 12 | Tokens absents/altérés/non signés/expirés, matrice USER/ADMIN, compte supprimé, changement de rôle |
| `ProductIntegrationTest` | 20 | CRUD, 403 sans effet, 404/400, prix exacts, PUT invalide sans effet |
| `UserIntegrationTest` | 12 | CRUD, 409 après normalisation, changement de mot de passe, password omis/null |
| `PersistenceIntegrationTest` | 6 | Doublon d'email refusé par PostgreSQL en contournant le service, mapping, contraintes |
| `AdminBootstrapIntegrationTest` | 4 | Création, ADMIN existant, email pris, validations |

Les tests d'intégration utilisent la configuration de sécurité réelle et un PostgreSQL 17 jetable
(Testcontainers), séparé de la base de démonstration.

## 4. Persistance après redémarrage

Entre deux démarrages successifs de l'application en profil `tp`, les données ont été relevées en base :

```
avant : 1:admin@exemple.test:ADMIN  2:awa.diop@exemple.test:USER | produits : 1:Clavier:12.30
après : 1:admin@exemple.test:ADMIN  2:awa.diop@exemple.test:USER | produits : 1:Clavier:12.30
```

## 5. Limites et pistes d'amélioration

- Pas de renouvellement ni de révocation des tokens (hors périmètre). Après un changement de mot de passe, un
  token déjà émis reste valide jusqu'à son expiration, au plus 15 minutes. Les droits appliqués sont toujours
  ceux actuellement en base.
- `ddl-auto=update` convient au TP mais pas à la production : des migrations versionnées (Flyway ou Liquibase)
  seraient nécessaires.
- Pas de pagination (facultative), ni de limitation du nombre de tentatives de connexion.
- Rien n'empêche un ADMIN de supprimer ou de rétrograder le dernier administrateur. Il faudrait alors relancer
  le profil `bootstrap`.

## 6. Conclusion

L'API respecte le tableau des URI, les statuts HTTP et les règles de validation du sujet. Les droits sont
appliqués par Spring Security à partir du rôle actuel en base, les erreurs ont un format JSON unique et aucun
secret n'est exposé. Le README permet de recréer la base, les comptes et le parcours de démonstration dans un
environnement neuf.
