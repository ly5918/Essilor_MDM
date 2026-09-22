// 场景三A：BU Steward 审批 MERGE 任务（BU Scope 初审 → 确认合并）
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
  // 1. 登录 Data Steward · BU Scope
  await page.goto('http://localhost/cmd-poc/login', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.getByText('Steward BU', { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);
  console.log('URL:', page.url());

  // 2. 治理与审批
  await page.locator('.side-menu li', { hasText: '治理与审批' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's3-01-approval-list-bu');

  // 3. 选中 MERGE 任务行
  const row = page.locator('.el-table__row', { hasText: '客户合并' }).first();
  await row.scrollIntoViewIfNeeded();
  await row.click();
  await page.waitForTimeout(2000);
  await shot(page, 's3-02-merge-detail');

  // 4. 打开流程跟踪（MERGE 泳道图）
  await page.locator('button', { hasText: '流程跟踪' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's3-03-flow-trace-swimlane');
  // 关闭弹窗
  await page.locator('.el-dialog__headerbtn').last().click();
  await page.waitForTimeout(1000);

  // 5. 点击「确认合并」动作
  const actBtn = page.locator('.ap-actions button', { hasText: '确认合并' }).first();
  await actBtn.click();
  await page.waitForTimeout(1000);
  await shot(page, 's3-04-before-confirm');
  // 确认框（若有）
  const confirmBtn = page.locator('.el-message-box button', { hasText: '确定' });
  if (await confirmBtn.count()) {
    await confirmBtn.click();
  }
  await page.waitForTimeout(2500);
  await shot(page, 's3-05-after-bu-approve');
  const toast = await page.locator('.el-message').allTextContents().catch(() => []);
  console.log('toast:', JSON.stringify(toast));
  console.log('SCENARIO3A DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's3-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
