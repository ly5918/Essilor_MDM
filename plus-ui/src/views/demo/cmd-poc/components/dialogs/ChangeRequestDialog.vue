<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="客户" prop="oneId">
        <el-select v-model="form.oneId" filterable placeholder="选择客户（One ID）" style="width: 100%" @change="onCustomerChange">
          <el-option v-for="c in customers" :key="c.oneId" :label="`${c.legalName}（${c.oneId}）`" :value="c.oneId" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划生效日" prop="effectiveDate">
        <el-date-picker
          v-model="form.effectiveDate"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="留空 = 审批通过后立即生效"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="变更原因" prop="reason"><el-input v-model="form.reason" /></el-form-item>
    </el-form>

    <div class="cr-fields">
      <div class="cr-fields__head">
        <span class="cr-fields__title">字段级变更明细</span>
        <el-button link type="primary" icon="Plus" @click="onAddField">新增字段</el-button>
      </div>
      <div v-for="(item, idx) in form.fields" :key="idx" class="cr-row">
        <el-select
          v-model="item.fieldCode"
          placeholder="选择字段"
          style="width: 230px"
          @change="onFieldChange(item)"
        >
          <el-option v-for="opt in changeFields" :key="opt.fieldCode" :label="opt.fieldName" :value="opt.fieldCode">
            <span>{{ opt.fieldName }}</span>
            <el-tag v-if="opt.isKeyField === 'Y'" size="small" type="danger" style="margin-left: 6px">关键</el-tag>
            <el-tag v-if="opt.isSensitive === 'Y'" size="small" type="warning" style="margin-left: 6px">敏感</el-tag>
          </el-option>
        </el-select>
        <el-input v-model="item.afterValue" placeholder="变更后值" />
        <el-button link type="danger" :disabled="form.fields.length <= 1" @click="onRemoveField(idx)">移除</el-button>
      </div>
      <div class="cr-fields__tip">
        服务端用主档当前值补全 Before，形成 Before / After 证据链并落入 cmd_change_diff；
        含关键字段时自动升级审批级别，敏感字段变更额外留痕。
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交后执行 DQ 与 Duplicate Check；审批通过后写入主档新版本，One ID 保持不变。"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { getChangeFields, submitChangeRequest } from '@/api/demo/cmdPoc';
import type { ChangeFieldItem, ChangeFieldVO, ChangeRequestForm } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocChangeRequestDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const ctx = useCmdPoc();
const customers = computed(() => ctx.customers.value);
const changeFields = ref<ChangeFieldVO[]>([]);

const formRef = ref<FormInstance>();
const form = reactive<ChangeRequestForm>({
  oneId: '',
  changeType: 'Update',
  reason: '',
  effectiveDate: '',
  fields: [{ fieldCode: '', afterValue: '' }]
});

const rules: FormRules<ChangeRequestForm> = {
  oneId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  reason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }]
};

function onCustomerChange(): void {
  // 切换客户即清空差异（不同客户主档列值不同，Before 需重新比对）
  form.fields = [{ fieldCode: '', afterValue: '' }];
}

function onFieldChange(item: ChangeFieldItem): void {
  item.fieldName = changeFields.value.find(f => f.fieldCode === item.fieldCode)?.fieldName;
}

function onAddField(): void {
  form.fields.push({ fieldCode: '', afterValue: '' });
}

function onRemoveField(idx: number): void {
  form.fields.splice(idx, 1);
}

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  const diffs = form.fields.filter(f => f.fieldCode && f.afterValue.trim());
  if (!diffs.length) {
    ElMessage.warning('请至少填写一行「字段 + 变更后值」');
    throw new Error('字段级变更明细为空');
  }
  const msg = await submitChangeRequest(form);
  ctx.markChangeChanged();
  ctx.refreshBadge();
  return msg;
};

onMounted(async () => {
  if (ctx.customers.value.length === 0) {
    try {
      await ctx.loadCustomers();
    } catch {
      /* 忽略 */
    }
  }
  changeFields.value = await getChangeFields().catch(() => []);
  if (props.payload?.oneId) form.oneId = props.payload.oneId as string;
});

defineExpose({ submit });
</script>

<style scoped lang="scss">
.cr-fields {
  margin: 4px 0 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__tip {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.cr-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .el-input {
    flex: 1;
  }
}
</style>
