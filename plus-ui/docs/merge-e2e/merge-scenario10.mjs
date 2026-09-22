// 场景十：方案 C「左右布局 + 右侧只读速览 + 进入审批弹窗」验证
// ① 双栏恢复（右侧速览无审批动作） ② 点行加载速览概要 ③ 「进入审批」打开 1040px 弹窗（含意见+动作按钮）
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

  // 点首行 → 右侧速览加载
  const row = page.locator('.el-table__row').first();
  await row.click();
  await page.waitForTimeout(2500);
  await shot(page, 's10-01-quick-view');

  const side = await page.evaluate(() => {
    const quick = document.querySelector('.ap-quick');
    if (!quick) return { exists: false };
    const layout = document.querySelector('.ap-layout');
    return {
      exists: true,
      listWidth: Math.round(document.querySelector('.ap-list').getBoundingClientRect().width),
      quickWidth: Math.round(quick.getBoundingClientRect().width),
      hasEnterBtn: !!quick.querySelector('.ap-quick-foot button'),
      sideHasActions: !!quick.querySelector('.ap-actions'),
      sideHasComment: !!quick.querySelector('textarea'),
      gridCols: layout ? getComputedStyle(layout).gridTemplateColumns : null
    };
  });
  console.log('SIDE:', JSON.stringify(side));

  // 点「进入审批」→ 审批弹窗
  await page.locator('.ap-quick-foot button', { hasText: '进入审批' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's10-02-approval-dialog');

  const dlg = await page.evaluate(() => {
    const d = [...document.querySelectorAll('.el-dialog')].find(x => x.getBoundingClientRect().width > 0);
    if (!d) return { open: false };
    return {
      open: true,
      width: Math.round(d.getBoundingClientRect().width),
      hasActions: !!d.querySelector('.ap-actions button'),
      hasComment: !!d.querySelector('textarea'),
      hasEvidence: !!d.querySelector('.ap-cand, .ap-evidence'),
      kvCols: getComputedStyle(d.querySelector('.h-kv')).gridTemplateColumns
    };
  });
  console.log('DIALOG:', JSON.stringify(dlg));
  console.log('SCENARIO10 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's10-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
