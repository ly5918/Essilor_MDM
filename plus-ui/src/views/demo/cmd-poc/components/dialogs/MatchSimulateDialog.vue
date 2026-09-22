<template>
  <div class="poc-dialog-body">
    <!-- ① 样例记录（对齐总设计「运行测试数据集：选择已知重复与非重复样本」） -->
    <div class="sim-section-title">样例记录</div>
    <div class="sim-sample">
      <el-radio-group v-model="sampleMode" size="small">
        <el-radio-button value="master">从主档选择</el-radio-button>
        <el-radio-button value="manual">手工输入</el-radio-button>
      </el-radio-group>
      <template v-if="sampleMode === 'master'">
        <el-select
          v-model="sampleOneId"
          filterable
          clearable
          size="small"
          placeholder="选择一条主档客户作为样例"
          class="sim-sample-select"
          :loading="customersLoading"
        >
          <el-option
            v-for="c in customers"
            :key="c.oneId"
            :label="`${c.legalName}（${c.oneId}）`"
            :value="c.oneId"
          />
        </el-select>
      </template>
      <template v-else>
        <el-input v-model="manualForm.legalName" size="small" placeholder="客户名称" class="sim-manual-input" />
        <el-input v-model="manualForm.creditCode" size="small" placeholder="统一社会信用代码" class="sim-manual-input" />
        <el-input v-model="manualForm.address" size="small" placeholder="经营地址" class="sim-manual-input" />
        <el-select v-model="manualForm.bu" size="small" placeholder="BU" clearable class="sim-manual-bu">
          <el-option v-for="b in BU_OPTIONS" :key="b" :label="b" :value="b" />
        </el-select>
      </template>
    </div>

    <!-- ② 匹配规则清单（真实 match_rule 表数据） -->
    <div class="sim-section-title m-t-12">规则清单（共 {{ rules.length }} 条）</div>
    <el-table border :data="rules" class="data-table" max-height="240" size="small">
      <el-table-column label="规则编码" prop="ruleCode" min-width="120" />
      <el-table-column label="规则名称" prop="ruleName" min-width="150" show-overflow-tooltip />
      <el-table-column label="应用场景" width="84" align="center">
        <template #default="{ row }">{{ sceneLabel(asRule(row).scene) }}</template>
      </el-table-column>
      <el-table-column label="算法" width="92" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" type="info">{{ asRule(row).algorithm ?? '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Exact 阈值" width="92" align="center">
        <template #default="{ row }"><span class="sim-mono">{{ pct(asRule(row).exactThreshold) }}</span></template>
      </el-table-column>
      <el-table-column label="Suspected 阈值" width="108" align="center">
        <template #default="{ row }"><span class="sim-mono">{{ pct(asRule(row).suspectThreshold) }}</span></template>
      </el-table-column>
      <el-table-column label="自动合并" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="asRule(row).autoMergeFlag === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
            {{ asRule(row).autoMergeFlag === 'Y' ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="跨BU" width="72" align="center">
        <template #default="{ row }">
          <el-tag :type="asRule(row).crossBuFlag === 'Y' ? 'warning' : 'info'" size="small" effect="plain">
            {{ asRule(row).crossBuFlag === 'Y' ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="72" align="center">
        <template #default="{ row }">
          <el-tag :type="asRule(row).status === '1' ? 'success' : 'info'" size="small" effect="plain">
            {{ statusLabel(asRule(row).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="104" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="onSimulateOne(asRule(row))">模拟</el-button>
          <el-button link type="danger" size="small" @click="onDelete(asRule(row))">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ③ 模拟结果：候选与解释（字段贡献 / 相似度 / 候选 One ID / 结果分类 + 分布） -->
    <template v-if="simResult">
      <div class="sim-section-title m-t-12">
        模拟结果
        <span class="sim-result-meta">
          样例「{{ simResult.sample.legalName || simResult.sample.creditCode || '手工输入' }}」 ·
          规则 {{ simResult.rule.ruleName }}（Exact ≥ {{ pct(simResult.rule.exactThreshold) }} / Suspected ≥ {{ pct(simResult.rule.suspectThreshold) }}）
          · 扫描 {{ simResult.scanned }} 条主档
        </span>
      </div>
      <div class="sim-dist">
        <div class="sim-dist-item">
          <b class="sim-dist-exact">{{ simResult.distribution.exact }}</b><span>Exact（可自动关联）</span>
        </div>
        <div class="sim-dist-item">
          <b class="sim-dist-suspect">{{ simResult.distribution.suspected }}</b><span>Suspected（需 Steward 判断）</span>
        </div>
        <div class="sim-dist-item">
          <b>{{ simResult.distribution.below }}</b><span>低于 Suspected 阈值</span>
        </div>
      </div>
      <el-table :data="simResult.candidates" class="data-table m-t-8" size="small" max-height="220" empty-text="无候选（样例可创建新 One ID）">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="sim-field-contrib">
              <div v-for="f in asCand(row).fields" :key="f.field" class="sim-field-row">
                <span class="sim-field-label">{{ f.label }}</span>
                <el-progress
                  class="sim-field-bar"
                  :percentage="f.score"
                  :stroke-width="8"
                  :color="f.score >= 100 ? '#67c23a' : f.score >= 70 ? '#e6a23c' : '#f56c6c'"
                />
                <span class="sim-field-detail">权重 {{ f.weight }} · {{ f.detail }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="候选 One ID" prop="oneId" min-width="104" />
        <el-table-column label="客户名称" prop="legalName" min-width="150" show-overflow-tooltip />
        <el-table-column label="信用代码" prop="creditCode" min-width="130" show-overflow-tooltip />
        <el-table-column label="BU" prop="buScope" width="92" />
        <el-table-column label="综合相似度" width="96" align="center">
          <template #default="{ row }"><b class="sim-mono">{{ asCand(row).score }}%</b></template>
        </el-table-column>
        <el-table-column label="分类" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="verdictTag(asCand(row).result)" size="small">{{ verdictText(asCand(row).result) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <div class="sim-footer-meta">点「模拟」用当前样例测试单条规则；点右下角「开始测试」用第一条已发布规则模拟。</div>

    <!-- 新增规则（对齐总设计「调整匹配字段与标准化 / 设置阈值与结果分层」） -->
    <el-dialog v-model="addVisible" title="新增匹配规则" width="520" append-to-body destroy-on-close>
      <el-form :model="addForm" label-width="104px" size="small">
        <el-form-item label="规则名称" required>
          <el-input v-model="addForm.ruleName" placeholder="如：客户匹配规则-并购场景" maxlength="60" />
        </el-form-item>
        <el-form-item label="应用场景" required>
          <el-select v-model="addForm.scene" class="w-full">
            <el-option v-for="s in SCENES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="算法" required>
          <el-select v-model="addForm.algorithm" class="w-full">
            <el-option label="加权相似度（WEIGHTED）" value="WEIGHTED" />
            <el-option label="信用代码全等（EXACT）" value="EXACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Exact 阈值" required>
          <el-input-number v-model="addForm.exactThreshold" :min="50" :max="100" :step="1" size="small" controls-position="right" />
          <span class="sim-threshold-hint">≥ 该分数判定为 Exact，可自动关联已有 One ID</span>
        </el-form-item>
        <el-form-item label="Suspected 阈值" required>
          <el-input-number v-model="addForm.suspectThreshold" :min="30" :max="99" :step="1" size="small" controls-position="right" />
          <span class="sim-threshold-hint">≥ 该分数进入 Steward 人工判断队列</span>
        </el-form-item>
        <el-form-item label="超阈值自动合并">
          <el-switch v-model="addAutoMerge" />
        </el-form-item>
        <el-form-item label="参与跨 BU 匹配">
          <el-switch v-model="addCrossBu" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveRule">保存为草稿并参与模拟</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listMatchRules, saveMatchRule, deleteMatchRule, simulateMatch, listCustomers } from '@/api/demo/cmdPoc';
import type {
  CustomerQuery,
  CustomerVO,
  MatchRuleRow,
  MatchSimulateResultVO,
  MatchCandidateVO
} from '@/api/demo/cmdPoc/types';
import { BU_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocMatchSimulateDialog' });

const SCENES = [
  { value: 'CREATE', label: '新建客户（CREATE）' },
  { value: 'IMPORT', label: '批量导入（IMPORT）' },
  { value: 'BATCH', label: '批量治理（BATCH）' },
  { value: 'MERGE', label: '合并（MERGE）' }
];

const rules = ref<MatchRuleRow[]>([]);
const customers = ref<CustomerVO[]>([]);
const customersLoading = ref(false);
const saving = ref(false);
const running = ref(false);
const simResult = ref<MatchSimulateResultVO | null>(null);

/** 样例来源 */
const sampleMode = ref<'master' | 'manual'>('master');
const sampleOneId = ref<string>('');
const manualForm = reactive({ legalName: '', creditCode: '', address: '', bu: '' });

const asRule = (row: unknown): MatchRuleRow => row as MatchRuleRow;
const asCand = (row: unknown): MatchCandidateVO => row as MatchCandidateVO;

const loadRules = async () => {
  rules.value = await listMatchRules();
};

const loadCustomers = async () => {
  customersLoading.value = true;
  try {
    const query: CustomerQuery = { pageNum: 1, pageSize: 200 };
    const page = await listCustomers(query);
    customers.value = page.rows;
  } finally {
    customersLoading.value = false;
  }
};

onMounted(() => {
  loadRules();
  loadCustomers();
});

/* ---------- 展示辅助 ---------- */
const sceneLabel = (scene?: string) => SCENES.find(s => s.value === scene)?.label.replace(/（.*）/, '') ?? scene ?? '—';
const statusLabel = (status?: string) => (status === '1' ? '已发布' : status === '2' ? '已停用' : '草稿');
const pct = (v?: number | string) => (v == null || v === '' ? '—' : `${Number(v)}%`);
const verdictText = (v: string) => (v === 'EXACT' ? 'Exact' : v === 'SUSPECTED' ? 'Suspected' : 'Below');
const verdictTag = (v: string): 'success' | 'warning' | 'info' => (v === 'EXACT' ? 'success' : v === 'SUSPECTED' ? 'warning' : 'info');

/** 当前样例 → 请求体（对齐后端 MatchSimulateForm） */
const buildForm = (ruleId?: number) => ({
  oneId: sampleMode.value === 'master' ? sampleOneId.value || undefined : undefined,
  legalName: sampleMode.value === 'manual' ? manualForm.legalName || undefined : undefined,
  creditCode: sampleMode.value === 'manual' ? manualForm.creditCode || undefined : undefined,
  address: sampleMode.value === 'manual' ? manualForm.address || undefined : undefined,
  bu: sampleMode.value === 'manual' ? manualForm.bu || undefined : undefined,
  ruleId
});

const ensureSample = (): string | null => {
  if (sampleMode.value === 'master' && !sampleOneId.value) return '请先选择一条样例客户';
  if (sampleMode.value === 'manual' && !manualForm.legalName && !manualForm.creditCode && !manualForm.address) {
    return '请至少填写名称 / 信用代码 / 地址中的一项';
  }
  return null;
};

/* ---------- 模拟执行 ---------- */
const runSimulate = async (ruleId?: number) => {
  const noSample = ensureSample();
  if (noSample) {
    ElMessage.warning(noSample);
    return Promise.reject(new Error(noSample));
  }
  running.value = true;
  try {
    simResult.value = await simulateMatch(buildForm(ruleId));
    return simResult.value;
  } finally {
    running.value = false;
  }
};

const onSimulateOne = async (rule: MatchRuleRow) => {
  const res = await runSimulate(rule.id);
  const d = res.distribution;
  ElMessage.success(`规则 ${rule.ruleCode} 模拟完成：Exact ${d.exact} / Suspected ${d.suspected}`);
};

/** 开始测试（弹窗底部「开始测试」按钮 → DialogHost submit 约定） */
const submit = async (): Promise<string> => {
  const res = await runSimulate();
  const d = res.distribution;
  return `样例模拟完成：扫描 ${res.scanned} 条主档，Exact ${d.exact} / Suspected ${d.suspected} / New（低于阈值）${d.below}`;
};
defineExpose({ submit, keepOpenAfterSubmit: true });

/* ---------- 删除 ---------- */
const onDelete = async (rule: MatchRuleRow) => {
  if (!rule.id) return;
  await ElMessageBox.confirm(`确认删除规则「${rule.ruleName ?? rule.ruleCode}」？历史任务仍引用其原版本结果。`, '删除确认', { type: 'warning' });
  await deleteMatchRule(rule.id);
  await loadRules();
  ElMessage.success('规则已删除');
};

/* ---------- 新增规则 ---------- */
const addVisible = ref(false);
/** 表单内阈值用 number，提交时再对齐 MatchRuleRow（number | string） */
const addForm = reactive({
  ruleName: '',
  scene: 'CREATE',
  algorithm: 'WEIGHTED',
  exactThreshold: 95,
  suspectThreshold: 70,
  autoMergeFlag: 'N',
  crossBuFlag: 'Y'
});
const addAutoMerge = computed({
  get: () => addForm.autoMergeFlag === 'Y',
  set: (v: boolean) => (addForm.autoMergeFlag = v ? 'Y' : 'N')
});
const addCrossBu = computed({
  get: () => addForm.crossBuFlag === 'Y',
  set: (v: boolean) => (addForm.crossBuFlag = v ? 'Y' : 'N')
});

const onSaveRule = async () => {
  if (!addForm.ruleName?.trim()) {
    ElMessage.warning('请填写规则名称');
    return;
  }
  if (Number(addForm.suspectThreshold) > Number(addForm.exactThreshold)) {
    ElMessage.warning('Suspected 阈值不能高于 Exact 阈值');
    return;
  }
  saving.value = true;
  try {
    await saveMatchRule({
      ...addForm,
      ruleCode: `MR_${(addForm.scene ?? 'X').slice(0, 3).toUpperCase()}_${Date.now().toString().slice(-5)}`,
      status: '0'
    });
    addVisible.value = false;
    await loadRules();
    ElMessage.success('规则已保存为草稿，可直接参与样例模拟');
  } finally {
    saving.value = false;
  }
};
</script>

<style scoped>
.sim-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--g-text);
  margin-bottom: 8px;
}

.sim-result-meta {
  font-weight: 400;
  font-size: 12px;
  color: var(--g-text2);
}

.sim-mono {
  font-size: 12px;
  color: var(--g-text2);
}

.sim-na {
  font-size: 12px;
  color: var(--g-text3, #a0a6ad);
}

.sim-sample {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  background: var(--app-elevated-soft-bg, #f8fafc);
  border-radius: 8px;
}

.sim-sample-select {
  width: 320px;
}

.sim-manual-input {
  width: 168px;
}

.sim-manual-bu {
  width: 110px;
}

.sim-dist {
  display: flex;
  flex-wrap: wrap;
  padding: 8px 0;
  background: var(--app-elevated-soft-bg, #f8fafc);
  border-radius: 8px;
}

.sim-dist-item {
  flex: 1;
  min-width: 140px;
  padding: 0 14px;

  & + .sim-dist-item {
    border-left: 1px solid var(--g-divider);
  }

  b {
    display: block;
    font-size: 16px;
    color: var(--g-text);
    line-height: 1.4;
  }

  span {
    font-size: 12px;
    color: var(--g-text2);
  }
}

.sim-dist-exact {
  color: var(--el-color-success) !important;
}

.sim-dist-suspect {
  color: var(--el-color-warning) !important;
}

.sim-field-contrib {
  padding: 4px 24px 8px 40px;
}

.sim-field-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 0;
}

.sim-field-label {
  width: 110px;
  font-size: 12px;
  color: var(--g-text2);
  text-align: right;
  flex-shrink: 0;
}

.sim-field-bar {
  width: 200px;
}

.sim-field-detail {
  font-size: 12px;
  color: var(--g-text3, #a0a6ad);
}

.sim-footer-meta {
  margin-top: 10px;
  font-size: 12px;
  color: var(--g-text3, #a0a6ad);
}

.sim-threshold-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--g-text3, #a0a6ad);
}

.m-t-8 {
  margin-top: 8px;
}

.w-full {
  width: 100%;
}
</style>
