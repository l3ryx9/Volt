---
description: Identification des dépendances, frameworks et bibliothèques.
mode: subagent
---

# AGENT DEPENDENCIES

## OBJECTIF

Identifier les dépendances internes et externes utilisées par l'application.

## OUTILS

- JADX
- Androguard
- 7z
- grep
- Python
- git/curl si nécessaire pour vérifier une bibliothèque publique.

## ANALYSER

Identifier :

- bibliothèques Java/Kotlin ;
- AndroidX ;
- Google/Firebase ;
- OkHttp ;
- Retrofit ;
- SQLite ;
- bibliothèques crypto ;
- frameworks ;
- SDK tiers ;
- bibliothèques natives ;
- versions détectables ;
- dépendances embarquées ;
- code probablement propriétaire.

Comparer les packages et signatures lorsque nécessaire.

## SORTIE

Créer :

analysis/dependencies.json

Inclure :

name
version
package
type
evidence
source
confidence
