# -*- coding: utf-8 -*-
"""把 shots/*.jpg 压缩到宽 1280 / q80，输出到 web/，供手册内嵌"""
import os
from PIL import Image

SRC = r"D:\Code\2026AI\AIA_MDM\Essilor_MDM\plus-ui\docs\manual-kit\shots"
DST = r"D:\Code\2026AI\AIA_MDM\Essilor_MDM\plus-ui\docs\manual-kit\web"
os.makedirs(DST, exist_ok=True)
total = 0
for f in sorted(os.listdir(SRC)):
    if not f.lower().endswith(('.jpg', '.png')):
        continue
    im = Image.open(os.path.join(SRC, f)).convert('RGB')
    if im.width > 1280:
        im = im.resize((1280, int(im.height * 1280 / im.width)), Image.LANCZOS)
    out = os.path.join(DST, os.path.splitext(f)[0] + '.jpg')
    im.save(out, 'JPEG', quality=80, optimize=True)
    total += os.path.getsize(out)
print('files:', len(os.listdir(DST)), 'total KB:', total // 1024)
