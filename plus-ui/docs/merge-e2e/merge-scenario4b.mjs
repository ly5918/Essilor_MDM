// 场景四B：已完成工作流列表 + MERGE 完成态流程跟踪
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
  await page.getByText('Business User', { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);

  // 展开「流程中心」子菜单，再点「已完成的工作流」（叶子节点用 .el-menu-item 精确匹配）
  await page.locator('.el-sub-menu__title', { hasText: '流程中心' }).first().click();
  await page.waitForTimeout(1200);
  await page.locator('.el-menu-item', { hasText: '已完成的工作流' }).first().click();
  await page.waitForTimeout(2500);
  // 校验页面真的切过去了
  const bodyText = await page.locator('.poc-page, .panel, main, body').first().innerText();
  if (!bodyText.includes('已完成')) console.log('WARN: page may not have switched');
  await shot(page, 's4-04-flow-done-real');

  // 若列表有 MERGE 行，打开其流程跟踪看完成态泳道
  const mergeRow = page.locator('.el-table__row', { hasText: 'AP-20260921-0011' }).first();
  if (await mergeRow.count()) {
    const traceBtn = mergeRow.locator('button', { hasText: '流程跟踪' }).first();
    if (await traceBtn.count()) {
      await traceBtn.click();
      await page.waitForTimeout(2500);
      await shot(page, 's4-05-merge-done-swimlane');
    } else {
      console.log('no trace button on merge row');
    }
  } else {
    console.log('merge row not found in done list');
  }
  console.log('SCENARIO4B DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's4-98-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
