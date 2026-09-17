<template>
  <header class="navbar">
    <!-- 顶部导航栏：品牌 / 环境标识 / 面包屑 / 模拟角色 / 头像（融合 RuoYi 自带能力） -->
    <div class="navbar-left">
      <el-tooltip content="隐藏/显示菜单" effect="dark" placement="bottom">
        <div class="hamburger-shell">
          <hamburger
            id="hamburger-container"
            :is-active="!sidebarCollapsed"
            class="hamburger-container"
            @toggle-click="toggleSidebar"
          />
        </div>
      </el-tooltip>
      <div class="brand-logo">EL</div>
      <span class="brand-title">Customer Master Data · POC</span>
      <el-tag class="env-tag" size="small" effect="plain">Demo Environment</el-tag>
    </div>

    <div class="right-menu flex align-center">
      <!-- 全屏（RuoYi 自带） -->
      <el-tooltip content="全屏" effect="dark" placement="bottom">
        <div class="right-menu-item hover-effect"><screenfull /></div>
      </el-tooltip>

      <!-- 布局大小（RuoYi 自带） -->
      <el-tooltip content="布局大小" effect="dark" placement="bottom">
        <div class="right-menu-item hover-effect"><size-select /></div>
      </el-tooltip>

      <!-- 模拟角色切换 -->
      <span class="role-label">模拟角色</span>
      <el-select v-model="roleKey" class="role-select" @change="onRoleChange">
        <el-option
          v-for="item in ROLE_LIST"
          :key="item.key"
          :label="ROLE_DROPDOWN_LABELS[item.key]"
          :value="item.key"
        />
      </el-select>

      <!-- 头像下拉：布局设置 / 退出登录（RuoYi 自带） -->
      <el-dropdown class="avatar-dropdown" trigger="click" @command="handleCommand">
        <div class="top-avatar" :style="{ background: role.color }">
          <span class="avatar-alias">{{ role.alias }}</span>
          <el-icon class="avatar-caret"><caret-bottom /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="setLayout">布局设置</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 布局设置抽屉（RuoYi 自带） -->
    <settings ref="settingRef" />
  </header>
</template>

<script setup lang="ts">
import { CaretBottom } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import Settings from '@/layout/components/Settings/index.vue';
import Hamburger from '@/components/Hamburger/index.vue';
import Screenfull from '@/components/Screenfull/index.vue';
import SizeSelect from '@/components/SizeSelect/index.vue';
import tab from '@/plugins/tab';
import { useUserStore } from '@/store/modules/user';
import { useCmdPoc } from '../composables/useCmdPoc';
import { ROLE_DROPDOWN_LABELS, ROLE_LIST } from '../constants/roles';

defineOptions({ name: 'CmdPocNavbar' });

const { roleKey, role, pageTitle, goMenu, sidebarCollapsed, toggleSidebar } = useCmdPoc();
const router = useRouter();
const userStore = useUserStore();
const settingRef = ref<InstanceType<typeof Settings>>();

/** 角色切换：每个角色对应一个独立路由页面 */
const onRoleChange = () => {
  const target = `/cmd-poc/${roleKey.value}`;
  if (router.currentRoute.value.path !== target) {
    router.push(target);
    ElMessage.info(`已切换为模拟角色：${role.value.name}（${role.value.scope}）`);
  }
};

/** 打开布局设置抽屉 */
const openSetting = () => settingRef.value?.openSetting();

/** 退出登录 */
const logout = async () => {
  await ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  });
  await userStore.logout();
  tab.closeAllPage();
  router.replace({
    path: '/login',
    query: {
      redirect: encodeURIComponent(router.currentRoute.value.fullPath || '/')
    }
  });
};

const commandMap: Record<string, () => void> = {
  setLayout: openSetting,
  logout
};
const handleCommand = (command: string) => {
  commandMap[command]?.();
};
</script>

<style lang="scss" scoped>
.navbar-left {
  .hamburger-shell {
    width: 30px;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    background: var(--g-content);
    border: 1px solid var(--g-divider);
    color: var(--g-text2);
    flex-shrink: 0;
    cursor: pointer;
    transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;

    &:hover {
      background: var(--g-card);
      color: var(--g-text);
      border-color: var(--el-color-primary-light-5);
    }
  }

  .hamburger-container {
    line-height: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    :deep(.hamburger) {
      width: 18px;
      height: 18px;
      fill: currentColor;
    }
  }

  .navbar-divider {
    width: 1px;
    height: 20px;
    background: var(--g-divider);
    margin: 0 6px;
    flex-shrink: 0;
  }

  .poc-breadcrumb {
    display: inline-flex;
    align-items: center;
    font-size: 13px;
    color: var(--g-text2);
    margin-left: 2px;

    :deep(.el-breadcrumb__inner) {
      color: inherit;
      font-weight: 500;
    }

    .crumb-link {
      color: var(--g-text2);
      cursor: pointer;

      &:hover {
        color: var(--g-text);
      }
    }

    :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
      color: var(--g-text);
      cursor: text;
    }
  }
}

.right-menu {
  .right-menu-item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    font-size: 16px;
    color: var(--g-text2);
    border-radius: 8px;
    background: transparent;
    border: 1px solid transparent;
    flex-shrink: 0;

    :deep(.svg-icon),
    :deep(svg),
    :deep(.el-icon) {
      width: 16px;
      height: 16px;
      font-size: 16px;
      display: block;
    }

    &.hover-effect {
      cursor: pointer;
      transition:
        background 0.3s,
        color 0.3s;

      &:hover {
        background: var(--g-content);
        color: var(--g-text);
        border-color: var(--g-divider);
      }
    }
  }

  .role-select {
    width: 180px;
  }

  .avatar-dropdown {
    outline: none;

    .top-avatar {
      cursor: pointer;
      width: auto;
      min-width: 30px;
      height: 30px;
      padding: 0 8px 0 10px;
      border-radius: 16px;
      color: #fff;
      font-size: 12px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      gap: 2px;
      transition:
        transform 0.15s ease,
        filter 0.15s ease;

      &:hover {
        transform: translateY(-1px);
        filter: brightness(1.05);
      }

      .avatar-alias {
        white-space: nowrap;
      }

      .avatar-caret {
        font-size: 12px;
        opacity: 0.85;
      }
    }
  }
}

.flex {
  display: flex;
}

.align-center {
  align-items: center;
}
</style>
