import { chromium } from 'file:///C:/Users/Leo/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';

const BASE = 'http://localhost/cmd-poc';
const OUT = 'D:/Code/2026AI/AIA_MDM/Essilor_MDM/plus-ui/docs/cmd-poc/verify';

const log = (...a) => console.log(...a);

const browser = await chromium.launch({
  executablePath: 'C:/Users/Leo/AppData/Local/Google/Chrome/Application/chrome.exe',
  headless: true
});
const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } });
page.on('pageerror', e => log('[pageerror]', e.message));

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);

// 登录：演示账号 → SSO 按钮
await page.getByText('Steward BU', { exact: false }).first().click();
await page.waitForTimeout(600);
await page.click('.sso-button');
await page.waitForTimeout(2500);
log('[1] logged in, url =', page.url());

// 进入「客户层级」菜单
await page.getByText('客户层级', { exact: false }).first().click();
await page.waitForTimeout(2500);
log('[2] hierarchy page url =', page.url());

const snap = async tag => {
  const info = await page.evaluate(() => {
    const sentinels = Array.from(document.querySelectorAll('.hier-load-more')).map(e => e.textContent.trim());
    const leftItems = Array.from(document.querySelectorAll('.hier-result')).length;
    const tip = document.querySelector('.hier-results-tip')?.textContent.trim() ?? '';
    const cap = document.querySelector('.hier-cap-tip')?.textContent.trim() ?? '';
    const treeNodes = document.querySelectorAll('.hier-tree .hier-node').length;
    return { sentinels, leftItems, tip, cap, treeNodes };
  });
  log(`[${tag}] 占位行=${info.sentinels.length} 树节点=${info.treeNodes} 左侧条目=${info.leftItems}`);
  log(`[${tag}] 左提示: ${info.tip}`);
  if (info.cap) log(`[${tag}] 截断提示: ${info.cap}`);
  info.sentinels.forEach((s, i) => log(`   · 占位行${i + 1}: ${s}`));
  return info;
};

const before = await snap('默认');
await page.screenshot({ path: `${OUT}/hier_01_default.png`, fullPage: false });

// 点击第一个「展开全部子节点」
const first = page.locator('.hier-load-more').first();
if (await first.count()) {
  await first.click();
  await page.waitForTimeout(1200);
  const after = await snap('展开后');
  await page.screenshot({ path: `${OUT}/hier_02_expanded.png`, fullPage: false });
  log(`[结论] 占位行 ${before.sentinels.length} → ${after.sentinels.length}，树节点 ${before.treeNodes} → ${after.treeNodes}`);
} else {
  log('[warn] 未找到占位行（数据量可能不足 5 个子节点）');
}

// 搜索关键字
const input = page.locator('.hier-search input').first();
await input.click();
await input.fill('');
await input.type('A', { delay: 60 });
await page.keyboard.press('Enter');
await page.waitForTimeout(2000);
const searched = await snap('搜索A');
await page.screenshot({ path: `${OUT}/hier_03_search.png`, fullPage: false });

await browser.close();
log('DONE');
