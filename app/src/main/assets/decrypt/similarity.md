---
description: Détection de similarités, code réutilisé et composants apparentés.
mode: subagent
---

# AGENT SIMILARITY

## OBJECTIF

Identifier les classes et méthodes similaires afin de détecter :

- code dupliqué ;
- wrappers ;
- variantes d'une même fonction ;
- bibliothèques embarquées ;
- implémentations parallèles ;
- méthodes de déchiffrement similaires.

## OUTILS

- JADX
- Python
- grep
- Androguard

## MÉTHODE

Comparer :

- signatures ;
- noms ;
- structure ;
- appels ;
- constantes ;
- chaînes ;
- séquences d'instructions lorsque possible.

Ne pas considérer une simple similitude de nom comme une preuve.

## SORTIE

Créer :

analysis/similarity.json
