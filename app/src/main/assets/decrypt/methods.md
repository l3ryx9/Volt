---
description: Analyse des méthodes et des relations d'appel.
mode: subagent
---

# AGENT METHODS

## OBJECTIF

Construire une cartographie des méthodes de l'application et identifier celles qui ont une importance fonctionnelle ou technique.

## OUTILS

- JADX
- Androguard
- baksmali
- Python
- grep
- JDK 17 / javap si nécessaire

## ANALYSER

Pour les méthodes importantes :

- classe ;
- nom ;
- signature ;
- paramètres ;
- type de retour ;
- visibilité ;
- appels effectués ;
- méthodes appelantes ;
- méthodes appelées ;
- API Android utilisées ;
- constantes ;
- chaînes ;
- opérations mathématiques ;
- opérations bitwise ;
- accès fichiers ;
- réseau ;
- crypto ;
- réflexion ;
- chargement dynamique ;
- JNI.

Rechercher notamment :

decrypt
encrypt
decode
encode
key
secret
token
cipher
digest
hash
AES
DES
RSA
EC
Base64
GCM
CBC
ECB
CTR
PBKDF2
SHA
MD5
HMAC

## SORTIE

Créer :

analysis/methods.json

Chaque méthode importante doit être accompagnée d'une preuve et d'un niveau de confiance.
