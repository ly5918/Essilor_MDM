<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="One ID" prop="oneId"><el-input v-model="form.oneId" readonly /></el-form-item>
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
        <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="说明" prop="remark"><el-input v-model="form.remark" /></el-form-item>
    </el-form>

    <div class="danger-box">
      <b>不执行物理删除。</b> 主记录、One ID、版本、审批及交叉引用均保留。
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { deactivateCustomer } from '@/api/demo/cmdPoc';
import type { DeactivateForm } from '@/api/demo/cmdPoc/types';
import { DEACTIVATE_REASON_OPTIONS, DEACTIVATE_STATUS_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocDeactivateDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const formRef = ref<FormInstance>();
const form = reactive<DeactivateForm>({
  oneId: 'GC-000245',
  targetStatus: 'Inactive',
  reason: DEACTIVATE_REASON_OPTIONS[0],
  effectiveDate: '2026-09-15',
  remark: '已完成业务确认'
});

const rules: FormRules<DeactivateForm> = {
  oneId: [{ required: true, message: 'One ID 不能为空', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  return deactivateCustomer(form);
};

onMounted(() => {
  if (props.payload?.oneId) form.oneId = props.payload.oneId as string;
});

defineExpose({ submit });
</script>
