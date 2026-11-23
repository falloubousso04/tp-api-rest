#!/usr/bin/env sh
# Génère une clé de signature JWT : 32 octets aléatoires encodés en Base64.
# Utilisation : sh scripts/generate-jwt-secret.sh
openssl rand -base64 32
