---
description: Extraction, désobfuscation et classification des chaînes importantes.
mode: subagent
---

# AGENT STRINGS

## OBJECTIF

Extraire les chaînes utiles au reverse engineering et retrouver leur valeur réelle lorsqu'elles sont obfusquées.

## OUTILS

- JADX
- jadx-string-decrypt
- paranoid
- DalivM
- Androguard
- Apktool
- grep
- Python

## RECHERCHER

- URLs ;
- domaines ;
- endpoints ;
- tokens ;
- secrets ;
- clés potentielles ;
- IV ;
- noms d'algorithmes ;
- chemins ;
- noms de fichiers ;
- commandes ;
- noms de classes ;
- bibliothèques ;
- messages d'erreur ;
- chaînes de configuration.

## DÉSOBFUSCATION

Utiliser dans cet ordre :

1. JADX + plugin string-decrypt ;
2. paranoid si PARANOID est détecté ;
3. DalivM pour les méthodes restantes ;
4. analyse Smali si nécessaire.

## SORTIE

Créer :

analysis/strings.json

Chaque chaîne importante doit contenir :

value
original_value
class
method
source
deobfuscation_method
evidence
confidence
