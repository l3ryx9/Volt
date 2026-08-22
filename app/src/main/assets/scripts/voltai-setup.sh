#!/bin/bash
# VoltAI — Installation automatisée du runtime + outils (barre de progression).
# Exécuté DANS le runtime Termux embarqué (via proot). Pilote Ubuntu via proot-distro.
# Usage : voltai-setup.sh <toolsDir_hote>
set -u

TOOLS="$1"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
ROOTFS="$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu"
VHOME="/root/voltai/usr"
STAGING="/usr/local/share/voltai-tools-install"
ARCHIVE="voltai-tools.7z"

log() { echo "[VOLTAI|PROGRESS|$1|$2]"; }
err() { echo "[VOLTAI|ERROR|$1]"; exit 1; }

U() { proot-distro login ubuntu -- "$@"; }

# ---------------------------------------------------------------------------
# Phase 1 : proot-distro
# ---------------------------------------------------------------------------
if [ ! -x "$PREFIX/bin/proot-distro" ]; then
  log 10 "Installation de proot-distro…"
  tries=0
  pkg_output=""
  until pkg_output="$(pkg update -y 2>&1)"; do
    tries=$((tries + 1))
    if [ "$tries" -ge 3 ]; then
      detail="$(printf '%s\n' "$pkg_output" | tail -n 8 | tr '\n' ' ' | sed 's/[[:space:]][[:space:]]*/ /g')"
      [ -n "$detail" ] || detail="aucun détail retourné par pkg"
      err "pkg update a échoué : $detail"
    fi
    log 10 "Nouvel essai pkg update ($tries)…"
  done
  pkg_output="$(pkg install -y proot-distro 2>&1)" ||
    err "pkg install proot-distro a échoué : $(printf '%s\n' "$pkg_output" | tail -n 8 | tr '\n' ' ' | sed 's/[[:space:]][[:space:]]*/ /g')"
fi
# Vérification : proot-distro est bien exécutable
[ -x "$PREFIX/bin/proot-distro" ] || err "proot-distro absent après installation"

# ---------------------------------------------------------------------------
# Phase 2 : Ubuntu 24.04
# ---------------------------------------------------------------------------
if [ ! -d "$ROOTFS/etc" ]; then
  log 20 "Téléchargement d'Ubuntu 24.04 (réseau requis)…"
  # Nettoyage d'un rootfs potentiellement corrompu par un téléchargement
  # interrompu lors d'une tentative précédente.
  if [ -d "$ROOTFS" ]; then
    log 20 "Nettoyage du rootfs incomplet…"
    rm -rf "$ROOTFS"
  fi
  tries=0
  until proot-distro install ubuntu:24.04 >/dev/null 2>&1; do
    tries=$((tries + 1))
    [ "$tries" -ge 3 ] && err "Installation d'Ubuntu échouée après 3 essais"
    # Nettoyage entre les tentatives pour repartir d'un état propre
    rm -rf "$ROOTFS"
    log 20 "Nouvel essai Ubuntu ($tries)…"
  done
  # Vérification explicite : le rootfs doit contenir /etc et /usr/bin
  [ -d "$ROOTFS/etc" ] && [ -d "$ROOTFS/usr/bin" ] \
    || err "Rootfs Ubuntu incomplet après installation (etc ou usr/bin absent)"
fi

# Vérification : proot-distro login fonctionne réellement
U true 2>/dev/null || err "proot-distro login ubuntu échoue (rootfs corrompu ?)"

# ---------------------------------------------------------------------------
# Phase 3 : copie du bundle dans le rootfs
# ---------------------------------------------------------------------------
log 35 "Copie du bundle vers Ubuntu…"
U sh -c "mkdir -p '$STAGING'" || err "Impossible de créer le répertoire de staging"
cp "$TOOLS/7zz" "$TOOLS/$ARCHIVE" "$ROOTFS$STAGING/" || err "Copie du bundle échouée"
U chmod 755 "$STAGING/7zz" || err "chmod 7zz échoué"

# ---------------------------------------------------------------------------
# Phase 4 : extraction dans /root/voltai (progression réelle via 7zz -bsp1)
# ---------------------------------------------------------------------------
log 45 "Décompression du bundle…"
U sh -c "cd '$STAGING' && ./7zz x -y -bsp1 -o/root/voltai $ARCHIVE" 2>&1 | tr '\r' '\n' | while IFS= read -r line; do
  pct="${line%%%*}"
  pct="${pct##* }"
  case "$pct" in
    '' | *[!0-9]*) ;;
    *) log $((45 + pct * 25 / 100)) "Décompression ${pct}%…" ;;
  esac
done
U test -x "$VHOME/bin/apktool" || err "Décompression du bundle échouée (apktool absent)"

# ---------------------------------------------------------------------------
# Phase 5 : configuration des outils (wrappers, JRE, python, env)
# ---------------------------------------------------------------------------
log 72 "Configuration des outils…"
U sh -c '
VHOME="/root/voltai/usr"
for w in "$VHOME"/bin/*; do
  [ -f "$w" ] || continue
  if head -c2 "$w" | grep -q "#!"; then
    sed -i \
      -e "s#/usr/share/voltai#$VHOME/usr/share/voltai#g" \
      -e "s#/usr/lib/jvm/java-17-openjdk#$VHOME/usr/lib/jvm/java-17-openjdk#g" \
      -e "s#/usr/bin/python3.14#$VHOME/usr/bin/python3.14#g" \
      -e "s#/usr/bin/7zz#$VHOME/usr/bin/7zz#g" \
      -e "s#/usr/bin/xz#$VHOME/usr/bin/xz#g" \
      -e "s#/usr/lib/python3.14/site-packages#$VHOME/usr/lib/python3.14/site-packages#g" \
      "$w"
    chmod 755 "$w"
  fi
done
mkdir -p /usr/local/bin
for t in apktool smali baksmali jadx jadx-gui 7z 7zz xz unxz zstd unzstd rg git curl openssl python python3 python3.14 pip pip3 androguard dalivm paranoid; do
  if [ -e "$VHOME/bin/$t" ]; then ln -sf "$VHOME/bin/$t" "/usr/local/bin/$t"; fi
done
ln -sf "$VHOME/lib/jvm/java-17-openjdk/bin/java" /usr/local/bin/java
ln -sf "$VHOME/lib/jvm/java-17-openjdk/bin/javap" /usr/local/bin/javap
echo "$VHOME/lib" > /etc/ld.so.conf.d/voltai.conf
ldconfig 2>/dev/null || true
cat > /etc/profile.d/voltai.sh <<ENV
export VOLTAI_HOME="$VHOME"
export JAVA_HOME="$VHOME/lib/jvm/java-17-openjdk"
export PATH="$VHOME/bin:\$PATH"
export PYTHONPATH="$VHOME/lib/python3.14/site-packages:\$PYTHONPATH"
export LD_LIBRARY_PATH="$VHOME/lib:\$LD_LIBRARY_PATH"
ENV
'

# ---------------------------------------------------------------------------
# Phase 6 : paquets Python (wheels offline, interpréteur 3.14 embarqué)
# ---------------------------------------------------------------------------
log 80 "Installation des paquets Python (wheels)…"
if ! U "$VHOME/bin/python3.14" -c "import androguard" >/dev/null 2>&1; then
  U sh -c "cd /root/voltai && $VHOME/bin/python3.14 -m pip install --break-system-packages --no-index --no-build-isolation --find-links $VHOME/share/voltai/wheels androguard frida-tools numpy click setuptools wheel" >/dev/null 2>&1 \
    || log 85 "Wheels python : partiel (non bloquant)"
fi

# ---------------------------------------------------------------------------
# Phase 7 : dépendances apt (système)
# ---------------------------------------------------------------------------
log 88 "Installation des dépendances apt…"
tries=0
until U sh -c "export DEBIAN_FRONTEND=noninteractive; apt-get update -y >/dev/null 2>&1 && apt-get install -y --no-install-recommends zip unrar wget file ca-certificates >/dev/null 2>&1"; do
  tries=$((tries + 1))
  [ "$tries" -ge 3 ] && err "Installation des dépendances apt échouée"
  log 88 "Nouvel essai apt ($tries)…"
done
# Vérification : les paquets critiques sont installables
U sh -c "command -v zip >/dev/null 2>&1 && command -v wget >/dev/null 2>&1" \
  || log 89 "Certaines dépendances apt n'ont pas pu être vérifiées (non bloquant)"

# ---------------------------------------------------------------------------
# Phase 8 : finalisation + vérifications
# ---------------------------------------------------------------------------
log 96 "Finalisation…"
U sh -c 'for t in apktool smali baksmali jadx 7z xz zstd rg git curl openssl python3.14 androguard dalivm paranoid java; do
  if command -v "$t" >/dev/null 2>&1; then echo "[VOLTAI|VERIFY|$t OK]"; else echo "[VOLTAI|VERIFY|$t ABSENT]"; fi
done'
U sh -c "touch /root/voltai/.installed" || err "Écriture du marqueur échouée"

# Vérification finale : le script de réparation garantit que tout est en
# place (apt, wrappers, wheels, JRE). No-op si tout est correct.
log 97 "Vérification finale de l'environnement…"
sh "$TOOLS/voltai-fix.sh" "$TOOLS" || err "Vérification finale échouée"

U sh -c "rm -rf '$STAGING'" >/dev/null 2>&1 || true

log 100 "Installation terminée"
echo "[VOLTAI|DONE]"