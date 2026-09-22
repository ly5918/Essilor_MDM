// 场景七：MergeDialog 合并目标候选收敛验证
// 期望：候选只列出与源重复命中的存量主档（同名/同信用代码），并标注命中原因
import { createRequire } from 'module';
const require = createRequire('C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules/_require_.js');
const { chromium } = require('playwright-core');

const OUT = 'D:/Code/2026AI/AIA_MDM/Essilor_MDM/plus-ui/docs/merge-e2e';
const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false });
const SRC = 'GC-000128'; // active 上海清视眼镜（跨BU），同名候选 GC-000001

const browser = await chromium.launch({
  executablePath: 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe',
  headless: true
});
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
page.setDefaultTimeout(15000);

try {
  await page.goto('http://localhost/cmd-poc/login', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.getByText('Business User', { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);

  await page.locator('.side-menu li', { hasText: '客户管理' }).first().click();
  await page.waitForTimeout(2000);
  // 搜索定位源客户
  const search = page.locator('.el-input__inner').first();
  await search.fill(SRC);
  await page.waitForTimeout(600);
  await page.locator('button', { hasText: '查询' }).first().click();
  await page.waitForTimeout(2000);
  await shot(page, 's7-00-search-result');
  const row = page.locator('.el-table__row', { hasText: SRC }).first();
  await row.scrollIntoViewIfNeeded();
  await row.locator('button', { hasText: '查看客户' }).first().click();
  await page.waitForTimeout(2000);
  await shot(page, 's7-00b-detail-dialog');
  // 详情头部「发起合并」
  await page.locator('.el-dialog button', { hasText: '发起合并' }).first().click();
  await page.waitForTimeout(2000);
  await shot(page, 's7-01-merge-dialog-candidates');
  // 展开目标下拉
  await page.locator('.poc-dialog .el-select').last().click();
  await page.waitForTimeout(1200);
  await shot(page, 's7-02-candidate-dropdown');
  const opts = await page.locator('.el-select-dropdown__item').allTextContents().catch(() => []);
  console.log('candidates:', JSON.stringify(opts));
  console.log('SCENARIO7 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's7-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
