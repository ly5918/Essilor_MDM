<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
      <el-form-item label="Hierarchy Type" prop="hierarchyType">
        <el-select v-model="form.hierarchyType" style="width: 100%">
          <el-option v-for="item in HIER_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="关系类型" prop="relationType">
        <el-select v-model="form.relationType" style="width: 100%">
          <el-option v-for="item in HIER_RELATION_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="父客户 One ID" prop="parentId">
        <el-input v-model="form.parentId" />
      </el-form-item>
      <el-form-item label="子客户 One ID" prop="childId">
        <el-input v-model="form.childId" />
      </el-form-item>
      <el-form-item label="Payer One ID">
        <el-input v-model="form.payerOneId" />
      </el-form-item>
      <el-form-item label="生效日期" prop="effectiveDate">
        <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="变更原因" prop="reason">
        <el-input v-model="form.reason" placeholder="如 新门店归属确认" />
      </el-form-item>
      <el-form-item label="校验示例" prop="validationCase">
        <el-select v-model="form.validationCase" style="width: 100%" @change="resetValidation">
          <el-option v-for="item in HIER_VALIDATION_CASE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-alert
      :type="validation.type"
      :closable="false"
      show-icon
      :title="validation.title"
      class="m-t-12"
    >
      <div v-html="validation.message" />
    </el-alert>

    <el-alert type="info" :closable="false" show-icon class="m-t-12">
      <template #title>
        <b>审批流：</b>{{ flowText }}
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { getHierarchyNode } from '@/api/demo/cmdPoc';
import type { HierarchyRelationForm } from '@/api/demo/cmdPoc/types';
import {
  HIER_RELATION_OPTIONS,
  HIER_TYPE_OPTIONS,
  HIER_VALIDATION_CASE_OPTIONS
} from '../../constants/options';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocHierarchyAddDialog' });

const props = defineProps<{ payload?: { mode?: string; nodeKey?: string } }>();

const { roleKey } = useCmdPoc();
const formRef = ref<FormInstance>();

const mode = computed(() => (props.payload?.mode as 'request' | 'manage' | 'child' | 'edit') || 'manage');
const nodeKey = computed(() => props.payload?.nodeKey || 'store');

const form = reactive<HierarchyRelationForm>({
  hierarchyType: 'Legal Hierarchy',
  relationType: HIER_RELATION_OPTIONS[0],
  parentId: 'CN-CUS-000021',
  childId: 'CN-CUS-000129',
  payerOneId: 'GC-PY-0092',
  effectiveDate: '2026-09-17',
  reason: '新门店归属确认',
  validationCase: 'pass'
});

const validation = reactive({ type: 'warning' as 'success' | 'warning' | 'error', title: '提交前校验', message: '父子节点不同 · A3→A2→A1 级别约束 · 多父冲突 · 完整路径循环' });

const rules: FormRules<HierarchyRelationForm> = {
  hierarchyType: [{ required: true, message: '请选择层级类型', trigger: 'change' }],
  relationType: [{ required: true, message: '请选择关系类型', trigger: 'change' }],
  parentId: [{ required: true, message: '请输入父客户 One ID', trigger: 'blur' }],
  childId: [{ required: true, message: '请输入子客户 One ID', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
  validationCase: [{ required: true, message: '请选择校验示例', trigger: 'change' }]
};

const flowText = computed(() => {
  if (mode.value === 'request') return 'Business User 提交 → BU Data Steward 审核 → 通过后发布；如为跨 BU 关系则升级 GC Scope。';
  if (roleKey.value === 'gc') return 'GC Scope 审核跨 BU 关系 → 发布 → 审计记录。';
  return 'BU Data Steward 处理本 BU 关系；跨 BU 或重大 A2/A3 关系升级 GC Scope。';
});

const runValidation = () => {
  const c = form.validationCase;
  if (c === 'pass') {
    validation.type = 'success';
    validation.title = '校验通过';
    validation.message = '✓ 父子节点不同　✓ 层级级别有效　✓ 未发现多父冲突　✓ 未发现循环路径';
    return;
  }
  validation.type = 'error';
  validation.title = '校验失败 · BLOCKED';
  if (c === 'same') {
    validation.message = `<div class="hier-loop">父子节点不能相同：${form.parentId} → ${form.parentId}</div>当前关系不会进入审批。`;
  } else if (c === 'multiple') {
    validation.message = '<div class="hier-loop">多父冲突：CN-CUS-000129 已存在有效父节点 CN-CUS-000031</div>当前关系不会进入审批。';
  } else {
    validation.message = '<div class="hier-loop">完整路径循环：CN-CUS-000001 → CN-CUS-000021 → CN-CUS-000125 → CN-CUS-000001</div>当前关系不会进入审批。';
  }
};

const resetValidation = () => {
  validation.type = 'warning';
  validation.title = '提交前校验';
  validation.message = '父子节点不同 · A3→A2→A1 级别约束 · 多父冲突 · 完整路径循环';
};

watch(() => form.validationCase, runValidation, { immediate: true });

onMounted(async () => {
  const node = await getHierarchyNode(nodeKey.value);
  if (node) {
    form.parentId = node.oneId;
  }
});

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  if (form.validationCase !== 'pass') {
    runValidation();
    throw new Error('校验未通过，请修正关系后再提交');
  }
  return mode.value === 'request'
    ? '层级关系申请已提交至 BU Data Steward'
    : mode.value === 'edit'
      ? '层级关系已保存，审计日志已写入'
      : '层级关系已提交审批';
};

defineExpose({ submit });
</script>

<style lang="scss" scoped>
:deep(.hier-loop) {
  margin-top: 6px;
  padding: 8px;
  background: #fff0f2;
  border: 1px solid #f3c3c6;
  border-radius: 6px;
  color: #9e3c48;
  font-size: 13px;
}
</style>
