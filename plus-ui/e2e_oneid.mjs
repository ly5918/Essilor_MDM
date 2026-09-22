/**
 * One ID 规则真实生效 E2E 验证脚本（后端直连 8080）
 * 用法：
 *   node e2e_oneid.mjs main     # 登录 → 查规则 → 保存(7位) → 发布 → 创建客户 → 输出 One ID
 *   node e2e_oneid.mjs revert   # 登录 → 恢复(6位/原名称) → 发布
 *
 * 登录加密方案与前端一致：
 *   AES-256-ECB/PKCS7 加密 body + RSA(PKCS1 v1.5) 加密 base64(aesKey) 放入 encrypt-key 头
 */
import crypto from 'node:crypto';
import net from 'node:net';

const BASE = 'http://localhost:8080';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const RSA_PUBLIC_KEY_B64 =
  'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';

// ---------- 加密工具 ----------
function rsaEncryptTxt(txt) {
  const der = Buffer.from(RSA_PUBLIC_KEY_B64, 'base64');
  const keyObj = crypto.createPublicKey({ key: der, format: 'der', type: 'spki' });
  const out = crypto.publicEncrypt(
    { key: keyObj, padding: crypto.constants.RSA_PKCS1_PADDING },
    Buffer.from(txt, 'utf8')
  );
  return out.toString('base64');
}

function generateAesKey() {
  // 与前端 generateRandomString 一致：32 个十六进制字符（ASCII 占 32 字节 → AES-256）
  let s = '';
  const chars = '0123456789abcdef';
  for (let i = 0; i < 32; i++) s += chars[Math.floor(Math.random() * 16)];
  return s;
}

function encryptLoginPayload(data) {
  const aesKey = generateAesKey();
  const keyBytes = Buffer.from(aesKey, 'utf8');
  const header = rsaEncryptTxt(keyBytes.toString('base64'));
  const cipher = crypto.createCipheriv('aes-256-ecb', keyBytes, null);
  const enc = Buffer.concat([cipher.update(JSON.stringify(data), 'utf8'), cipher.final()]);
  return { header, body: enc.toString('base64') };
}

// ---------- HTTP ----------
/** 通过 redis-cli 协议内联命令读取验证码答案 */
function redisCommand(cmd) {
  return new Promise((resolve, reject) => {
    const sock = net.connect(6379, 'localhost');
    let buf = '';
    sock.setTimeout(3000, () => { sock.destroy(); reject(new Error('redis timeout')); });
    sock.on('connect', () => sock.write(`AUTH ruoyi123\r\n${cmd}\r\n`));
    sock.on('data', d => { buf += d.toString(); sock.end(); });
    sock.on('end', () => resolve(buf));
    sock.on('error', reject);
  });
}

async function resolveCaptcha() {
  const res = await fetch(`${BASE}/auth/code`);
  const json = await res.json();
  const info = json.data ?? {};
  if (info.captchaEnabled === false) return {};
  const uuid = info.uuid;
  if (!uuid) throw new Error('未获取到验证码 uuid: ' + JSON.stringify(json).slice(0, 200));
  const keys = await redisCommand(`KEYS *${uuid}*`);
  const lines = keys.split('\r\n').filter(l => l.includes(uuid));
  if (!lines.length) throw new Error('redis 未找到验证码键: ' + keys.slice(0, 200));
  const raw = await redisCommand(`GET "${lines[0]}"`);
  const m = raw.match(/\$\d+\r\n([\s\S]*?)\r\n/);
  let code = m ? m[1] : raw.trim();
  // redis 中为 JSON 序列化值（如 "2"），去掉 JSON 引号
  try { code = JSON.parse(code); } catch { /* 保留原值 */ }
  console.log('    验证码已解析: uuid=' + uuid + ' code=' + code);
  return { uuid, code };
}

async function login() {
  const captcha = await resolveCaptcha();
  const { header, body } = encryptLoginPayload({
    username: 'admin',
    password: 'admin123',
    clientId: CLIENT_ID,
    grantType: 'password',
    tenantId: '000000',
    ...captcha
  });
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      clientid: CLIENT_ID,
      'encrypt-key': header
    },
    body: body
  });
  const json = await res.json();
  if (json.code !== 200 || !json.data?.access_token) {
    throw new Error('登录失败: ' + JSON.stringify(json));
  }
  console.log('[1] 登录成功, token 前 12 位:', json.data.access_token.slice(0, 12));
  return json.data.access_token;
}

function authHeaders(token) {
  return {
    'Content-Type': 'application/json;charset=UTF-8',
    clientid: CLIENT_ID,
    Authorization: 'Bearer ' + token
  };
}

async function api(token, method, url, payload) {
  const res = await fetch(BASE + url, {
    method,
    headers: authHeaders(token),
    body: payload === undefined ? undefined : JSON.stringify(payload)
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`${method} ${url} 非JSON响应(${res.status}): ${text.slice(0, 200)}`);
  }
  return json;
}

// ---------- 主流程 ----------
async function main() {
  const token = await login();

  const cur = await api(token, 'GET', '/cmd/oneid/rule');
  console.log('[2] 当前规则:', JSON.stringify(cur.data ? {
    id: cur.data.id, ruleCode: cur.data.ruleCode, ruleName: cur.data.ruleName,
    prefix: cur.data.prefix, separator: cur.data.separator, serialLength: cur.data.serialLength,
    seqCode: cur.data.seqCode, pattern: cur.data.pattern, status: cur.data.status,
    isDefault: cur.data.isDefault
  } : cur));
  const ruleId = cur.data?.id;

  const saved = await api(token, 'PUT', '/cmd/oneid/rule', {
    id: ruleId,
    ruleName: 'One ID 全局编码规则 V2',
    prefix: 'GC',
    separator: '-',
    serialLength: 7,
    scopeType: 'GC',
    seqCode: 'ONE_ID',
    remark: 'E2E 验证：流水号长度临时改为 7 位'
  });
  console.log('[3] 保存规则(7位):', JSON.stringify(saved));

  const published = await api(token, 'PUT', '/cmd/oneid/rule/publish');
  console.log('[4] 发布规则:', JSON.stringify(published));

  const uniq = Date.now().toString().slice(-6);
  const cust = await api(token, 'POST', '/cmd/customer', {
    legalName: 'E2E验证客户-OneID规则' + uniq,
    creditCode: '91510100E2E' + uniq + '8M',
    address: '上海市浦东新区E2E测试路88号',
    customerType: 'Door',
    buScope: 'High End',
    productLine: 'Frame',
    sourceSystem: 'Cloud',
    country: '中国',
    province: '上海市',
    city: '上海市',
    contactName: 'E2E联系人',
    contactPhone: '13800138000',
    matchState: 'NEW',
    status: 'pending'
  });
  console.log('[5] 创建客户回执:', JSON.stringify(cust));
  console.log('    => 期望 One ID 形如 GC-0000129（7位流水，接 cfg_sequence 128+1）');
}

async function revert() {
  const token = await login();
  const cur = await api(token, 'GET', '/cmd/oneid/rule');
  console.log('[R0] 当前编辑视角规则:', JSON.stringify(cur.data ? {
    id: cur.data.id, ruleCode: cur.data.ruleCode, ruleName: cur.data.ruleName,
    serialLength: cur.data.serialLength, status: cur.data.status, isDefault: cur.data.isDefault
  } : cur));
  const saved = await api(token, 'PUT', '/cmd/oneid/rule', {
    ruleName: 'One ID 默认规则',
    prefix: 'GC',
    separator: '-',
    serialLength: 6,
    scopeType: 'GC',
    seqCode: 'ONE_ID',
    remark: 'One ID 默认规则（E2E 验证后已恢复 6 位流水）'
  });
  console.log('[R1] 恢复规则(6位):', JSON.stringify(saved));
  const published = await api(token, 'PUT', '/cmd/oneid/rule/publish');
  console.log('[R2] 重新发布:', JSON.stringify(published));
}

async function createCustomer() {
  const token = await login();
  const uniq = Date.now().toString().slice(-6);
  const cust = await api(token, 'POST', '/cmd/customer', {
    legalName: 'E2E验证客户-恢复6位' + uniq,
    creditCode: '91320100E2F' + uniq + '1K',
    address: '上海市浦东新区E2E恢复路66号',
    customerType: 'Door',
    buScope: 'Mainstream',
    productLine: 'Frame',
    sourceSystem: 'Cloud',
    country: '中国',
    province: '上海市',
    city: '上海市',
    contactName: 'E2E联系人B',
    contactPhone: '13900139000',
    matchState: 'NEW',
    status: 'pending'
  });
  console.log('[C1] 恢复后创建客户回执:', JSON.stringify(cust));
  console.log('    => 期望 One ID 形如 GC-000130（恢复 6 位流水）');
}

const phase = process.argv[2] || 'main';
if (phase === 'revert') {
  await revert();
} else if (phase === 'create') {
  await createCustomer();
} else {
  await main();
}
