// 场景一：Business User 单条创建客户 → Duplicate Check 命中疑似重复（跨BU）→ 生成跨BU治理任务
import { createRequire } from 'module';
const require = createRequire('C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules/_require_.js');
const { chromium } = require('playwright-core');
import fs from 'fs';

const OUT = 'D:/Code/2026AI/AIA_MDM/Essilor_MDM/plus-ui/docs/merge-e2e';
const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false });

const browser = await chromium.launch({
  executablePath: 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe',
  headless: true
});
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
page.setDefaultTimeout(15000);

try {
  // 1. 登录页 → 演示账号 Business User → SSO 登录
  await page.goto('http://localhost/cmd-poc/login', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await shot(page, 's1-01-login');
  await page.getByText('Business User', { exact: true }).first().click();
  await page.waitForTimeout(500);
  await page.click('.sso-button');
  await page.waitForTimeout(2500);
  console.log('URL after login:', page.url());

  // 2. 进入客户管理
  await page.locator('.side-menu li', { hasText: '客户管理' }).first().click();
  await page.waitForTimeout(2000);
  await shot(page, 's1-02-customers-list');

  // 3. 打开新建客户弹窗
  await page.locator('button', { hasText: '新建客户' }).first().click();
  await page.waitForTimeout(1800);
  await shot(page, 's1-03-new-customer-dialog');

  // 4. 填写固定字段（按 label 定位所在 form-item）
  const fillByLabel = async (label, value) => {
    const item = page.locator('.el-form-item').filter({ hasText: label }).first();
    await item.locator('input').first().fill(value);
    console.log('filled', label, '=', value);
  };
  await fillByLabel('客户法定名称', '上海清视眼镜有限公司');
  await fillByLabel('统一社会信用代码', '91310000E2ETEST09');
  await fillByLabel('注册地址', '上海市黄浦区南京东路100号');

  // 动态必填字段：扫描空 input 并填值
  const emptyInputs = await page.locator('.el-form-item .el-input__inner').evaluateAll(els =>
    els.map((el, i) => ({ i, v: el.value, ph: el.placeholder }))
  );
  console.log('inputs:', JSON.stringify(emptyInputs));

  await shot(page, 's1-04-dialog-filled');

  // 5. 提交申请
  await page.locator('.el-dialog__footer button', { hasText: '提交申请' }).first().click();
  await page.waitForTimeout(2500);
  await shot(page, 's1-05-after-submit');
  const toast = await page.locator('.el-message').allTextContents().catch(() => []);
  console.log('toast:', JSON.stringify(toast));
  console.log('SCENARIO1 DONE');
} catch (e) {
  console.error('FAIL:', e.message);
  await shot(page, 's1-99-error').catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
