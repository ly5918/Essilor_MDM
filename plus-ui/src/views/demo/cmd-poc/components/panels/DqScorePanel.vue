<template>
  <section class="page">
    <!-- 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">操作区</span>
          <div class="card-toolbar-right">
            <el-button plain icon="Refresh" @click="openDialog('reEvaluate')">历史数据重评估</el-button>
            <el-button type="primary" plain icon="SetUp" @click="openDialog('dq')">配置规则</el-button>
          </div>
        </div>
      </template>
      <p class="text-tip">配置 DQ 规则后，可对历史数据执行重评估并保留版本影响记录。</p>
    </el-card>

    <div class="two-col">
      <!-- 客户质量分数 -->
      <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">客户质量分数 · {{ scorecard.oneId }}</span></template>
        <div class="score-layout">
          <div class="score-ring" :style="{ background: ringBackground }">{{ scorecard.overall }}</div>
          <el-table border :data="scorecard.dimensions" class="data-table">
            <el-table-column label="维度" prop="dimension" min-width="100" />
            <el-table-column label="权重" prop="weight" width="90" align="center" />
            <el-table-column label="得分" prop="score" width="90" align="center">
              <template #default="{ row }">
                <span :class="scoreClass(row.score)">{{ row.score }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <!-- 规则版本影响 -->
      <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">规则版本影响</span></template>
        <el-table border :data="scorecard.versions" class="data-table">
          <el-table-column label="版本" prop="version" min-width="120" />
          <el-table-column label="规则" prop="ruleCount" width="90" align="center" />
          <el-table-column label="历史分数" min-width="110" align="center">
            <template #default="{ row }">{{ row.version.includes('Draft') ? '未计算' : scorecard.overall }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Current' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          class="m-t-12"
          type="warning"
          :closable="false"
          show-icon
          title="规则变更默认不静默覆盖历史分数。需执行显式重评估Job，并保留旧规则版本、旧分数和新分数。"
        />
      </el-card>
    </div>

    <!-- 质量异常 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">质量异常</span></template>
      <el-table v-loading="loading" border :data="scorecard.exceptions" class="data-table">
        <el-table-column label="规则" prop="name" min-width="200" />
        <el-table-column label="类型" prop="type" width="110" align="center" />
        <el-table-column label="级别" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 'Blocking' ? 'danger' : 'warning'" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 'Blocking' ? 'danger' : 'warning'" size="small" effect="plain">
              {{ row.level === 'Blocking' ? 'Failed' : 'Warning' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" prop="action" min-width="160" />
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getDqScorecard } from '@/api/demo/cmdPoc';
import type { DqScorecardVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocDqScorePanel' });

const { openDialog } = useCmdPoc();

const loading = ref(false);
const scorecard = ref<DqScorecardVO>({ oneId: '-', legalName: '', overall: 0, dimensions: [], versions: [], exceptions: [] });

/** 分数环：conic-gradient 按分值着色 */
const ringBackground = computed(() => {
  const score = scorecard.value.overall;
  const color = score >= 90 ? '#3f8d52' : score >= 80 ? '#db7c18' : '#d9534f';
  return `conic-gradient(${color} 0 ${score}%, #dfe8ee ${score}%)`;
});

const scoreClass = (score: number) => (score >= 90 ? 'dq-score s-ok' : score >= 80 ? 'dq-score s-mid' : 'dq-score s-bad');

onMounted(async () => {
  loading.value = true;
  try {
    scorecard.value = await getDqScorecard();
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}
</style>
