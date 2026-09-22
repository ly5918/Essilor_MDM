(async () => {
const { chromium } = require('playwright-core');
/**
 * 模型版本列变更只读走查：验证「规则数」已移除、「差异 / 草稿创建时间」列生效
 * 运行：export NODE_PATH="C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules" && node walk_version_cols.cjs
 */
const BASE = 'http://localhost';
const CHROME = 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe';
const log = (k, v) => console.log(`[${k}]`, typeof v === 'string' ? v : JSON.stringify(v));

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
try {
  await page.goto(`${BASE}/cmd-poc/login`, { waitUntil: 'load' });
  await page.getByText('Admin', { exact: true }).first().click();
  await page.click('.sso-button');
  await page.waitForURL('**/cmd-poc/admin', { timeout: 15000 });
  await page.waitForTimeout(2500);
  await page.locator('.side-menu li').nth(1).click();
  await page.waitForTimeout(2000);
  await page.locator('.admin-card button').first().click();
  await page.waitForSelector('.poc-dialog .el-tabs', { timeout: 10000 });
  await page.locator('.poc-dialog .el-tabs__item').nth(2).click();
  await page.locator('.el-tab-pane').nth(2).locator('.el-table__row').first().waitFor({ timeout: 10000 });
  await page.waitForTimeout(600);
  const headers = (await page.locator('.el-tab-pane').nth(2).locator('.el-table__header th').allTextContents()).map(t => t.trim()).filter(Boolean);
  log('headers', headers);
  const rows = (await page.locator('.el-tab-pane').nth(2).locator('.el-table__row').allTextContents()).map(t => t.trim());
  log('rows', rows);
  log('ruleCountGone', !headers.some(h => h.includes('规则数')));
  await page.screenshot({ path: 'version_columns_new.png' });
} catch (e) {
  log('ERROR', String(e).slice(0, 400));
  await page.screenshot({ path: 'walk_error.png' });
} finally {
  await browser.close();
}
console.log('DONE');
})();
