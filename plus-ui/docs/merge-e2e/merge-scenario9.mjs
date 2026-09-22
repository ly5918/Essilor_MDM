// 场景九：审批页改「全宽列表 + 点击行弹出审批详情弹窗」验证
// ① 列表全宽（无右侧详情栏）② 无 One ID 列 ③ 分页贴底 ④ 点行弹 1040px 审批弹窗（证据对比/动作区）
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
  await shot(page, 's9-01-fullwidth-list');

  // 几何校验：列表宽度 / 无 One ID 列 / 分页贴底 / 右侧详情栏已移除
  const check = await page.evaluate(() => {
    const headers = [...document.querySelectorAll('.ap-list .el-table__header th')].map(t => t.textContent.trim());
    const pager = document.querySelector('.ap-list .pagination-container');
    const main = document.querySelector('.cmd-poc .cmd-main');
    const list = document.querySelector('.ap-list');
    return {
      headers,
      oneIdGone: !headers.includes('One ID'),
      listWidth: list ? Math.round(list.getBoundingClientRect().width) : null,
      pagerBottom: pager ? Math.round(pager.getBoundingClientRect().bottom) : null,
      mainBottom: main ? Math.round(main.getBoundingClientRect().bottom) : null,
      noSideDetail: !document.querySelector('.ap-detail')
    };
  });
  console.log('CHECK:', JSON.stringify(check));

  // 点击首行 → 审批详情弹窗
  const row = page.locator('.el-table__row').first();
  await row.click();
  await page.waitForTimeout(2500);
  await shot(page, 's9-02-approval-dialog');

  const dlg = await page.evaluate(() => {
    const d = [...document.querySelectorAll('.el-dialog')].find(x => x.offsetParent !== null || x.getBoundingClientRect().width > 0);
    if (!d) return { open: false };
    return {
      open: true,
      width: Math.round(d.getBoundingClientRect().width),
      title: d.querySelector('.el-dialog__title')?.textContent.trim(),
      hasActions: !!d.querySelector('.ap-actions button'),
      kvCols: getComputedStyle(d.querySelector('.h-kv') ?? d).gridTemplateColumns
    };
  });
  console.log('DIALOG:', JSON.stringify(dlg));
  console.log('SCENARIO9 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's9-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
