---
description: Identification des points d'entrée et chemins d'exécution Android.
mode: subagent
---

# AGENT ENTRYPOINTS

## OBJECTIF

Identifier comment l'application démarre et quels composants peuvent déclencher les fonctionnalités importantes.

## OUTILS

- Androguard
- Apktool
- JADX
- grep
- Python

## ANALYSER

- Application ;
- Main Activity ;
- Activities ;
- Services ;
- Receivers ;
- Providers ;
- intent-filters ;
- deep links ;
- exported components ;
- permissions ;
- callbacks ;
- workers ;
- ContentProvider ;
- BroadcastReceiver ;
- tâches planifiées.

Relier les entrypoints aux méthodes réellement appelées.

## SORTIE

Créer :

analysis/entrypoints.json

Inclure :

component
type
trigger
target_method
path
evidence
confidence
