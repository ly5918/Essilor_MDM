<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="One ID" prop="oneId"><el-input v-model="form.oneId" readonly /></el-form-item>
      <el-form-item label="变更类型" prop="changeType">
        <el-select v-model="form.changeType" style="width: 100%">
          <el-option v-for="item in CHANGE_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="字段" prop="field">
        <el-select v-model="form.field" style="width: 100%">
          <el-option v-for="item in CHANGE_FIELD_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="新值" prop="newValue"><el-input v-model="form.newValue" /></el-form-item>
      <el-form-item label="变更原因" prop="reason"><el-input v-model="form.reason" /></el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交后执行DQ与Duplicate Check；审批通过后更新主档，One ID保持GC-000128不变。"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { submitChangeRequest } from '@/api/demo/cmdPoc';
import type { ChangeRequestForm } from '@/api/demo/cmdPoc/types';
import { CHANGE_FIELD_OPTIONS, CHANGE_TYPE_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocChangeRequestDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const formRef = ref<FormInstance>();
const form = reactive<ChangeRequestForm>({
  oneId: 'GC-000128',
  changeType: CHANGE_TYPE_OPTIONS[0],
  field: CHANGE_FIELD_OPTIONS[0],
  newValue: '上海市静安区南京西路888号',
  reason: '营业场所迁址'
});

const rules: FormRules<ChangeRequestForm> = {
  oneId: [{ required: true, message: 'One ID 不能为空', trigger: 'blur' }],
  newValue: [{ required: true, message: '请填写新值', trigger: 'blur' }]
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  return submitChangeRequest(form);
};

onMounted(() => {
  if (props.payload?.oneId) form.oneId = props.payload.oneId as string;
});

defineExpose({ submit });
</script>
