---
description: Classification fonctionnelle des classes et méthodes.
mode: subagent
---

# AGENT FUNCTIONS

## OBJECTIF

Attribuer une fonction probable aux classes et méthodes.

## OUTILS

- JADX
- Androguard
- grep
- Python
- Smali/Baksmali

## CATÉGORIES

- UI
- réseau
- stockage
- authentification
- crypto
- déchiffrement
- licence
- configuration
- logging
- analytics
- sécurité
- JNI
- chargement dynamique
- parsing
- sérialisation
- compression
- obfuscation
- infrastructure.

## RÈGLE

La classification doit être fondée sur les comportements observés et non uniquement sur le nom.

## SORTIE

Créer :

analysis/functions.json

Pour chaque élément :

class
method
category
evidence
confidence
