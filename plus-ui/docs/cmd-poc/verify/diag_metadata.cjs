(async () => {
const { chromium } = require('playwright-core');
const BASE = 'http://localhost';
const CHROME = 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe';
const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.log('[console]', m.type(), m.text().slice(0, 200)); });
page.on('response', async r => {
  const u = r.url();
  if (u.includes('/cmd/metadata/')) {
    let body = '';
    try { body = (await r.text()).slice(0, 300); } catch {}
    console.log('[resp]', r.status(), r.request().method(), u.replace(BASE, ''), body);
  }
});
try {
  await page.goto(`${BASE}/cmd-poc/login`, { waitUntil: 'load' });
  await page.getByText('Admin', { exact: true }).first().click();
  await page.click('.sso-button');
  await page.waitForURL('**/cmd-poc/admin', { timeout: 15000 });
  await page.waitForTimeout(2000);
  await page.locator('.side-menu li').nth(1).click();
  await page.waitForTimeout(1500);
  await page.locator('.admin-card button').first().click();
  await page.waitForSelector('.poc-dialog .el-tabs', { timeout: 10000 });
  await page.waitForTimeout(2500);
  // 计数三个页签的行数
  for (let i = 0; i < 3; i++) {
    await page.locator('.poc-dialog .el-tabs__item').nth(i).click();
    await page.waitForTimeout(700);
    const rows = await page.locator('.el-tab-pane').nth(i).locator('.el-table__row').count();
    console.log(`[tab${i}] rows =`, rows);
  }
  await page.screenshot({ path: 'diag_tabs.png' });
} catch (e) {
  console.log('[ERROR]', String(e).slice(0, 300));
} finally {
  await browser.close();
}
console.log('DIAG_DONE');
})();
