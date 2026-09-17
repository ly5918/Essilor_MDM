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
        <b>{{ candidate.score }}% · {{ candidate.verdict }}</b>
        <span>{{ candidate.reason }}</span>
      </div>
    </el-card>

    <!-- 候选对比 -->
    <div class="compare">
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
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { confirmNewCustomer, getDuplicateCandidate, linkExistingOneId } from '@/api/demo/cmdPoc';
import type { DuplicateCandidateVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocGovernancePanel' });

/** 候选对比数据来自接口；页面内无弹窗调度，故不注入 openDialog */
const candidate = ref<DuplicateCandidateVO>({ score: 0, verdict: '-', reason: '', incoming: {}, existing: {} });
const submitting = ref(false);

const onLink = async () => {
  submitting.value = true;
  try {
    ElMessage.success(await linkExistingOneId(String(candidate.value.existing['One ID'] ?? 'GC-000128')));
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
