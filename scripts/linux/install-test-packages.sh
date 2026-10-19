#!/usr/bin/env bash
#
# Installs what the tests of lwjwae need on Ubuntu 24.04, as root: Dockerfile.test runs it, and so
# does the Linux job of CI, so that the two never differ.
#
#   sudo scripts/linux/install-test-packages.sh
#
# GTK 3 and 4 with WebKitGTK 4.1 and 6.0; Xvfb and xfwm4, the display and the window manager, with
# xprop, which tells when xfwm4 is up; labwc, the compositor of the Wayland tests;
# ImageMagick for the screenshots; a session bus with dunst, the notification server, and GNOME
# Keyring, the Secret Service; the desktop portal with its GTK backend, which serves the color
# scheme to the theme tests; python3-gi and xdotool, which drag files onto a window; rsync, which
# copies the working tree into the container.
#
set -euo pipefail
apt-get update
apt-get install -y --no-install-recommends \
    libgtk-3-0t64 libwebkit2gtk-4.1-0 libgtk-4-1 libwebkitgtk-6.0-4 \
    imagemagick xvfb xfwm4 x11-utils labwc dbus-daemon dunst gnome-keyring \
    xdg-desktop-portal xdg-desktop-portal-gtk gsettings-desktop-schemas libglib2.0-bin \
    dconf-gsettings-backend python3-gi gir1.2-gtk-3.0 xdotool rsync

# Without a desktop to name, the portal picks no backend and serves no settings: this file picks the
# one for GTK, which has the color scheme of the user.
mkdir -p /etc/xdg-desktop-portal
printf '[preferred]\ndefault=gtk\n' > /etc/xdg-desktop-portal/portals.conf
