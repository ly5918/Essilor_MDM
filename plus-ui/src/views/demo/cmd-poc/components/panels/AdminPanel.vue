<template>
  <section class="page">
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

    <!-- 平台管理：9 张卡片，1:1 对齐原型 admin() 顺序与描述 -->
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

const { openDialog, goMenu } = useCmdPoc();

const publishing = ref(false);

/** 顺序与描述 1:1 取自原型 admin() */
const adminCards: AdminCard[] = [
  { title: '字段与值集', desc: '配置、版本、测试和发布管理。', actionText: '管理', dialog: 'fields' },
  { title: 'One ID规则', desc: '编码模式、生命周期与Legacy Code映射。', actionText: '管理', page: 'oneid' },
  { title: '角色与权限', desc: '技术角色、Scope、字段与操作。', actionText: '管理', dialog: 'permissions' },
  { title: 'DQ规则', desc: '技术规则、业务规则和版本。', actionText: '模拟测试', dialog: 'dq', extended: true },
  { title: '匹配规则', desc: '信用代码、经营地址和辅助线索。', actionText: '模拟测试', dialog: 'match', extended: true },
  { title: '导入Template', desc: '按业务上下文管理模板与映射。', actionText: '管理', dialog: 'template', extended: true },
  // Workflow：点击「管理」进入工作流定义页（CMD 业务场景 ↔ Warm-Flow 流程定义映射、
  // 部署与泳道图），页内每行可再打开按场景的 Workflow配置
  // （流程节点 / 路由条件 / SLA 与升级 / 版本与发布，对齐 V6.1 第 16 页）
  { title: 'Workflow', desc: '按BU和场景配置审批路由。', actionText: '管理', page: 'flowDefinition', extended: true },
  { title: 'DQ Scorecard', desc: '质量维度、规则版本与历史重评估。', actionText: '查看', page: 'dqscore', extended: true },
  { title: '集成配置', desc: 'API、File、Batch与Retry策略。', actionText: '管理', page: 'integration', extended: true }
];

const onCardAction = (card: AdminCard) => {
  if (card.dialog) openDialog(card.dialog);
  // 能力页下钻：带上 'admin' 作为面包屑上级（CMD POC / 平台管理 / xxx）
  else if (card.page) goMenu(card.page, 'admin');
};
</script>
