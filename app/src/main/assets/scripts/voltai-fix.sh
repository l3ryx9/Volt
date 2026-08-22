#!/bin/bash
# VoltAI — Auto-réparation des dépendances.
# Exécuté DANS le runtime Termux embarqué (via proot). Pilote Ubuntu via proot-distro.
# No-op si tout est correct, sinon répare : environnement → apt → outils → wheels → JRE.
# Usage : voltai-fix.sh <toolsDir_hote>
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
# Étape 0 : environnement complet ?
# ---------------------------------------------------------------------------
if [ ! -x "$PREFIX/bin/proot-distro" ] || [ ! -d "$ROOTFS/etc" ] || ! U test -f /root/voltai/.installed 2>/dev/null; then
  log 5 "Environnement incomplet → installation complète"
  sh "$TOOLS/voltai-setup.sh" "$TOOLS"
  exit $?
fi

# ---------------------------------------------------------------------------
# Étape 1 : dépendances apt
# ---------------------------------------------------------------------------
MISSING=""
for p in zip unrar wget file ca-certificates; do
  U sh -c "command -v $p >/dev/null 2>&1" || MISSING="$MISSING $p"
done
if [ -n "$MISSING" ]; then
  log 25 "Installation apt :$MISSING"
  U sh -c "export DEBIAN_FRONTEND=noninteractive; apt-get update -y >/dev/null 2>&1 && apt-get install -y --no-install-recommends $MISSING >/dev/null 2>&1" \
    && echo "[VOLTAI|FIXED|apt installes :$MISSING]" || err "Installation apt échouée"
fi

# ---------------------------------------------------------------------------
# Étape 2 : binaires / wrappers manquants
# ---------------------------------------------------------------------------
BINS="apktool smali baksmali jadx 7z xz zstd rg git curl openssl python3.14"
MISSING_BINS=""
for b in $BINS; do
  U sh -c "command -v $b >/dev/null 2>&1" || MISSING_BINS="$MISSING_BINS $b"
done
if [ -n "$MISSING_BINS" ]; then
  log 45 "Redéploiement des outils :$MISSING_BINS"
  U sh -c "mkdir -p '$STAGING'" || err "mkdir staging (fix)"
  cp "$TOOLS/7zz" "$TOOLS/$ARCHIVE" "$ROOTFS$STAGING/" || err "Copie archive (fix)"
  U sh -c "cd '$STAGING' && ./7zz x -y -o/root/voltai $ARCHIVE 'usr/bin/*' 'usr/share/voltai/*' 'usr/lib/python3.14/*' >/dev/null 2>&1"
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
      [ -e "$VHOME/bin/$t" ] && ln -sf "$VHOME/bin/$t" "/usr/local/bin/$t"
    done
    echo "$VHOME/lib" > /etc/ld.so.conf.d/voltai.conf
    ldconfig 2>/dev/null || true
  '
  U test -x "$VHOME/bin/apktool" || err "Redéploiement des outils échoué"
  echo "[VOLTAI|FIXED|outils redéployés]"
fi

# ---------------------------------------------------------------------------
# Étape 3 : wheels python
# ---------------------------------------------------------------------------
if ! U "$VHOME/bin/python3.14" -c "import androguard" >/dev/null 2>&1; then
  log 75 "Réinstallation des paquets Python…"
  U sh -c "cd /root/voltai && $VHOME/bin/python3.14 -m pip install --break-system-packages --no-index --no-build-isolation --find-links $VHOME/share/voltai/wheels androguard frida-tools numpy click setuptools wheel" >/dev/null 2>&1 \
    && echo "[VOLTAI|FIXED|wheels python]" || log 80 "Wheels python : partiel"
fi

# ---------------------------------------------------------------------------
# Étape 4 : JRE
# ---------------------------------------------------------------------------
if ! U test -x "$VHOME/lib/jvm/java-17-openjdk/bin/java"; then
  log 90 "Restauration du JRE…"
  U sh -c "mkdir -p '$STAGING'" || err "mkdir staging (jre)"
  cp "$TOOLS/7zz" "$TOOLS/$ARCHIVE" "$ROOTFS$STAGING/" || err "Copie archive (jre)"
  U sh -c "cd '$STAGING' && ./7zz x -y -o/root/voltai $ARCHIVE 'usr/lib/jvm/*' >/dev/null 2>&1" \
    && echo "[VOLTAI|FIXED|JRE restauré]" || err "Restauration JRE échouée"
fi

U sh -c "touch /root/voltai/.installed" 2>/dev/null
U sh -c "rm -rf '$STAGING'" >/dev/null 2>&1 || true

log 100 "Réparation terminée"
echo "[VOLTAI|DONE]"