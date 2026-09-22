(async () => {
const { chromium } = require('playwright-core');
/**
 * 字段与值集弹窗 E2E 走查（playwright-core + 本机 Chrome）
 * 验证：值集编辑保存 → 新建版本（v1 → v1.1 Draft）→ 发布版本（v1.1 转 Current）
 * 运行：export NODE_PATH="C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules" && node walk_fields_dialog.cjs
 */

const BASE = 'http://localhost';
const CHROME = 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe';
const results = {};
const log = (k, v) => { results[k] = v; console.log(`[${k}]`, typeof v === 'string' ? v : JSON.stringify(v)); };

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });

try {
  // ---- 登录 Admin ----
  await page.goto(`${BASE}/cmd-poc/login`, { waitUntil: 'load' });
  await page.getByText('Admin', { exact: true }).first().click();
  await page.click('.sso-button');
  await page.waitForURL('**/cmd-poc/admin', { timeout: 15000 });
  await page.waitForTimeout(2500);

  // ---- 平台管理 → 字段与值集（第一张卡） ----
  await page.locator('.side-menu li').nth(1).click();
  await page.waitForTimeout(2000);
  await page.locator('.admin-card button').first().click();
  await page.waitForSelector('.poc-dialog .el-tabs', { timeout: 10000 });

  // ---- ① 值集编辑保存 ----
  const tabs = page.locator('.poc-dialog .el-tabs__item');
  await tabs.nth(1).click();
  await page.locator('.el-tab-pane').nth(1).locator('.el-table__row').first().waitFor({ timeout: 10000 });
  log('activeTab', await page.locator('.poc-dialog .el-tabs__item.is-active').textContent());

  const vsRespPromise = page.waitForResponse(
    r => r.url().includes('/cmd/metadata/valueset') && r.request().method() === 'POST', { timeout: 10000 }
  ).catch(() => null);
  await page.locator('.el-tabs__content > .el-tab-pane').nth(1).locator('.el-table button').first().click();
  await page.waitForSelector('.el-dialog.vs-edit-dialog', { state: 'visible', timeout: 5000 });
  await page.screenshot({ path: 'vs_edit_dialog2.png' });
  const vsResp = await vsRespPromise;
  await page.locator('.vs-edit-dialog .el-dialog__footer .el-button--primary').click();
  const msg1 = await page.waitForSelector('.el-message', { timeout: 5000 }).catch(() => null);
  log('vsSaveMsg', msg1 ? (await msg1.textContent()).trim() : 'NO_TOAST');
  log('vsSaveHttpStatus', vsResp ? vsResp.status() : 'NO_REQUEST');
  await page.waitForTimeout(800);
  await page.screenshot({ path: 'vs_saved2.png' });

  // ---- ② 新建版本 ----
  await tabs.nth(2).click();
  await page.locator('.el-tab-pane').nth(2).locator('.el-table__row').first().waitFor({ timeout: 10000 });
  log('rowsBefore', (await page.locator('.el-tab-pane:visible .el-table__row').allTextContents()).map(t => t.trim()));
  await page.screenshot({ path: 'version_before2.png' });

  const createRespPromise = page.waitForResponse(
    r => r.url().includes('/cmd/metadata/version') && r.request().method() === 'POST', { timeout: 10000 }
  ).catch(() => null);
  await page.click('.btn-create-version');
  const createResp = await createRespPromise;
  const msg2 = await page.waitForSelector('.el-message', { timeout: 5000 }).catch(() => null);
  log('createMsg', msg2 ? (await msg2.textContent()).trim() : 'NO_TOAST');
  log('createHttpStatus', createResp ? createResp.status() : 'NO_REQUEST');
  await page.waitForTimeout(1200);
  log('rowsAfterCreate', (await page.locator('.el-tab-pane:visible .el-table__row').allTextContents()).map(t => t.trim()));
  await page.screenshot({ path: 'version_created2.png' });

  // ---- ③ 发布新版本 ----
  const pubRespPromise = page.waitForResponse(
    r => r.url().includes('/cmd/metadata/version/publish') && r.request().method() === 'PUT', { timeout: 10000 }
  ).catch(() => null);
  await page.locator('.el-tab-pane:visible .el-table button').first().click();
  const pubResp = await pubRespPromise;
  const msg3 = await page.waitForSelector('.el-message', { timeout: 5000 }).catch(() => null);
  log('publishMsg', msg3 ? (await msg3.textContent()).trim() : 'NO_TOAST');
  log('publishHttpStatus', pubResp ? pubResp.status() : 'NO_REQUEST');
  await page.waitForTimeout(1500);
  log('rowsAfterPublish', (await page.locator('.el-tab-pane:visible .el-table__row').allTextContents()).map(t => t.trim()));
  await page.screenshot({ path: 'version_published2.png' });

  // ---- ④ 新建字段默认目标版本 = Draft 工作版本 ----
  await tabs.nth(0).click();
  await page.locator('.el-tab-pane').nth(0).locator('.el-table__row').first().waitFor({ timeout: 10000 });
  await page.locator('.el-tab-pane:visible button:has-text("新建字段")').click();
  await page.waitForSelector('.el-dialog:visible .poc-form', { timeout: 5000 });
  const selTexts = await page.locator('.el-dialog:visible .el-select__selected-item').allTextContents();
  log('newFieldSelects', selTexts.map(t => t.trim()).filter(Boolean));
  await page.screenshot({ path: 'field_version_default.png' });
  await page.keyboard.press('Escape');
} catch (e) {
  log('ERROR', String(e).slice(0, 500));
  await page.screenshot({ path: 'walk_error.png' });
} finally {
  await browser.close();
}
console.log('WALK_DONE');
})();
