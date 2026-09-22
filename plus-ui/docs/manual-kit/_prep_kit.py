# -*- coding: utf-8 -*-
"""准备操作手册资料包：营业执照示例图 + 下载系统模板 + 校验演示数据表头"""
import io, os, urllib.request
from openpyxl import load_workbook

ROOT = r"D:\Code\2026AI\AIA_MDM\Essilor_MDM\plus-ui\docs\manual-kit"
os.makedirs(ROOT, exist_ok=True)

# ---------- 1. 营业执照示例图 ----------
from PIL import Image, ImageDraw, ImageFont

def font(sz, bold=False):
    names = ["msyhbd.ttc" if bold else "msyh.ttc", "simhei.ttf", "simsun.ttc"]
    for n in names:
        try:
            return ImageFont.truetype("C:/Windows/Fonts/" + n, sz)
        except Exception:
            continue
    return ImageFont.load_default()

W, H = 1480, 1050
img = Image.new("RGB", (W, H), "#f7f3e8")
d = ImageDraw.Draw(img)
# 边框
d.rectangle([30, 30, W-30, H-30], outline="#b98a2f", width=6)
d.rectangle([44, 44, W-44, H-44], outline="#d9b96a", width=2)
# 顶部国徽区（简单五角星圆代替，避免版权问题）
d.ellipse([W//2-70, 60, W//2+70, 200], fill="#d3360b")
# 标题
d.text((W//2, 235), "营 业 执 照", font=font(72, True), fill="#8b1a1a", anchor="mm")
d.text((W//2, 320), "副本  统一社会信用代码 91310000MA1K35X78L", font=font(28), fill="#333", anchor="mm")

fields = [
    ("名　　　称", "上海晨曦眼镜有限公司"),
    ("类　　　型", "有限责任公司（自然人独资）"),
    ("法 定 代 表 人", "王小晨"),
    ("注 册 资 本", "人民币伍佰万元整"),
    ("成 立 日 期", "2018年03月15日"),
    ("住　　　所", "上海市静安区南京西路 1266 号恒隆广场 2 期 35 楼"),
    ("经 营 范 围", "眼镜及配件的销售，验光配镜服务，视力健康咨询，"
                    "医疗器械（二类）销售，货物及技术进出口等。"),
]
y = 390
f_label = font(30, True)
f_val = font(30)
for label, val in fields:
    d.text((140, y), label + "：", font=f_label, fill="#5a3b0f")
    # 值可能折行
    x = 460
    lines, cur = [], ""
    for ch in val:
        if d.textlength(cur + ch, font=f_val) > W - 240 - x:
            lines.append(cur); cur = ch
        else:
            cur += ch
    lines.append(cur)
    for ln in lines:
        d.text((x, y), ln, font=f_val, fill="#222")
        y += 46
    y += 18

# 底部登记机关 + 日期
d.text((200, y + 20), "登记机关：上海市静安区市场监督管理局", font=font(30, True), fill="#5a3b0f")
d.text((200, y + 80), "登记日期：2018年03月15日", font=font(30, True), fill="#5a3b0f")
# 红色印章
d.ellipse([880, y - 60, 1330, y + 120], outline="#d3360b", width=8)
d.text((1105, y + 10), "上海市市场监督管理局", font=font(34, True), fill="#d3360b", anchor="mm")
d.text((1105, y + 58), "企业登记专用章", font=font(28, True), fill="#d3360b", anchor="mm")
# 二维码占位
qx, qy = W - 260, H - 260
d.rectangle([qx, qy, qx + 170, qy + 170], fill="white", outline="#333", width=3)
import random
random.seed(7)
for i in range(10):
    for j in range(10):
        if random.random() < 0.45:
            d.rectangle([qx + 8 + i * 16, qy + 8 + j * 16, qx + 8 + i * 16 + 14, qy + 8 + j * 16 + 14], fill="#111")
d.text((qx + 85, qy + 195), "扫码验证", font=font(24), fill="#333", anchor="mm")
img.save(os.path.join(ROOT, "营业执照_示例_上海晨曦眼镜.png"), "PNG")
print("license ok")

# ---------- 2. 下载系统导入模板 ----------
for code, fname in [("TPL_DOOR_MAINSTREAM", "系统下载_导入模板_Door_Mainstream.xlsx")]:
    url = f"http://localhost:8080/cmd/import/template/download?templateCode={code}"
    try:
        with urllib.request.urlopen(url, timeout=15) as r, open(os.path.join(ROOT, fname), "wb") as f:
            f.write(r.read())
        print("template ok", code)
    except Exception as e:
        print("template FAIL", code, e)

# ---------- 3. 校验演示数据表头 ----------
def headers(p):
    wb = load_workbook(p, read_only=True)
    ws = wb.active
    for row in ws.iter_rows(min_row=1, max_row=2, values_only=True):
        return list(row)
    return []

print("kit模板表头:", headers(os.path.join(ROOT, "系统下载_导入模板_Door_Mainstream.xlsx")))
src = r"D:\Code\2026AI\AIA_MDM\Essilor_MDM\plus-ui\docs\cmd-poc\批量导入_自测_四类分流.xlsx"
print("四类分流表头:", headers(src))
