// 验证：审批详情的「发起合并」按钮（SUSPECTED 任务）→ 点击打开 MergeDialog
import { createRequire } from 'module';
const require = createRequire('C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules/_require_.js');
const { chromium } = require('playwright-core');

const OUT = 'D:/Code/2026AI/AIA_MDM/Essilor_MDM/plus-ui/docs/merge-e2e';
const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false });

const browser = await chromium.launch({
  executablePath: 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe',
  headless: true
});
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
page.setDefaultTimeout(15000);

try {
  await page.goto('http://localhost/cmd-poc/login', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.getByText('Steward BU', { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);

  await page.locator('.el-menu-item', { hasText: '治理与审批' }).first().click();
  await page.waitForTimeout(2500);

  // 选中新建的疑似重复任务 AP-20260921-0013
  const row = page.locator('.el-table__row', { hasText: 'AP-20260921-0013' }).first();
  await row.scrollIntoViewIfNeeded();
  await row.click();
  await page.waitForTimeout(2000);
  await shot(page, 's5-01-detail-with-merge-btn');

  // 点「发起合并」
  await page.locator('button', { hasText: '发起合并' }).first().click();
  await page.waitForTimeout(1800);
  await shot(page, 's5-02-merge-dialog-from-approval');
  console.log('VERIFY DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's5-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
