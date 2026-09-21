<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="客户" prop="oneId">
        <el-select v-model="form.oneId" filterable placeholder="选择客户（One ID）" style="width: 100%">
          <el-option v-for="c in customers" :key="c.oneId" :label="`${c.legalName}（${c.oneId}）`" :value="c.oneId" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标状态" prop="targetStatus">
        <el-select v-model="form.targetStatus" style="width: 100%">
          <el-option v-for="item in DEACTIVATE_STATUS_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="停用原因" prop="reason">
        <el-select v-model="form.reason" style="width: 100%">
          <el-option v-for="item in DEACTIVATE_REASON_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期" prop="effectiveDate">
        <el-date-picker
          v-model="form.effectiveDate"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="审批通过后立即生效"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="说明" prop="remark"><el-input v-model="form.remark" /></el-form-item>
    </el-form>

    <div class="danger-box">
      <b>不执行物理删除。</b> 主记录、One ID、版本、审批及交叉引用均保留，下游接收状态变更而非物理删除。
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { submitDeactivateRequest } from '@/api/demo/cmdPoc';
import type { DeactivateForm } from '@/api/demo/cmdPoc/types';
import { DEACTIVATE_REASON_OPTIONS, DEACTIVATE_STATUS_OPTIONS } from '../../constants/options';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocDeactivateDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const ctx = useCmdPoc();
const customers = computed(() => ctx.customers.value);

const formRef = ref<FormInstance>();
const form = reactive<DeactivateForm>({
  oneId: '',
  targetStatus: 'Inactive',
  reason: DEACTIVATE_REASON_OPTIONS[0],
  effectiveDate: '',
  remark: ''
});

const rules: FormRules<DeactivateForm> = {
  oneId: [{ required: true, message: '请选择客户', trigger: 'change' }]
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  const msg = await submitDeactivateRequest(form);
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
  if (props.payload?.oneId) form.oneId = props.payload.oneId as string;
});

defineExpose({ submit });
</script>
