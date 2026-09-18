<template>
  <div class="poc-login">
    <div class="login-shell">
      <!-- 左侧品牌区（玻璃卡片，参考框架登录页风格） -->
      <section class="login-brand">
        <span class="brand-pill">EssilorLuxottica CMD</span>
        <h1 class="brand-title">统一客户主数据<br />从可信身份开始</h1>
        <p class="brand-desc">
          面向企业客户主数据治理的一体化平台：统一客户创建、OCR 识别、DQ 质量规则、重复匹配、
          审批工作流与 Golden Record / One ID 分发，为 Business、BU、GC 与审计角色提供可信数据底座。
        </p>
        <div class="brand-highlights">
          <span v-for="item in highlights" :key="item" class="highlight-chip">{{ item }}</span>
        </div>
        <div class="brand-metrics">
          <article v-for="item in metrics" :key="item.label" class="metric-card">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </article>
        </div>
      </section>

      <!-- 右侧登录卡片 -->
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="onLogin">
        <div class="title-box">
          <div>
            <p class="eyebrow">CMD POC Sign In</p>
            <h3 class="title">欢迎登录 CMD 平台</h3>
            <p class="subtitle">使用 Microsoft Entra ID 账号体系登录到治理工作台。</p>
          </div>
        </div>

        <!-- 单点登录（显著位置：标题正下方） -->
        <el-button class="sso-button" size="large" @click="onMicrosoftLogin">
          <svg-icon icon-class="international" class="sso-icon" />
          使用 Microsoft 账号登录
        </el-button>

        <div class="divider"><span>或使用账号登录</span></div>

        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username">
            <template #prefix><svg-icon icon-class="user" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
          >
            <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="code" class="captcha-row">
          <div class="captcha-wrap">
            <el-input v-model="form.code" placeholder="请输入验证码" class="captcha-input" @keyup.enter="onLogin">
              <template #prefix><svg-icon icon-class="validCode" class="input-icon" /></template>
            </el-input>
            <div class="captcha-box" title="点击刷新验证码" @click="drawCaptcha">
              <canvas ref="canvasRef" class="captcha-canvas" width="130" height="40"></canvas>
              <span class="captcha-refresh">点击刷新</span>
            </div>
          </div>
        </el-form-item>

        <div class="form-meta">
          <el-checkbox v-model="rememberMe">记住我</el-checkbox>
        </div>

        <el-form-item class="submit-row">
          <el-button size="large" type="primary" class="submit-button" :loading="loading" @click.prevent="onLogin">
            {{ loading ? '登 录 中' : '登 录' }}
          </el-button>
        </el-form-item>

        <div class="demo-panel">
          <span class="demo-label">演示账号</span>
          <div class="demo-actions">
            <el-button
              v-for="acc in DEMO_ACCOUNTS"
              :key="acc.username"
              size="small"
              round
              plain
              @click="fillAccount(acc.username)"
            >
              {{ acc.label }}
            </el-button>
          </div>
        </div>
      </el-form>
    </div>

    <div class="login-footer">
      <span>Copyright © 2026 EssilorLuxottica CMD POC. All Rights Reserved.</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

defineOptions({ name: 'CmdPocLogin' });

interface DemoAccount {
  username: string;
  label: string;
  role: string;
}

/** 演示账号 → 角色入口路由映射（POC 免密模拟） */
const DEMO_ACCOUNTS: DemoAccount[] = [
  { username: 'alice.demo@cmd-poc.com', label: 'Business User', role: 'business' },
  { username: 'steward.bu@cmd-poc.com', label: 'Steward BU', role: 'bu' },
  { username: 'steward.gc@cmd-poc.com', label: 'Steward GC', role: 'gc' },
  { username: 'admin@cmd-poc.com', label: 'Admin', role: 'admin' },
  { username: 'auditor@cmd-poc.com', label: 'Auditor', role: 'audit' }
];

const highlights = ['Golden Record', 'One ID 分发', 'DQ 质量规则', '审批工作流', '客户层级管理'];
const metrics = [
  { value: '5', label: '角色工作台' },
  { value: 'A3-A2-A1', label: '客户层级' },
  { value: 'OCR', label: '证照识别' },
  { value: 'SLA', label: '审批时效' }
];

const REMEMBER_KEY = 'cmd-poc-remember-username';

const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const rememberMe = ref(false);
const form = reactive({ username: '', password: '', code: '' });

/* ---------------- POC 本地验证码（canvas 绘制，点击刷新） ---------------- */
const canvasRef = ref<HTMLCanvasElement>();
const captchaCode = ref('');

const drawCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  captchaCode.value = Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  form.code = '';

  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const { width, height } = canvas;
  ctx.clearRect(0, 0, width, height);
  // 深色渐变底，保证与白色卡片对比明显
  const bg = ctx.createLinearGradient(0, 0, width, height);
  bg.addColorStop(0, '#e8eefb');
  bg.addColorStop(1, '#dbe7f7');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, width, height);
  // 干扰点
  for (let i = 0; i < 40; i++) {
    ctx.fillStyle = `rgba(37, 99, 235, ${0.12 + Math.random() * 0.2})`;
    ctx.fillRect(Math.random() * width, Math.random() * height, 2, 2);
  }
  // 干扰线
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = `rgba(${53 + i * 30}, ${109 + i * 20}, 255, 0.3)`;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(Math.random() * width, Math.random() * height);
    ctx.bezierCurveTo(
      Math.random() * width, Math.random() * height,
      Math.random() * width, Math.random() * height,
      Math.random() * width, Math.random() * height
    );
    ctx.stroke();
  }
  // 字符
  captchaCode.value.split('').forEach((ch, i) => {
    ctx.save();
    ctx.font = `800 ${22 + Math.random() * 3}px Arial`;
    ctx.fillStyle = ['#1d4ed8', '#0369a1', '#5b21b6', '#0f766e'][i % 4];
    ctx.translate((width / 4) * i + width / 8, height / 2 + 8);
    ctx.rotate((Math.random() - 0.5) * 0.5);
    ctx.textAlign = 'center';
    ctx.fillText(ch, 0, 0);
    ctx.restore();
  });
};

const validateCaptcha = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入验证码'));
  } else if (value.trim().toUpperCase() !== captchaCode.value) {
    callback(new Error('验证码错误，请重新输入'));
    drawCaptcha();
  } else {
    callback();
  }
};

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, validator: validateCaptcha, trigger: 'blur' }]
};

/* ---------------- 记住我（localStorage 记住用户名） ---------------- */
onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY);
  if (saved) {
    form.username = saved;
    rememberMe.value = true;
  } else {
    form.username = 'alice.demo@cmd-poc.com';
  }
  drawCaptcha();
});

const fillAccount = (username: string) => {
  form.username = username;
};

/** POC 模拟登录：根据演示账号跳到对应角色工作台 */
const doLogin = (mode: 'account' | 'microsoft') => {
  loading.value = true;
  if (mode === 'account' && rememberMe.value) {
    localStorage.setItem(REMEMBER_KEY, form.username.trim());
  } else {
    localStorage.removeItem(REMEMBER_KEY);
  }
  const matched = DEMO_ACCOUNTS.find(acc => acc.username === form.username.trim().toLowerCase());
  const role = matched?.role ?? 'business';
  window.setTimeout(() => {
    loading.value = false;
    ElMessage.success(mode === 'microsoft' ? 'Microsoft Entra ID 登录成功（POC 模拟）' : '登录成功（POC 模拟）');
    router.push(`/cmd-poc/${role}`);
  }, 400);
};

const onLogin = () => {
  formRef.value?.validate(valid => {
    if (valid) doLogin('account');
  });
};

const onMicrosoftLogin = () => {
  doLogin('microsoft');
};
</script>

<style lang="scss" scoped>
.poc-login {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 24px;
  background:
    radial-gradient(circle at 12% 12%, rgb(53 109 255 / 22%), transparent 24%),
    radial-gradient(circle at 88% 18%, rgb(14 165 233 / 18%), transparent 24%),
    linear-gradient(135deg, #071120 0%, #0f1b33 42%, #15345f 100%);
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(340px, 1.1fr) minmax(360px, 0.9fr);
  gap: 32px;
  align-items: stretch;
  width: min(1040px, 100%);
}

/* ---------------- 左侧品牌区 ---------------- */
.login-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 44px;
  color: #eef4ff;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 8%), rgb(255 255 255 / 2%)),
    linear-gradient(135deg, rgb(53 109 255 / 32%), rgb(15 23 42 / 24%));
  box-shadow: 0 30px 80px rgb(2 8 23 / 32%);
  backdrop-filter: blur(6px);
}

.brand-pill {
  align-self: flex-start;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  border-radius: 999px;
  background: rgb(255 255 255 / 12%);
  color: rgb(255 255 255 / 90%);
}

.brand-title {
  margin: 22px 0 0;
  font-size: 34px;
  font-weight: 800;
  line-height: 1.4;
}

.brand-desc {
  max-width: 440px;
  margin: 18px 0 0;
  font-size: 13px;
  line-height: 1.9;
  color: rgb(226 232 240 / 88%);
}

.brand-highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.highlight-chip {
  padding: 6px 14px;
  font-size: 12px;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 999px;
  background: rgb(255 255 255 / 8%);
  color: #f8fbff;
}

.brand-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 30px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 20px;
  background: rgb(255 255 255 / 8%);

  strong {
    font-size: 17px;
    font-weight: 700;
    color: #fff;
  }

  span {
    font-size: 12px;
    color: rgb(226 232 240 / 72%);
  }
}

/* ---------------- 右侧登录卡片 ---------------- */
.login-form {
  align-self: center;
  width: 100%;
  max-width: 420px;
  padding: 36px 34px 26px;
  border-radius: 28px;
  background: var(--app-surface-bg, #fff);
  box-shadow: 0 30px 80px rgb(2 8 23 / 32%);
}

.title-box {
  margin-bottom: 22px;

  .eyebrow {
    margin: 0;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 1.2px;
    text-transform: uppercase;
    color: var(--app-accent-strong, #2563eb);
  }

  .title {
    margin: 8px 0 0;
    font-size: 24px;
    font-weight: 800;
    color: var(--app-text-title, #1e293b);
  }

  .subtitle {
    margin: 8px 0 0;
    font-size: 13px;
    color: var(--app-text-muted, #64748b);
  }
}

/* ---------------- 输入框：固定合理高度 ---------------- */
.login-form :deep(.el-input__wrapper) {
  padding: 0 14px;
  border-radius: 12px;
  background-color: var(--el-bg-color, #fff);
  box-shadow: 0 0 0 1px var(--app-surface-border, #e2e8f0) inset;
}

.login-form :deep(.el-input__inner) {
  height: 40px;
  line-height: 40px;
}

.login-form :deep(.el-input__prefix) {
  font-size: 14px;
}

.login-form :deep(.input-icon) {
  width: 1em;
  height: 1em;
  color: var(--app-text-muted, #94a3b8);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--app-accent-strong, #2563eb) inset,
    0 0 0 3px rgb(53 109 255 / 16%);
}

/* 验证码行：输入框 + 验证码块并排 */
.captcha-row {
  .captcha-wrap {
    display: flex;
    gap: 12px;
    width: 100%;
  }

  .captcha-input {
    flex: 1;
    min-width: 0;
  }

  /* 验证码块：描边 + 阴影 + 刷新提示，确保醒目 */
  .captcha-box {
    position: relative;
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    width: 130px;
    overflow: hidden;
    cursor: pointer;
    border: 1px solid var(--app-surface-border, #cbd5e1);
    border-radius: 12px;
    transition: border-color 0.2s, box-shadow 0.2s;

    &:hover {
      border-color: var(--app-accent-strong, #2563eb);
      box-shadow: 0 4px 12px rgb(53 109 255 / 25%);
    }

    .captcha-canvas {
      display: block;
      width: 130px;
      height: 40px;
    }

    .captcha-refresh {
      padding: 1px 0 2px;
      font-size: 10px;
      line-height: 1.2;
      text-align: center;
      color: var(--app-text-muted, #64748b);
      background: #fff;
    }
  }
}

/* 单点登录按钮：全宽、白底描边、hover 蓝色反馈 */
.sso-button {
  width: 100%;
  height: 44px;
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  border: 1px solid var(--app-surface-border, #cbd5e1);
  border-radius: 12px;
  transition: all 0.2s;

  .sso-icon {
    width: 18px;
    height: 18px;
    margin-right: 8px;
    color: #0ea5e9;
  }

  &:hover {
    color: var(--app-accent-strong, #2563eb);
    border-color: var(--app-accent-strong, #2563eb);
    background: rgb(53 109 255 / 6%);
  }
}

/* 分隔线：或使用账号登录 */
.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 6px 0 18px;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--app-surface-border, #e2e8f0);
  }

  span {
    font-size: 12px;
    color: var(--app-text-muted, #94a3b8);
  }
}

.form-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;

  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: var(--app-text-muted, #64748b);
  }
}

.submit-row {
  margin-bottom: 16px;
}

.submit-button {
  width: 100%;
  font-weight: 700;
  letter-spacing: 4px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #356dff, #0ea5e9);
  box-shadow: 0 18px 34px rgb(53 109 255 / 22%);
}

.demo-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--app-elevated-soft-bg, #f4f7fb);

  .demo-label {
    font-size: 12px;
    color: var(--app-text-muted, #64748b);
  }

  .demo-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}

/* ---------------- 页脚 ---------------- */
.login-footer {
  margin-top: 28px;
  font-size: 12px;
  color: rgb(226 232 240 / 68%);
}

/* ---------------- 窄屏适配 ---------------- */
@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
