<template>
  <section class="page">
    <!-- 匹配结论 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">匹配结论</span>
          <div class="card-toolbar-right">
            <el-button type="success" plain icon="Connection" :loading="submitting" @click="onLink">关联已有One ID</el-button>
            <el-button type="warning" plain icon="CircleCheck" :loading="submitting" @click="onConfirmNew">确认新客户</el-button>
          </div>
        </div>
      </template>

      <div class="score-head">
        <span class="score-num">{{ candidate.score }}%</span>
        <el-tag :type="verdictTagType" effect="light">{{ candidate.verdict }}</el-tag>
        <span class="score-reason">{{ candidate.reason }}</span>
        <span v-if="targetOneId" class="score-target">候选主档：<b>{{ targetOneId }}</b></span>
      </div>
      <el-alert
        v-if="candidate.acceptHint"
        class="accept-hint"
        type="warning"
        :closable="false"
        show-icon
        :title="candidate.acceptHint"
      />
    </el-card>

    <!-- 分组逐字段对比（借鉴 DCR Matching Review：字段级命中高亮） -->
    <template v-if="hasGroups">
      <el-card
        v-for="group in candidate.groups"
        :key="group.name"
        class="page-card dup-group"
        shadow="never"
        :body-style="{ padding: '8px 20px 12px' }"
      >
        <template #header><span class="card-title">{{ group.name }}</span></template>
        <div class="dup-head">
          <span class="dup-label">对比字段</span>
          <span>新申请</span>
          <span>现有主档</span>
          <span class="dup-flag">匹配</span>
        </div>
        <div v-for="field in group.fields" :key="field.label" class="dup-row">
          <span class="dup-label">{{ field.label }}</span>
          <span class="dup-val" :class="statusClass(field)">{{ field.incoming || '（空）' }}</span>
          <span class="dup-val" :class="statusClass(field)">{{ field.existing || '（空）' }}</span>
          <span class="dup-flag">
            <el-tag size="small" :type="flagTagType(field.status)" effect="plain">{{ flagText(field.status) }}</el-tag>
          </span>
        </div>
      </el-card>
    </template>

    <!-- 回退：无分组数据时的左右两栏对比 -->
    <div v-else class="compare">
      <el-card class="box" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">新申请 · High End</span></template>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="(value, key) in candidate.incoming" :key="key" :label="String(key)">
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="box" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">现有主档 · Mainstream</span></template>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="(value, key) in candidate.existing" :key="key" :label="String(key)">
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { confirmNewCustomer, getDuplicateCandidate, linkExistingOneId } from '@/api/demo/cmdPoc';
import type { DuplicateCandidateVO, DuplicateFieldMatch, DuplicateFieldStatus } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocGovernancePanel' });

/** 候选对比数据来自接口；页面内无弹窗调度，故不注入 openDialog */
const candidate = ref<DuplicateCandidateVO>({ score: 0, verdict: '-', reason: '', incoming: {}, existing: {} });
const submitting = ref(false);

const hasGroups = computed(() => (candidate.value.groups?.length ?? 0) > 0);
const targetOneId = computed(() => candidate.value.existingOneId || String(candidate.value.existing['One ID'] ?? ''));

const VERDICT_TAG: Record<string, 'success' | 'warning' | 'info'> = {
  'Exact Match': 'success',
  'Suspected Match': 'warning',
  New: 'info'
};
const verdictTagType = computed(() => VERDICT_TAG[candidate.value.verdict] ?? 'warning');

const FLAG_TEXT: Record<DuplicateFieldStatus, string> = { MATCH: '一致', DIFF: '不一致', EMPTY: '空缺' };
const FLAG_TAG: Record<DuplicateFieldStatus, 'success' | 'danger' | 'info'> = { MATCH: 'success', DIFF: 'danger', EMPTY: 'info' };
const flagText = (status: DuplicateFieldStatus) => FLAG_TEXT[status] ?? status;
const flagTagType = (status: DuplicateFieldStatus) => FLAG_TAG[status] ?? 'info';
const statusClass = (field: DuplicateFieldMatch) => `is-${field.status.toLowerCase()}`;

const onLink = async () => {
  const oneId = targetOneId.value || 'GC-000128';
  try {
    await ElMessageBox.confirm(candidate.value.acceptHint || `确认将本申请关联到已有 One ID ${oneId}？`, '关联已有 One ID', {
      confirmButtonText: '确认关联',
      cancelButtonText: '取消',
      type: 'warning'
    });
  } catch {
    return;
  }
  submitting.value = true;
  try {
    ElMessage.success(await linkExistingOneId(oneId));
  } finally {
    submitting.value = false;
  }
};

const onConfirmNew = async () => {
  submitting.value = true;
  try {
    ElMessage.success(await confirmNewCustomer());
  } finally {
    submitting.value = false;
  }
};

onMounted(async () => {
  candidate.value = await getDuplicateCandidate();
});
</script>

<style scoped>
.score-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.score-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-warning);
}
.score-reason {
  color: var(--el-text-color-secondary);
}
.score-target {
  color: var(--el-text-color-primary);
}
.accept-hint {
  margin-top: 10px;
}

.dup-group + .dup-group {
  margin-top: 4px;
}
.dup-head,
.dup-row {
  display: grid;
  grid-template-columns: 160px 1fr 1fr 72px;
  gap: 10px;
  align-items: center;
  padding: 6px 0;
}
.dup-head {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 8px;
}
.dup-row + .dup-row {
  border-top: 1px dashed var(--el-border-color-lighter);
}
.dup-label {
  color: var(--el-text-color-regular);
  font-weight: 600;
}
.dup-val {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 13px;
  word-break: break-all;
}
.dup-val.is-match {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.dup-val.is-diff {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.dup-val.is-empty {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-placeholder);
}
.dup-flag {
  text-align: center;
}
</style>
