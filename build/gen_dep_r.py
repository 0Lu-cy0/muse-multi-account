#!/usr/bin/env python3
"""Generate per-package R.java from aapt2 link R.txt, filtered by each
library's own R.txt name set (AGP-style: library R fields are non-final so
the consumer-link IDs win over the library's own link IDs).
Usage: gen_dep_r.py host_R.txt out_dir pkg1:lib_R.txt pkg2:lib_R.txt ...
R.txt format (aapt2): 'int <type> <name> 0x...' and
'int[] styleable <Name> { 0x..., 0x... }' plus 'int styleable <Name>_<attr> 0x...'."""
import sys, re, os
from collections import defaultdict

host_rtxt, out_dir = sys.argv[1], sys.argv[2]
# parse host R.txt
host = {}  # (type,name) -> value string
styleable_arrays = {}  # name -> [ids]
lines = open(host_rtxt).read().splitlines()
for line in lines:
    m = re.match(r"int \[\] styleable (\S+) \{([^}]*)\}", line)
    if m:
        styleable_arrays[m.group(1)] = [x.strip() for x in m.group(2).split(",") if x.strip()]
        continue
    m = re.match(r"int (\S+) (\S+) (0x[0-9a-fA-F]+)", line)
    if m:
        host[(m.group(1), m.group(2))] = m.group(3)

for spec in sys.argv[3:]:
    pkg, lib_rtxt = spec.split(":", 1)
    # lib R.txt name set
    names = set()
    for line in open(lib_rtxt):
        m = re.match(r"int \[\] styleable (\S+) ", line)
        if m:
            names.add(("styleable", m.group(1)))
            continue
        m = re.match(r"int (\S+) (\S+) ", line)
        if m:
            names.add((m.group(1), m.group(2)))
    # group host values by type for this lib
    by_type = defaultdict(list)
    for (typ, name), val in host.items():
        if (typ, name) in names:
            by_type[typ].append((name, val))
    # styleable arrays for this lib
    sty = {n: v for n, v in styleable_arrays.items() if ("styleable", n) in names}
    pkg_dir = os.path.join(out_dir, *pkg.split("."))
    os.makedirs(pkg_dir, exist_ok=True)
    with open(os.path.join(pkg_dir, "R.java"), "w") as f:
        f.write("package %s;\n\npublic final class R {\n" % pkg)
        for typ in sorted(by_type):
            f.write("  public static final class %s {\n" % typ)
            for name, val in sorted(by_type[typ]):
                # styleable attr entries belong inside styleable class, not here
                if typ == "styleable":
                    continue
                f.write("    public static int %s=%s;\n" % (name, val))
            f.write("  }\n")
        if sty or any(t == "styleable" for t in by_type):
            f.write("  public static final class styleable {\n")
            for name, ids in sorted(sty.items()):
                f.write("    public static int[] %s={%s};\n" % (name, ",".join(ids)))
            for name, val in sorted(by_type.get("styleable", [])):
                f.write("    public static int %s=%s;\n" % (name, val))
            f.write("  }\n")
        f.write("}\n")
    print(f"R.java for {pkg}: types={sorted(by_type.keys())} styleable_arrays={len(sty)}")
