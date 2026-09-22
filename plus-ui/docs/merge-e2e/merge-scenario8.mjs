// 场景八：审批面板布局优化验证
// ① 列表无 One ID 列 ② 分页固定在内容区底部 ③ 左右布局间距优化（详情 420-480px）
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

  await page.locator('.side-menu li', { hasText: '治理与审批' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's8-01-approval-new-layout');

  // 几何校验：One ID 列是否移除 / 分页位置 / 左右栏宽
  const check = await page.evaluate(() => {
    const headers = [...document.querySelectorAll('.ap-list .el-table__header th')].map(t => t.textContent.trim());
    const pager = document.querySelector('.ap-list .pagination-container');
    const main = document.querySelector('.cmd-poc .cmd-main');
    const list = document.querySelector('.ap-list');
    const detail = document.querySelector('.ap-detail');
    return {
      headers,
      oneIdGone: !headers.includes('One ID'),
      pagerBottom: pager ? Math.round(pager.getBoundingClientRect().bottom) : null,
      mainBottom: main ? Math.round(main.getBoundingClientRect().bottom) : null,
      listWidth: list ? Math.round(list.getBoundingClientRect().width) : null,
      detailWidth: detail ? Math.round(detail.getBoundingClientRect().width) : null
    };
  });
  console.log('CHECK:', JSON.stringify(check));

  // 选中一行看详情新头部布局
  const row = page.locator('.el-table__row').first();
  await row.click();
  await page.waitForTimeout(2000);
  await shot(page, 's8-02-detail-new-head');
  console.log('SCENARIO8 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's8-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
