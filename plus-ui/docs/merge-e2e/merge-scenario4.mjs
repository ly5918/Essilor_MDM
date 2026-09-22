// 场景四：合并结果证据 —— 已完成工作流列表 + 客户列表「已合并」状态
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
  // 1. 登录 Business User
  await page.goto('http://localhost/cmd-poc/login', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.getByText('Business User', { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);

  // 2. 流程中心 → 已完成的工作流
  await page.locator('.side-menu li', { hasText: '流程中心' }).first().click();
  await page.waitForTimeout(800);
  await page.locator('.side-menu li', { hasText: '已完成的工作流' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's4-01-flow-done-list');

  // 3. 客户管理 → 查看 GC-23F62B7A 的合并状态
  await page.locator('.side-menu li', { hasText: '客户管理' }).first().click();
  await page.waitForTimeout(2000);
  await page.locator('.el-input__inner').first().fill('GC-23F62B7A');
  await page.locator('button', { hasText: '查询' }).first().click();
  await page.waitForTimeout(2000);
  await shot(page, 's4-02-customer-merged-status');

  // 4. 打开详情看合并标识
  const row = page.locator('.el-table__row', { hasText: 'GC-23F62B7A' }).first();
  if (await row.count()) {
    await row.locator('button', { hasText: '查看客户' }).first().click();
    await page.waitForTimeout(2000);
    await shot(page, 's4-03-merged-detail');
    await page.locator('.el-dialog__headerbtn').last().click();
  }
  console.log('SCENARIO4 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's4-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
