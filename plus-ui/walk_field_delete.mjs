import { chromium } from 'file:///C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';

const BASE = 'http://localhost/cmd-poc';
const OUT = 'D:/Code/2026AI/AIA_MDM/Essilor_MDM/plus-ui/docs/cmd-poc/verify';
const log = (...a) => console.log(...a);

const browser = await chromium.launch({
  executablePath: 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe',
  headless: true
});
const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } });
page.on('pageerror', e => log('[pageerror]', e.message));
const shot = (name) => page.screenshot({ path: `${OUT}/${name}.png`, type: 'jpeg', quality: 82 });

/** 读取字段目录表格：编码 / 名称 / 版本 / 状态 / 删除按钮是否禁用 */
const readRows = () =>
  page.evaluate(() => {
    const pane = document.querySelector('.el-tab-pane:not([style*="display: none"])') ?? document;
    const rows = Array.from(pane.querySelectorAll('.el-table__row'));
    return rows.map(r => {
      const tds = Array.from(r.querySelectorAll('td'));
      const btn = Array.from(r.querySelectorAll('button')).find(b => b.textContent.trim() === '删除');
      return {
        code: tds[0]?.textContent?.trim() ?? '',
        label: tds[1]?.textContent?.trim() ?? '',
        version: tds[4]?.textContent?.trim() ?? '',
        status: tds[5]?.textContent?.trim() ?? '',
        deleteGuardTooltip: btn ? btn.disabled : null
      };
    });
  });

/** 点下拉选项（Element Plus 的下拉 popper 挂在 body 上） */
const pickVersion = async (text) => {
  await page.locator('.fd-version-filter').click();
  await page.waitForTimeout(600);
  await page.locator(`.el-select-dropdown__item:has-text("${text}")`).first().click();
  await page.waitForTimeout(1000);
};

const search = async (kw) => {
  const input = page.locator('.fd-keyword input');
  await input.fill(kw);
  await page.waitForTimeout(1200);
};

/** 删除表格中匹配 label/keyword 的第一行（含确认框） */
const deleteRowByText = async (text, tag) => {
  const row = page.locator('.el-table__row', { hasText: text }).first();
  const cnt = await row.count();
  if (!cnt) {
    log(`[${tag}] 未找到目标行「${text}」，跳过`);
    return false;
  }
  await row.locator('button:has-text("删除")').click();
  await page.waitForTimeout(1000);
  const boxText = await page.evaluate(
    () => document.querySelector('.el-message-box')?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
  );
  log(`[${tag}] 确认框:`, boxText.slice(0, 110));
  if (tag === 'shot') await shot('field_09_delete_confirm');
  await page.locator('.el-message-box button:has-text("确认删除")').first().click();
  await page.waitForTimeout(2500);
  return true;
};

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.getByText('Admin', { exact: true }).first().click();
await page.waitForTimeout(500);
await page.click('.sso-button');
await page.waitForTimeout(2500);
log('[1] logged in:', page.url());

// 平台管理（侧边菜单第 2 项）
await page.locator('.side-menu li').nth(1).click();
await page.waitForTimeout(2000);
await page.locator('.admin-card:has-text("字段与值集")').locator('button:has-text("管理")').first().click();
await page.waitForTimeout(2500);
await page.locator('.el-tabs__item:has-text("字段目录")').last().click();
await page.waitForTimeout(1500);
await shot('field_01_dialog_default');

// —— 1) 默认视图（当前工作版本）+ 核心字段保护 ——
const rows0 = await readRows();
log('[2] 默认筛选行数:', rows0.length, '| 版本:', Array.from(new Set(rows0.map(r => r.version))).join(','));
const core = rows0.filter(r => ['legal_name', 'credit_code', 'address', 'status'].includes(r.code));
log('[3] 核心字段删除按钮禁用状态:', JSON.stringify(core));
await shot('field_02_protected_core');

// —— 2) 切到「全部版本」并搜索，清理测试字段 ——
await pickVersion('全部版本');
const all = await readRows();
log('[4] 全部版本行数:', all.length, '| 版本集合:', Array.from(new Set(all.map(r => r.version))).join(','));
await shot('field_03_all_versions');

let removed = 0;
for (const kw of ['测试字段', '测试新的字段', 'CESHI001']) {
  await search(kw);
  const hit = await readRows();
  log(`[5] 搜索「${kw}」命中:`, hit.map(r => `${r.code}/${r.label}@${r.version}`).join(' | ') || '(无)');
  if (hit.length === 0) continue;
  if (kw === '测试字段') await shot('field_04_search_before_delete');
  const ok = await deleteRowByText(hit[0].label || kw, kw === '测试字段' ? 'shot' : kw);
  if (ok) removed++;
  const after = await readRows();
  log(`[6] 删除「${kw}」后剩余命中:`, after.length);
}

// 清理结果
await search('');
await pickVersion('全部版本');
const finalAll = await readRows();
const leftovers = finalAll.filter(r => ['测试字段', '测试新的字段', 'CESHI001'].some(t => (r.label ?? '').includes(t)));
log('[7] 残留测试字段:', leftovers.length, JSON.stringify(leftovers.map(r => `${r.code}/${r.label}@${r.version}`)));
await shot('field_05_after_delete_all_versions');

// 关闭「字段与值集管理」弹窗：Element Plus 的 el-dialog 不响应 Escape，需点右上角关闭按钮
await page.locator('.el-dialog__headerbtn').last().click();
await page.waitForTimeout(1000);
log('[7.5] 弹窗已关闭, 剩余 dialog:', await page.locator('.el-dialog:visible').count());

// —— 3) Business User 业务表单：已删字段不应再出现，核心字段仍在 ——
await page.locator('.role-select').first().click();
await page.waitForTimeout(800);
await page.getByText('Business User', { exact: true }).first().click();
await page.waitForTimeout(2200);
await page.getByText('客户管理', { exact: false }).first().click();
await page.waitForTimeout(2000);
await page.locator('button:has-text("新建客户")').first().click();
await page.waitForTimeout(2500);
const dyn = await page.evaluate(() => {
  const body =
    Array.from(document.querySelectorAll('.el-dialog'))
      .find(d => d.textContent.includes('动态元数据表单'))
      ?.querySelector('.el-dialog__body')?.textContent ?? '';
  return {
    hasDeleted: body.includes('测试字段') || body.includes('测试新的字段') || body.includes('CESHI001'),
    hasProtected: body.includes('统一社会信用代码') && body.includes('客户法定名称'),
    len: body.length
  };
});
log('[8] 业务表单含已删字段:', dyn.hasDeleted, '| 核心字段仍在:', dyn.hasProtected, '| 表单文本长度:', dyn.len);
await shot('field_06_business_form_after_delete');

await browser.close();
log(`DONE removed=${removed}`);
