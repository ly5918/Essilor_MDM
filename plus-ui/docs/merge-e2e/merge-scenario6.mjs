// 场景六：疑似重复创建任务的决策动作对齐总设计
// BU 初审只可「升级GC决策 / 排除重复 / 退回补充」；GC 决策「确认关联已有 / 创建新主档 / 退回BU修复」
import { createRequire } from 'module';
const require = createRequire('C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules/_require_.js');
const { chromium } = require('playwright-core');

const OUT = 'D:/Code/2026AI/AIA_MDM/Essilor_MDM/plus-ui/docs/merge-e2e';
const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false });
const TASK = 'AP-20260921-0013';

async function login(page, demoName) {
  await page.goto('http://localhost/cmd-poc/login', { waitUntil: 'load' });
  // 清理会话残留（二次登录时 demo 登录会被旧 token 重定向绕过）
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
  await page.context().clearCookies();
  await page.reload({ waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.getByText(demoName, { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);
}

const browser = await chromium.launch({
  executablePath: 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe',
  headless: true
});
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
page.setDefaultTimeout(15000);

try {
  // ===== BU 段（已验证：动作集=升级GC决策/排除重复/退回补充，见 s6-01/s6-02 截图）=====
  // 0013 已升级至 GC，此处直接从 GC 段开始验证
  // ===== GC Steward：查看升级后的决策集 =====
  await login(page, 'Steward GC');
  await shot(page, 's6-02c-after-login-gc');
  // GC 工作台高优先级列表直接「处理」0013（GC 角色侧边菜单无「治理与审批」）
  const gcRow = page.locator('.el-table__row, .dash-task-row, tr', { hasText: TASK }).first();
  await gcRow.scrollIntoViewIfNeeded();
  await gcRow.locator('button, a, .el-button', { hasText: '处理' }).first().click();
  await page.waitForTimeout(2500);
  // 跳转到「全局治理决策」列表后点击 0013 行打开右侧详情
  const row2 = page.locator('.el-table__row', { hasText: TASK }).first();
  await row2.scrollIntoViewIfNeeded();
  await row2.click();
  await page.waitForTimeout(2000);
  await shot(page, 's6-03-gc-detail-actions');
  const gcActs = await page.locator('.ap-actions button').allTextContents();
  console.log('GC actions:', JSON.stringify(gcActs));
  console.log('SCENARIO6 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's6-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
