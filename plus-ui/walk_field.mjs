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

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);
await page.getByText('Admin', { exact: true }).first().click();
await page.waitForTimeout(500);
await page.click('.sso-button');
await page.waitForTimeout(2500);
log('[1] logged in:', page.url());

await page.locator('.side-menu li').nth(1).click();
await page.waitForTimeout(2000);

await page.locator('.admin-card:has-text("字段与值集")').locator('button:has-text("管理")').first().click();
await page.waitForTimeout(2000);

// —— 第 1 步：模型版本页签 → 新建版本（克隆当前生效版本为 Draft）——
await page.locator('.el-tabs__item:has-text("模型版本")').last().click();
await page.waitForTimeout(1200);
await page.locator('button:has-text("新建版本")').last().click();
await page.waitForTimeout(2500);
await shot('field_04_versions');
// 取「第一个 Draft 行」的版本号（限定版本页签容器，避免抓到字段目录表；排序 Current 优先 + 版本倒序 → 第一个 Draft 即最新 Draft）
const workVersion = await page.evaluate(() => {
  const rows = Array.from(document.querySelectorAll('#pane-version .el-table__row'));
  const hit = rows.find(r => r.textContent.includes('Draft'));
  return hit?.querySelector('td')?.textContent?.trim() ?? '';
});
log('[2] 新工作版本 =', workVersion);

// —— 第 2 步：字段目录 → 新建字段 ——
await page.locator('.el-tabs__item:has-text("字段目录")').last().click();
await page.waitForTimeout(1000);
await page.locator('button:has-text("新建字段")').last().click();
await page.waitForTimeout(1200);
const codeInput = page.locator('input[placeholder="如 store_grade"]');
await codeInput.fill('');
await codeInput.fill('vip_level');
const labelInput = page.locator('input[placeholder="如 门店等级"]');
await labelInput.fill('');
await labelInput.fill('测试字段');
await page.waitForTimeout(600);
const versionText = await page.evaluate(() => {
  const dialogs = Array.from(document.querySelectorAll('.el-dialog')).filter(d => d.offsetParent !== null);
  const d = dialogs[dialogs.length - 1];
  const items = Array.from(d?.querySelectorAll('.el-form-item') ?? []);
  const ver = items.find(it => it.textContent.includes('目标版本'));
  return ver?.querySelector('.el-select__selected-item')?.textContent?.trim() ?? '';
});
log('[3] 新建字段默认目标版本 =', versionText, '(应为', workVersion, ')');
await shot('field_02_new_dialog');

await page.locator('button:has-text("保存为Draft")').last().click();
await page.waitForTimeout(2500);
await shot('field_03_saved');
log('[4] 保存完成');

// —— 第 3 步：模型版本页签 → 发布工作版本 ——
await page.locator('.el-tabs__item:has-text("模型版本")').last().click();
await page.waitForTimeout(1200);
const pubBtn = page.locator(`#pane-version tr:has-text("${workVersion}") button:has-text("发布")`).first();
if (await pubBtn.count()) {
  await pubBtn.click();
  await page.waitForTimeout(2500);
  await shot('field_05_published_version');
  log('[5]', workVersion, '发布完成');
} else {
  log('[warn] 未找到', workVersion, '发布按钮');
}

// —— 第 4 步：字段目录确认已 Published ——
await page.locator('.el-tabs__item:has-text("字段目录")').last().click();
await page.waitForTimeout(1500);
const rowState = await page.evaluate(() => {
  const rows = Array.from(document.querySelectorAll('.el-table__row'));
  const hit = rows.find(r => r.textContent.includes('vip_level'));
  return hit ? hit.textContent.replace(/\s+/g, ' ').trim() : 'NOT FOUND';
});
log('[6] vip_level 行：', rowState);
await shot('field_06_field_published');

await page.keyboard.press('Escape');
await page.waitForTimeout(600);
await page.keyboard.press('Escape');
await page.waitForTimeout(800);

// —— 第 5 步：Business User 表单验证 ——
await page.locator('.role-select').first().click();
await page.waitForTimeout(800);
await page.getByText('Business User', { exact: true }).first().click();
await page.waitForTimeout(2200);
await page.getByText('客户管理', { exact: false }).first().click();
await page.waitForTimeout(2000);
await page.locator('button:has-text("新建客户")').first().click();
await page.waitForTimeout(2500);
const dyn = await page.evaluate(() => {
  const body = Array.from(document.querySelectorAll('.el-dialog')).find(d => d.textContent.includes('动态元数据表单'))
    ?.querySelector('.el-dialog__body')?.textContent ?? '';
  const i = body.indexOf('动态元数据表单');
  return { hasNewField: body.includes('测试字段'), tip: body.slice(i, i + 90).replace(/\s+/g, ' ') };
});
log('[7] 动态字段区包含「测试字段」:', dyn.hasNewField, '| 提示条:', dyn.tip);
await shot('field_07_business_form');

await browser.close();
log('DONE');
