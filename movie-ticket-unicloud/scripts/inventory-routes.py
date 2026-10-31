"""List Spring controller routes so the uniCloud rewrite can track parity."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTROLLERS = ROOT / "movie-ticket-backend/src/main/java/com/movie/ticket/controller"
OUTPUT = Path(__file__).resolve().parents[1] / "api-inventory.json"

routes = []
for source in sorted(CONTROLLERS.glob("*Controller.java")):
    text = source.read_text(encoding="utf-8")
    class_match = re.search(r'@RequestMapping\("([^"]+)"\)', text)
    if not class_match:
        continue
    prefix = class_match.group(1)
    for match in re.finditer(r'@(Get|Post|Put|Delete)Mapping(?:\s*\(([^)]*)\))?', text):
        quoted = re.search(r'"([^"]*)"', match.group(2) or "")
        suffix = quoted.group(1) if quoted else ""
        routes.append({
            "method": match.group(1).upper(),
            "path": "/api" + prefix + (suffix if suffix.startswith("/") or not suffix else "/" + suffix),
            "source": source.name,
        })

OUTPUT.write_text(json.dumps(routes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Recorded {len(routes)} controller routes in {OUTPUT}")
