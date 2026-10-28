#!/usr/bin/env python3
"""Merge Bcore library manifest into host manifest, like AGP manifest merger
(minimal version): expand relative component names to top.niunaijun.blackbox.*,
substitute ${applicationId} with host package, strip tools:* attrs, dedupe.
v3 changes (audit 2026-10-27):
- Drop signature/system permissions a normal app can never be granted
  (INSTALL_PACKAGES, READ_LOGS, INTERACT_ACROSS_USERS, DEVICE_POWER,
  MOUNT_UNMOUNT_FILESYSTEMS, CLEAR_APP_USER_DATA, DELETE_PACKAGES, etc.).
  They were dead weight in the merged manifest and made the host look
  like it claimed system privileges it does not have. Dangerous/runtime
  permissions a guest app may actually need (camera, mic, location,
  storage, contacts, etc.) are KEPT, because the engine proxies guest
  permission checks through the host's declared permissions.
- ProxyContentProvider components are set exported=false. Guest code
  runs in the same application/UID as the host, so same-app provider
  calls still work, while external apps can no longer reach the
  provider at all. This is defence in depth on top of the caller
  check in ProxyContentProvider.call(). If phone testing shows guest
  launch breaks because of this, revert ONLY this provider change and
  keep the caller check.
- Activities/services stay exported=true where Bcore set them: the
  system must be able to launch proxy activities/services, and
  ProxyActivity finishes immediately unless its intent carries a
  valid proxy record, so an external intent without one does nothing."""
import sys, xml.etree.ElementTree as ET
AND = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
HOST_PKG = "com.example.musecontainer"
LIB_PKG = "top.niunaijun.blackbox"
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")

DENY_PERMISSIONS = {
    "android.permission.INSTALL_PACKAGES",
    "android.permission.DELETE_PACKAGES",
    "android.permission.CLEAR_APP_USER_DATA",
    "android.permission.CLEAR_APP_CACHE",
    "android.permission.READ_LOGS",
    "android.permission.INTERACT_ACROSS_USERS",
    "android.permission.INTERACT_ACROSS_USERS_FULL",
    "android.permission.DEVICE_POWER",
    "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
    "android.permission.MOUNT_FORMAT_FILESYSTEMS",
    "android.permission.ACCOUNT_MANAGER",
    "android.permission.MANAGE_ACCOUNTS",
    "android.permission.AUTHENTICATE_ACCOUNTS",
    "android.permission.BIND_APPWIDGET",
    "android.permission.BIND_DIRECTORY_SEARCH",
    "android.permission.BIND_VPN_SERVICE",
    "android.permission.HARDWARE_TEST",
    "android.permission.PERSISTENT_ACTIVITY",
    "android.permission.GET_TASKS",
    "android.permission.ACCESS_CACHE_FILESYSTEM",
    "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION",
    "android.permission.EXPAND_STATUS_BAR",
    "android.permission.DISABLE_KEYGUARD",
    "android.permission.CHANGE_CONFIGURATION",
    "android.permission.ACCESS_DOWNLOAD_MANAGER",
    "android.permission.ACCESS_GPS",
    "android.permission.ACCESS_WIMAX_STATE",
    "android.permission.CHANGE_WIMAX_STATE",
    "android.permission.READ_OWNER_DATA",
    "android.permission.READ_PRECISE_PHONE_STATE",
    "android.permission.READ_SOCIAL_STREAM",
    "android.permission.WRITE_SOCIAL_STREAM",
}

host_path, lib_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
host = ET.parse(host_path); hroot = host.getroot()
lib = ET.parse(lib_path); lroot = lib.getroot()
happ = hroot.find("application")
seen = set()
def key(elem):
    return (elem.tag, elem.get(AND + "name"))
for elem in happ:
    seen.add(key(elem))
# top-level: uses-permission, uses-feature, etc. (not application)
for elem in list(lroot):
    if elem.tag == "application":
        continue
    k = key(elem)
    if k in seen:
        continue
    if elem.tag == "uses-permission" and elem.get(AND + "name") in DENY_PERMISSIONS:
        continue
    seen.add(k)
    hroot.insert(list(hroot).index(happ), elem)
# application children from lib
for elem in list(lroot.find("application")):
    # deep copy via serialise/parse
    import copy
    e = copy.deepcopy(elem)
    # expand relative names + placeholders, strip tools attrs, recursively
    def fix(node):
        for attr in list(node.attrib):
            if attr.startswith(TOOLS):
                del node.attrib[attr]
            else:
                v = node.attrib[attr]
                if "${applicationId}" in v:
                    v = v.replace("${applicationId}", HOST_PKG)
                if attr == AND + "name" and v.startswith("."):
                    v = LIB_PKG + v
                node.attrib[attr] = v
        for child in node:
            fix(child)
    fix(e)
    # v3: providers are same-app only (see module docstring).
    if e.tag == "provider":
        e.set(AND + "exported", "false")
    # v4.1: every component must explicitly declare exported on
    # targetSdk 34. LauncherActivity was the only component missing
    # it in v4, and strict Samsung PackageParser may reject the
    # whole package for that. It is launched internally by the
    # engine via an explicit intent, so exported=false is correct.
    if e.tag in ("activity", "service", "provider", "receiver") and e.get(AND + "exported") is None:
        e.set(AND + "exported", "false")
    k = key(e)
    if k in seen:
        continue
    seen.add(k)
    happ.append(e)
host.write(out_path, encoding="utf-8", xml_declaration=True)
print("merged manifest written:", out_path)
# count
r = ET.parse(out_path).getroot()
from collections import Counter
print("top-level:", Counter(c.tag for c in r))
print("app children:", Counter(c.tag for c in r.find("application")))
