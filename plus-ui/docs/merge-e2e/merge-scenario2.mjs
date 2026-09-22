// 场景二：Business User 打开客户详情 → 发起合并 → MergeDialog 选择目标 → 提交
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

  // 2. 客户管理 → 搜索 GC-23F62B7A（High End 同名重复记录，business 只见 High End）→ 查看客户
  await page.locator('.side-menu li', { hasText: '客户管理' }).first().click();
  await page.waitForTimeout(2000);
  await page.locator('.el-input__inner').first().fill('GC-23F62B7A');
  await page.locator('button', { hasText: '查询' }).first().click();
  await page.waitForTimeout(2000);
  await page.screenshot({ path: `${OUT}/s2-01-list-search.png`, fullPage: false });
  const row = page.locator('.el-table__row', { hasText: 'GC-23F62B7A' }).first();
  await row.scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  await row.locator('button', { hasText: '查看客户' }).first().click();
  await page.waitForTimeout(2000);
  await shot(page, 's2-02-detail-dialog');

  // 3. 点「发起合并」
  await page.locator('.el-dialog button', { hasText: '发起合并' }).first().click();
  await page.waitForTimeout(1800);
  await shot(page, 's2-03-merge-dialog-open');

  // 4. MergeDialog：选目标 One ID（GC-23F62B7A 同名 High End）+ 填原因
  const dlg = page.locator('.el-dialog').filter({ hasText: '发起客户合并' }).last();
  await dlg.locator('.el-select').first().click();
  await page.waitForTimeout(800);
  await page.locator('.el-select-dropdown__item', { hasText: 'GC-000001' }).first().click();
  await page.waitForTimeout(500);
  await dlg.locator('textarea').first().fill('同一客户在两个 BU 分别建档（Mainstream / High End），客户名称一致，经业务核实确认为同一法人主体，申请合并主档。');
  await shot(page, 's2-04-merge-dialog-filled');

  // 5. 提交（DialogHost footer 按钮）
  await page.locator('.el-dialog__footer button', { hasText: '发起合并申请' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's2-05-after-submit');
  const toast = await page.locator('.el-message').allTextContents().catch(() => []);
  console.log('toast:', JSON.stringify(toast));
  console.log('SCENARIO2 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's2-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
