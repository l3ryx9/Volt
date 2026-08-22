---
description: Inventaire et analyse structurelle des classes Android/DEX.
mode: subagent
---

# AGENT CLASSES

## OBJECTIF

Construire une cartographie complète des classes et packages de l'application.

## ENTRÉES

- analysis/reconnaissance.json si disponible.

## OUTILS AUTORISÉS

- JADX
- Androguard
- Python
- grep
- 7z

## ANALYSER

Pour chaque classe pertinente :

- nom ;
- package ;
- superclass ;
- interfaces ;
- annotations ;
- champs ;
- méthodes ;
- classes internes ;
- relations avec d'autres classes ;
- références Android ;
- références bibliothèques externes ;
- indices d'obfuscation ;
- rôle potentiel.

Identifier particulièrement :

- Application ;
- Activity ;
- Service ;
- BroadcastReceiver ;
- ContentProvider ;
- classes de configuration ;
- classes réseau ;
- stockage ;
- authentification ;
- cryptographie ;
- JNI ;
- déchiffrement ;
- licence ;
- loaders ;
- classes dynamiquement chargées.

## SORTIE

Créer :

analysis/classes.json

JSON valide.

Chaque information importante doit conserver une référence vers la classe et, si possible, la source.
