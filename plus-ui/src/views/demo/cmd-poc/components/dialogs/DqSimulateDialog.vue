<template>
  <div class="poc-dialog-body">
    <!-- ① 规则草稿清单：真实 dq_rule 表数据，支持新增 / 删除 / 单条模拟 -->
    <div class="sim-section-title sim-section-flex">
      <span>规则清单（含草稿，共 {{ rules.length }} 条）</span>
      <el-button type="primary" plain size="small" icon="Plus" @click="onAddRule">新增规则</el-button>
    </div>
    <el-table border :data="rules" class="data-table" max-height="300" size="small">
      <el-table-column label="规则编码" prop="ruleCode" min-width="96" />
      <el-table-column label="规则名称" prop="ruleName" min-width="130" show-overflow-tooltip />
      <el-table-column label="维度" min-width="86" align="center">
        <template #default="{ row }">{{ dimensionName(asRule(row).dimension) }}</template>
      </el-table-column>
      <el-table-column label="校验字段" min-width="104" align="center">
        <template #default="{ row }">
          <span class="sim-mono">{{ fieldLabel(asRule(row).fieldCode) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="76" align="center">
        <template #default="{ row }">
          <el-tag :type="asRule(row).severity === 'ERROR' ? 'danger' : 'warning'" size="small" effect="plain">
            {{ asRule(row).severity === 'ERROR' ? '阻断' : '警告' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="权重" width="60" align="center">
        <template #default="{ row }"><span class="sim-mono">{{ asRule(row).scoreWeight ?? '—' }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="72" align="center">
        <template #default="{ row }">
          <el-tag :type="asRule(row).status === '1' ? 'success' : 'info'" size="small" effect="plain">
            {{ statusLabel(asRule(row).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="86" align="center">
        <template #default="{ row }">
          <template v-if="resultOf(asRule(row))">
            <el-tooltip :disabled="!sampleText(asRule(row))" placement="top">
              <template #content>
                <div class="sim-sample-tip">{{ sampleText(asRule(row)) }}</div>
              </template>
              <el-tag :type="resultTagType(asRule(row))" size="small">{{ resultText(asRule(row)) }}</el-tag>
            </el-tooltip>
          </template>
          <span v-else class="sim-na">待测试</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="104" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="onSimulateOne(asRule(row))">模拟</el-button>
          <el-button link type="danger" size="small" @click="onDelete(asRule(row))">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ② 测试数据集选择（对齐总设计「选择测试数据集」节点） -->
    <div class="sim-section-title m-t-12">测试数据集</div>
    <div class="sim-dataset">
      <el-select v-model="form.customerType" placeholder="客户类型：全部" clearable size="small" class="sim-select">
        <el-option v-for="t in CUSTOMER_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="form.bu" placeholder="BU：全部" clearable size="small" class="sim-select">
        <el-option v-for="b in BU_OPTIONS" :key="b" :label="b" :value="b" />
      </el-select>
      <el-select v-model="form.sourceSystem" placeholder="来源系统：全部" clearable size="small" class="sim-select">
        <el-option v-for="s in SOURCE_SYSTEM_OPTIONS" :key="s" :label="s" :value="s" />
      </el-select>
      <span class="sim-dataset-label">样例数量</span>
      <el-input-number v-model="form.sampleSize" :min="10" :max="1000" :step="50" size="small" controls-position="right" class="sim-size" />
    </div>

    <!-- ③ 影响评估摘要（对齐总设计「影响评估」节点：受影响客户 / 新增异常 / Score 变化） -->
    <template v-if="impact">
      <div class="sim-section-title m-t-12">影响评估</div>
      <div class="sim-impact">
        <div class="sim-impact-item"><b>{{ result?.datasetSize }}</b><span>测试集客户</span></div>
        <div class="sim-impact-item"><b class="sim-block">{{ impact.blockHits }}</b><span>Block 命中</span></div>
        <div class="sim-impact-item"><b class="sim-warn">{{ impact.warningHits }}</b><span>Warning 命中</span></div>
        <div class="sim-impact-item"><b>{{ impact.affectedCustomers }}</b><span>受影响客户</span></div>
        <div class="sim-impact-item"><b class="sim-block">{{ impact.avgScoreDelta }}</b><span>预计平均分变化</span></div>
      </div>
    </template>

    <div class="sim-footer-meta">点「模拟」测试单条规则；点右下角「开始测试」对整个数据集执行全部规则。</div>

    <!-- 新增规则（对齐总设计「新增 / 编辑 DQ 规则」节点） -->
    <el-dialog v-model="addVisible" title="新增 DQ 规则" width="520" append-to-body destroy-on-close>
      <el-form :model="addForm" label-width="92px" size="small">
        <el-form-item label="规则名称" required>
          <el-input v-model="addForm.ruleName" placeholder="如：Payer 必填" maxlength="60" />
        </el-form-item>
        <el-form-item label="质量维度" required>
          <el-select v-model="addForm.dimension" class="w-full">
            <el-option v-for="d in DIMENSIONS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验字段" required>
          <el-select v-model="addForm.fieldCode" filterable class="w-full">
            <el-option v-for="f in FIELD_OPTIONS" :key="f.value" :label="`${f.label}（${f.value}）`" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验类型" required>
          <el-select v-model="addForm.checkType" class="w-full">
            <el-option v-for="c in CHECK_TYPES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needsExpression" label="校验表达式" required>
          <el-input v-model="addForm.expression" :placeholder="expressionPlaceholder" />
        </el-form-item>
        <el-form-item label="严重级别" required>
          <el-radio-group v-model="addForm.severity">
            <el-radio value="ERROR">阻断（Block）</el-radio>
            <el-radio value="WARNING">警告（Warning）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="扣分权重">
          <el-input-number v-model="addForm.scoreWeight" :min="1" :max="100" size="small" controls-position="right" />
        </el-form-item>
        <el-form-item label="失败提示">
          <el-input v-model="addForm.errorMessage" placeholder="命中后展示给 Steward 的提示文案（可选）" maxlength="200" />
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
import { listDqRules, saveDqRule, deleteDqRule, simulateDq } from '@/api/demo/cmdPoc';
import type { DqRuleRow, DqSimulateForm, DqSimulateResultVO, DqRuleSimulateVO } from '@/api/demo/cmdPoc/types';
import { BU_OPTIONS, CUSTOMER_TYPE_OPTIONS, SOURCE_SYSTEM_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocDqSimulateDialog' });

const DIMENSIONS = [
  { value: 'COMPLETENESS', label: '完整性' },
  { value: 'VALIDITY', label: '有效性' },
  { value: 'CONSISTENCY', label: '一致性' },
  { value: 'UNIQUENESS', label: '唯一性' },
  { value: 'TIMELINESS', label: '及时性' }
];

const CHECK_TYPES = [
  { value: 'NOT_NULL', label: '非空校验（NOT_NULL）' },
  { value: 'REGEX', label: '正则格式（REGEX）' },
  { value: 'LENGTH', label: '长度区间（LENGTH，如 2,200）' },
  { value: 'RANGE', label: '数值区间（RANGE，如 0,99999999）' },
  { value: 'UNIQUE', label: '唯一性（UNIQUE）' },
  { value: 'CROSS_FIELD', label: '跨字段（CROSS_FIELD，如 address.contains(city)）' }
];

/** 主档模型已启用的可校验字段（对齐 cmd_customer 实际列） */
const FIELD_OPTIONS = [
  { value: 'legal_name', label: '客户名称' },
  { value: 'legal_name_en', label: '英文名称' },
  { value: 'short_name', label: '简称' },
  { value: 'credit_code', label: '统一社会信用代码' },
  { value: 'contact_email', label: '联系邮箱' },
  { value: 'contact_phone', label: '联系电话' },
  { value: 'address', label: '经营地址' },
  { value: 'city', label: '城市' },
  { value: 'postal_code', label: '邮编' },
  { value: 'payer_id', label: 'Payer' },
  { value: 'customer_type', label: '客户类型' },
  { value: 'bu_scope', label: 'BU 归属' },
  { value: 'source_system', label: '来源系统' }
];

const rules = ref<DqRuleRow[]>([]);
const running = ref(false);
const saving = ref(false);
const result = ref<DqSimulateResultVO | null>(null);

/** 结果列按规则编码索引（后端模拟结果回填） */
const resultMap = computed(() => {
  const map = new Map<string, DqRuleSimulateVO>();
  (result.value?.rules ?? []).forEach(r => map.set(r.ruleCode, r));
  return map;
});
const impact = computed(() => result.value?.impact ?? null);

/** el-table 插槽行类型为 DefaultRow，此处收敛断言 */
const asRule = (row: unknown): DqRuleRow => row as DqRuleRow;

/** 测试数据集筛选（选择测试数据集节点） */
const form = reactive<DqSimulateForm>({ customerType: '', bu: '', sourceSystem: '', sampleSize: 200 });

const loadRules = async () => {
  rules.value = await listDqRules();
  result.value = null;
};

onMounted(loadRules);

/* ---------- 展示辅助 ---------- */
const dimensionName = (code?: string) => DIMENSIONS.find(d => d.value === code)?.label ?? code ?? '—';
const fieldLabel = (code?: string) => FIELD_OPTIONS.find(f => f.value === code)?.label ?? code ?? '—';
const statusLabel = (status?: string) => (status === '1' ? '已发布' : status === '2' ? '已停用' : '草稿');

const resultOf = (rule: DqRuleRow) => resultMap.value.get(rule.ruleCode ?? '');
const resultText = (rule: DqRuleRow) => {
  const r = resultOf(rule);
  if (!r) return '';
  if (r.note) return 'N/A';
  if (r.block > 0) return 'Block';
  if (r.warn > 0) return 'Warning';
  return 'Pass';
};
const resultTagType = (rule: DqRuleRow): 'danger' | 'warning' | 'success' | 'info' => {
  const r = resultOf(rule);
  if (!r) return 'info';
  if (r.block > 0) return 'danger';
  if (r.warn > 0) return 'warning';
  return 'success';
};
/** 字段级错误样例（对齐总设计「输出 Pass / Warning / Block 及字段级错误说明」） */
const sampleText = (rule: DqRuleRow) => {
  const r = resultOf(rule);
  if (!r) return '';
  if (r.note) return r.note;
  const lines = r.samples.map(s => `${s.oneId} ${s.legalName}：${s.message}`);
  return lines.length ? lines.join('\n') : '';
};

/* ---------- 模拟执行 ---------- */
/** 单条规则模拟 */
const onSimulateOne = async (rule: DqRuleRow) => {
  if (!rule.id) return;
  running.value = true;
  try {
    const res = await simulateDq({ ...form, ruleId: rule.id });
    result.value = res;
    ElMessage.success(`规则 ${rule.ruleCode} 模拟完成：${resultText(rule)}（${resultOf(rule)?.passRate ?? ''}）`);
  } finally {
    running.value = false;
  }
};

/** 开始测试（弹窗底部「开始测试」按钮 → DialogHost submit 约定） */
const submit = async (): Promise<string> => {
  if (!rules.value.length) {
    ElMessage.warning('当前没有可测试的规则，请先新增');
    return Promise.reject(new Error('当前没有可测试的规则，请先新增'));
  }
  running.value = true;
  try {
    result.value = await simulateDq({ ...form });
    const i = result.value.impact;
    return `模拟完成：${result.value.ruleCount} 条规则 × ${result.value.datasetSize} 条客户记录，Block ${i.blockHits} / Warning ${i.warningHits}`;
  } finally {
    running.value = false;
  }
};
defineExpose({ submit, keepOpenAfterSubmit: true });

/* ---------- 删除 ---------- */
const onDelete = async (rule: DqRuleRow) => {
  if (!rule.id) return;
  await ElMessageBox.confirm(`确认删除规则「${rule.ruleName ?? rule.ruleCode}」？删除后历史模拟结果保留。`, '删除确认', { type: 'warning' });
  await deleteDqRule(rule.id);
  await loadRules();
  ElMessage.success('规则已删除');
};

/* ---------- 新增规则 ---------- */
const addVisible = ref(false);
/** 表单内权重等数值列用 number，提交时再对齐 DqRuleRow（number | string） */
const addForm = reactive({
  ruleName: '',
  dimension: 'COMPLETENESS',
  fieldCode: 'legal_name',
  checkType: 'NOT_NULL',
  expression: '',
  severity: 'WARNING',
  scoreWeight: 10,
  errorMessage: ''
});

const needsExpression = computed(() => ['REGEX', 'LENGTH', 'RANGE', 'CROSS_FIELD'].includes(addForm.checkType ?? ''));
const expressionPlaceholder = computed(() => {
  switch (addForm.checkType) {
    case 'REGEX': return '^[0-9A-HJ-NPQRTUWXY]{18}$';
    case 'LENGTH': return '2,200';
    case 'RANGE': return '0,99999999';
    case 'CROSS_FIELD': return 'address.contains(city)';
    default: return '';
  }
});

const onAddRule = () => {
  addVisible.value = true;
};

const onSaveRule = async () => {
  if (!addForm.ruleName?.trim()) {
    ElMessage.warning('请填写规则名称');
    return;
  }
  if (needsExpression.value && !addForm.expression?.trim()) {
    ElMessage.warning('请填写校验表达式');
    return;
  }
  saving.value = true;
  try {
    await saveDqRule({
      ...addForm,
      ruleCode: `DQ_${(addForm.dimension ?? 'X').slice(0, 1)}_${Date.now().toString().slice(-5)}`,
      ruleType: 'TECHNICAL',
      status: '0'
    });
    addVisible.value = false;
    await loadRules();
    ElMessage.success('规则已保存为草稿，可直接参与模拟测试');
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

  &.sim-section-flex {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}

.sim-mono {
  font-family: var(--el-font-family, inherit);
  font-size: 12px;
  color: var(--g-text2);
}

.sim-na {
  font-size: 12px;
  color: var(--g-text3, #a0a6ad);
}

.sim-sample-tip {
  white-space: pre-line;
  max-width: 360px;
  line-height: 1.6;
}

.sim-dataset {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  background: var(--app-elevated-soft-bg, #f8fafc);
  border-radius: 8px;
}

.sim-select {
  width: 148px;
}

.sim-dataset-label {
  font-size: 12px;
  color: var(--g-text2);
}

.sim-size {
  width: 110px;
}

.sim-impact {
  display: flex;
  flex-wrap: wrap;
  padding: 8px 0;
  background: var(--app-elevated-soft-bg, #f8fafc);
  border-radius: 8px;
}

.sim-impact-item {
  flex: 1;
  min-width: 92px;
  padding: 0 14px;
  text-align: left;

  & + .sim-impact-item {
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

.sim-block {
  color: var(--el-color-danger) !important;
}

.sim-warn {
  color: var(--el-color-warning) !important;
}

.sim-footer-meta {
  margin-top: 10px;
  font-size: 12px;
  color: var(--g-text3, #a0a6ad);
}

.w-full {
  width: 100%;
}
</style>
