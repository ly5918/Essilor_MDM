# -*- coding: utf-8 -*-
"""渲染单文件 HTML 手册：左侧目录 + base64 内嵌截图"""
import base64, html, mimetypes, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.join(HERE, 'web')
SECTIONS_DIR = os.path.join(HERE, 'sections')
OUT = r"D:\Code\2026AI\AIA_MDM\Essilor_MDM\plus-ui\docs\Essilor_CMD_POC_全功能操作手册.html"

sys.path.insert(0, HERE)
from manual_content import SECTIONS

_img_cache = {}
def img_data(name):
    if name not in _img_cache:
        path = os.path.join(WEB, name + '.jpg')
        with open(path, 'rb') as f:
            _img_cache[name] = base64.b64encode(f.read()).decode()
    return _img_cache[name]

_file_cache = {}
def file_data(fname):
    if fname not in _file_cache:
        path = os.path.join(HERE, fname)
        mime = mimetypes.guess_type(fname)[0]
        if not mime:
            mime = 'application/octet-stream'
        with open(path, 'rb') as f:
            _file_cache[fname] = (mime, base64.b64encode(f.read()).decode())
    return _file_cache[fname]

_raw_cache = {}
def raw_html(fname):
    """注入 sections/ 下的 HTML 片段原文（用于变更记录这类含复杂表格的章节）。"""
    if fname not in _raw_cache:
        with open(os.path.join(SECTIONS_DIR, fname), encoding='utf-8') as f:
            _raw_cache[fname] = f.read()
    return _raw_cache[fname]

def render_block(block, idx):
    kind = block[0]
    if kind == 'raw':
        return raw_html(block[1])
    if kind == 'p':
        return f"<p>{block[1]}</p>"
    if kind == 'h3':
        return f"<h3>{block[1]}</h3>"
    if kind == 'li':
        items = ''.join(f"<li>{i}</li>" for i in block[1])
        return f"<ul class='dot-list'>{items}</ul>"
    if kind == 'tip':
        return f"<div class='note tip'><span class='note-ic'>💡</span><div>{block[1]}</div></div>"
    if kind == 'warn':
        return f"<div class='note warn'><span class='note-ic'>⚠️</span><div>{block[1]}</div></div>"
    if kind == 'img':
        name, cap = block[1], block[2]
        return (f"<figure class='shot'><img loading='lazy' src='data:image/jpeg;base64,{img_data(name)}' alt='{html.escape(cap)}'>"
                f"<figcaption>{html.escape(cap)}</figcaption></figure>")
    if kind == 'table':
        head, rows = block[1], block[2]
        th = ''.join(f"<th>{h}</th>" for h in head)
        trs = ''.join("<tr>" + ''.join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
        return f"<div class='tbl-wrap'><table><thead><tr>{th}</tr></thead><tbody>{trs}</tbody></table></div>"
    if kind == 'files':
        rows = []
        for fname, desc in block[1]:
            mime, b64 = file_data(fname)
            rows.append(f"<tr><td><span class='fname'>{html.escape(fname)}</span><a class='file-dl' href='data:{mime};base64,{b64}' download='{html.escape(fname)}' title='点击下载 {html.escape(fname)}'>⬇ 下载</a></td><td>{desc}</td></tr>")
        return f"<div class='tbl-wrap'><table class='file-table'><thead><tr><th>文件</th><th>用途</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div>"
    return ''

# ---------- 组装目录与正文 ----------
toc = []
body = []
fig_no = [1]
for sid, title, blocks in SECTIONS:
    toc.append(f"<a class='toc-sec' href='#{sid}'>{html.escape(title)}</a>")
    body.append(f"<section id='{sid}'><h2 class='sec-title'>{html.escape(title)}</h2>")
    # 收集 h2 子目录
    sub = [b[1] for b in blocks if b[0] == 'h2']
    if sub:
        toc.append("<div class='toc-sub'>")
        for i, s in enumerate(sub, 1):
            toc.append(f"<a class='toc-item' href='#{sid}-{i}'>{i}. {html.escape(s)}</a>")
        toc.append("</div>")
    h2n = 0
    for b in blocks:
        if b[0] == 'h2':
            h2n += 1
            body.append(f"<h2 id='{sid}-{h2n}' class='body-h2'>{h2n}. {html.escape(b[1])}</h2>")
        else:
            body.append(render_block(b, fig_no))
    body.append("</section>")

CSS = """
:root{--brand:#14508c;--brand2:#1d6ab5;--ink:#22303e;--mut:#66788a;--line:#e4eaf0;--bg:#f3f6f9;}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:"Microsoft YaHei","PingFang SC",system-ui,sans-serif;color:var(--ink);background:var(--bg);line-height:1.75}
.layout{display:flex;min-height:100vh}
/* 左侧目录 */
aside{width:300px;min-width:300px;background:#0f2c49;color:#cfe0f0;position:sticky;top:0;height:100vh;overflow-y:auto;padding:22px 0 40px}
aside .brand{padding:0 22px 16px;border-bottom:1px solid rgba(255,255,255,.12)}
aside .brand b{display:block;color:#fff;font-size:18px;letter-spacing:1px}
aside .brand span{font-size:12px;color:#8fb4d6;letter-spacing:2px}
aside .toc{padding:14px 12px}
.toc-sec{display:block;color:#fff;font-size:14.5px;font-weight:600;padding:9px 12px;border-radius:8px;text-decoration:none;margin-top:8px}
.toc-sec:hover{background:rgba(255,255,255,.1)}
.toc-sub{margin:2px 0 6px}
.toc-item{display:block;color:#9fc0dd;font-size:12.8px;padding:5px 12px 5px 22px;text-decoration:none;border-left:2px solid transparent}
.toc-item:hover{color:#fff;border-left-color:#4d9fe8}
.toc-item.active{color:#fff;border-left-color:#f5a623;background:rgba(255,255,255,.06)}
/* 主体 */
main{flex:1;min-width:0;padding:34px 46px 90px;max-width:1120px;margin:0 auto}
.sec-title{font-size:26px;color:var(--brand);border-left:6px solid var(--brand);padding-left:14px;margin:10px 0 6px}
.body-h2{font-size:20px;color:var(--brand2);margin:38px 0 12px;padding-bottom:8px;border-bottom:2px solid var(--line)}
h3{font-size:16.5px;margin:22px 0 8px;color:#173a5c}
p{margin:10px 0}
.dot-list{margin:10px 0 10px 4px;list-style:none}
.dot-list li{position:relative;padding:4px 0 4px 20px}
.dot-list li::before{content:"";position:absolute;left:2px;top:14px;width:7px;height:7px;border-radius:50%;background:var(--brand2)}
.note{display:flex;gap:10px;border-radius:10px;padding:12px 16px;margin:14px 0;font-size:14.5px}
.note.tip{background:#eef7ee;border:1px solid #cfe8cf}
.note.warn{background:#fdf3e7;border:1px solid #f3ddb8}
.note-ic{font-size:18px;line-height:1.5}
.shot{margin:16px 0 26px}
.shot img{width:100%;border:1px solid var(--line);border-radius:12px;box-shadow:0 3px 14px rgba(20,60,100,.08);display:block}
.shot figcaption{font-size:13px;color:var(--mut);text-align:center;margin-top:8px}
.tbl-wrap{overflow-x:auto;margin:14px 0;border-radius:10px;border:1px solid var(--line)}
table{border-collapse:collapse;width:100%;font-size:14px;background:#fff}
th{background:var(--brand);color:#fff;text-align:left;padding:9px 12px;font-weight:600;white-space:nowrap}
td{padding:9px 12px;border-top:1px solid var(--line);vertical-align:top}
tbody tr:nth-child(even){background:#f7fafc}
code{background:#eef2f6;border:1px solid var(--line);border-radius:4px;padding:1px 6px;font-size:13px;color:#a1481f;font-family:Consolas,monospace}
b{color:#0f3f6e}
.file-table td:first-child{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.fname{font-family:Consolas,monospace;font-size:13.5px;color:#0f3f6e;word-break:break-all}
.file-dl{display:inline-flex;align-items:center;gap:4px;background:var(--brand2);color:#fff;padding:4px 12px;border-radius:6px;text-decoration:none;font-size:13px;white-space:nowrap;transition:background .2s}
.file-dl:hover{background:#104a7d}
.topbar{position:sticky;top:0;z-index:5;background:linear-gradient(90deg,var(--brand),var(--brand2));color:#fff;display:flex;align-items:center;gap:14px;padding:12px 28px}
.topbar .t{font-size:17px;font-weight:700;letter-spacing:1px}
.topbar .s{font-size:12.5px;opacity:.85}
@media(max-width:900px){aside{display:none}main{padding:20px 16px 60px}}
"""

JS = """
// 滚动高亮当前小节
const links=[...document.querySelectorAll('.toc-item')];
const targets=links.map(a=>document.querySelector(a.getAttribute('href'))).filter(Boolean);
addEventListener('scroll',()=>{
  let cur=targets[0],y=scrollY+120;
  for(const t of targets){if(t.offsetTop<=y)cur=t;}
  links.forEach(a=>a.classList.toggle('active',a.getAttribute('href')==='#'+(cur&&cur.id)));
},{passive:true});
"""

HTML = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Essilor CMD POC · 全功能操作手册</title>
<style>{CSS}</style>
</head>
<body>
<div class="topbar"><span class="t">ESSILOR · CMD POC 客户主数据管理</span><span class="s">全功能操作手册 V1.0（截图版 · 可离线浏览）</span></div>
<div class="layout">
<aside>
  <div class="brand"><b>📋 操作手册 · 目录</b><span>CMD POC MASTER GUIDE</span></div>
  <nav class="toc">{''.join(toc)}</nav>
</aside>
<main>{''.join(body)}
<footer style="margin-top:60px;color:#8a9aa9;font-size:12.5px;text-align:center">Essilor CMD POC 全功能操作手册 · 依据《总体设计方案 V6.1》与系统实际实现编写 · 截图均为系统实拍 · 支持离线浏览</footer>
</main>
</div>
<script>{JS}</script>
</body>
</html>"""

with open(OUT, 'w', encoding='utf-8') as f:
    f.write(HTML)
print('written:', OUT, 'size MB:', round(os.path.getsize(OUT) / 1048576, 1))
