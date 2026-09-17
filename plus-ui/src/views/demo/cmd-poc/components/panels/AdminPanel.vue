<template>
  <section class="page">
    <!-- 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">操作区</span>
          <el-button type="primary" plain icon="Promotion" :loading="publishing" @click="onPublish">发布配置版本</el-button>
        </div>
      </template>
      <p class="text-tip">统一管理字段、DQ/匹配规则、导入模板、Workflow、角色权限、One ID 与 DQ Scorecard。</p>
    </el-card>

    <el-alert
      class="platform-scope-note m-b-12"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #title>
        <span class="platform-star">*</span> 平台扩展能力，实施范围与优先级待后续确认
      </template>
    </el-alert>

    <!-- 平台管理 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 18px' }">
      <template #header><span class="card-title">平台管理</span></template>
      <div class="admin-grid">
        <el-card
          v-for="card in adminCards"
          :key="card.title"
          class="admin-card"
          :class="{ 'extended-capability': card.extended }"
          shadow="hover"
          :body-style="{ padding: '16px 18px' }"
        >
          <span v-if="card.extended" class="capability-star" title="平台扩展能力，实施范围与优先级待后续确认">*</span>
          <h3>{{ card.title }}</h3>
          <p>{{ card.desc }}</p>
          <el-button size="small" plain @click="onCardAction(card)">{{ card.actionText }}</el-button>
        </el-card>
      </div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { PageId } from '@/api/demo/cmdPoc/types';
import type { DialogKey } from '../../constants/dialogs';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocAdminPanel' });

interface AdminCard {
  title: string;
  desc: string;
  actionText: string;
  dialog?: DialogKey;
  page?: PageId;
  /** 平台扩展能力标记 */
  extended?: boolean;
}

const { openDialog, goMenu, publishMetadata } = useCmdPoc();

const publishing = ref(false);

const adminCards: AdminCard[] = [
  { title: '字段与值集', desc: '维护GC Core、BU与来源系统字段。', actionText: '管理', dialog: 'fields' },
  { title: 'DQ规则', desc: '技术规则、业务规则和版本。', actionText: '模拟测试', dialog: 'dq', extended: true },
  { title: '匹配规则', desc: '信用代码、经营地址和辅助线索。', actionText: '模拟测试', dialog: 'match', extended: true },
  { title: '导入模板', desc: '按业务上下文管理模板与映射。', actionText: '管理', dialog: 'template', extended: true },
  { title: 'Workflow', desc: '按BU和场景配置审批路由。', actionText: '管理', dialog: 'workflow', extended: true },
  { title: '角色与权限', desc: '技术角色、Scope、字段与操作。', actionText: '管理', dialog: 'permissions' },
  { title: 'One ID规则', desc: '编码模式、自动生成、生命周期与Legacy Code映射。', actionText: '管理', page: 'oneid' },
  { title: 'DQ Scorecard', desc: '质量维度、分数卡、规则版本与历史重评估。', actionText: '查看', page: 'dqscore', extended: true }
];

const onCardAction = (card: AdminCard) => {
  if (card.dialog) openDialog(card.dialog);
  else if (card.page) goMenu(card.page);
};

const onPublish = async () => {
  publishing.value = true;
  try {
    await publishMetadata();
  } finally {
    publishing.value = false;
  }
};
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}
</style>
