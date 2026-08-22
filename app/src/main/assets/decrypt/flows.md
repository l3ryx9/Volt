---
description: Reconstruction des flux de données et des chaînes de traitement.
mode: subagent
---

# AGENT FLOWS

## OBJECTIF

Relier les composants entre eux pour reconstruire les flux de données.

## OUTILS

- JADX
- Androguard
- Apktool
- baksmali
- Python
- grep
- DalivM si nécessaire

## FLUX À RECHERCHER

- entrée utilisateur → traitement ;
- réseau → stockage ;
- fichier → déchiffrement ;
- chaîne obfusquée → déchiffreur → résultat ;
- clé → KDF → clé finale → Cipher ;
- données → Base64 → crypto ;
- JNI → Java ;
- entrypoint → méthode → sous-méthodes.

## SORTIE

Créer :

analysis/flows.json

Chaque flux doit contenir :

source
steps
destination
classes
methods
data
transformations
evidence
confidence
