"""Download official Noto and PDF.js assets through the user's local HTTP proxy.

Run once before prepare-ai-release.js. Include common Chinese and catalog glyphs.
"""
from pathlib import Path
import os, subprocess, urllib.request, sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / '.local/ai-vendor/python'))
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.cu2quPen import Cu2QuPen

root = Path(__file__).resolve().parent.parent
out = root / '.local' / 'ai-vendor'
out.mkdir(parents=True, exist_ok=True)
proxy = os.environ.get('HTTPS_PROXY', 'http://127.0.0.1:7890')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({'http': proxy, 'https': proxy}))
for name, url in {
    'NotoSansCJKsc-full.otf': 'https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf',
    'OFL.txt': 'https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/LICENSE',
}.items():
    target = out / name
    if not target.exists():
        with opener.open(url, timeout=60) as response:
            target.write_bytes(response.read())
font = out / 'NotoSansCJKsc-Regular.otf'
if not font.exists():
    options = subset.Options()
    # Flatten CFF subroutines for the pure-JavaScript rasterizer.
    options.desubroutinize = True
    f = subset.load_font(str(out / 'NotoSansCJKsc-full.otf'), options)
    subsetter = subset.Subsetter(options=options)
    chars = set(range(32, 256)) | set(range(0x3000, 0x3100)) | set(range(0xff00, 0xffef))
    for a in range(0xa1, 0xf8):
        for b in range(0xa1, 0xff):
            try:
                chars.update(ord(c) for c in bytes([a, b]).decode('gb2312'))
            except UnicodeDecodeError:
                pass
    for sql in (root.parent / 'movie-ticket-backend/src/main/resources/db').rglob('*.sql'):
        chars.update(ord(c) for c in sql.read_text(encoding='utf-8', errors='ignore'))
    subsetter.populate(unicodes=chars)
    subsetter.subset(f)
    subset.save_font(f, str(font), options)
ttf = out / 'NotoSansCJKsc-Regular.ttf'
if not ttf.exists():
    original = TTFont(font)
    glyph_set = original.getGlyphSet()
    glyphs = {}
    for name in original.getGlyphOrder():
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, max_err=1.0, reverse_direction=True))
        glyphs[name] = pen.glyph()
    builder = FontBuilder(original['head'].unitsPerEm, isTTF=True)
    builder.setupGlyphOrder(original.getGlyphOrder())
    builder.setupCharacterMap(original.getBestCmap())
    builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics(original['hmtx'].metrics)
    builder.setupHorizontalHeader(ascent=original['hhea'].ascent, descent=original['hhea'].descent)
    builder.setupNameTable({'familyName': 'NotoSansSCCloud', 'styleName': 'Regular', 'uniqueFontIdentifier': 'NotoSansSCCloud-Regular', 'fullName': 'NotoSansSCCloud Regular', 'psName': 'NotoSansSCCloud-Regular'})
    builder.setupOS2(sTypoAscender=original['hhea'].ascent, sTypoDescender=original['hhea'].descent, usWinAscent=original['hhea'].ascent, usWinDescent=abs(original['hhea'].descent))
    builder.setupPost()
    builder.save(ttf)
env = {**os.environ, 'HTTP_PROXY': proxy, 'HTTPS_PROXY': proxy, 'ALL_PROXY': proxy, 'NO_PROXY': 'localhost,127.0.0.1,::1'}
subprocess.run(['npm.cmd' if os.name == 'nt' else 'npm', 'install', '--save-exact', '--ignore-scripts', 'pdfjs-dist@4.8.69'], cwd=out, env=env, check=True, shell=os.name == 'nt')
print('Official Noto font and PDF.js prepared:', font.stat().st_size, 'font bytes')
