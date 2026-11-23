#!/usr/bin/env sh
# Charge les variables d'un fichier .env dans le shell courant.
# Spring Boot ne lit pas les fichiers .env : ce script doit être « sourcé ».
#
# Utilisation :  . scripts/load-env.sh          (ou : . scripts/load-env.sh .env.test)
ENV_FILE="${1:-.env}"
if [ ! -f "$ENV_FILE" ]; then
  echo "Fichier $ENV_FILE introuvable. Copiez .env.example en $ENV_FILE puis complétez-le." >&2
  return 1 2>/dev/null || exit 1
fi
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
echo "Variables chargées depuis $ENV_FILE"
