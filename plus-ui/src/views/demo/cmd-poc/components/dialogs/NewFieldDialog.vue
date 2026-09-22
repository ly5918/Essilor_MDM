<template>
  <div class="poc-form">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="字段编码" prop="code">
        <el-input v-model="form.code" placeholder="如 store_grade" :disabled="!!props.field" />
      </el-form-item>
      <el-form-item label="显示名称" prop="label">
        <el-input v-model="form.label" placeholder="如 门店等级" />
      </el-form-item>
      <el-form-item label="字段类型" prop="type">
        <el-select v-model="form.type" style="width: 100%">
          <el-option v-for="item in FIELD_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据层级" prop="scope">
        <el-select v-model="form.scope" style="width: 100%">
          <el-option v-for="item in FIELD_SCOPE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用BU" prop="bu">
        <el-select v-model="form.bu" style="width: 100%">
          <el-option v-for="item in FIELD_BU_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用Customer Type" prop="customerType">
        <el-select v-model="form.customerType" style="width: 100%">
          <el-option v-for="item in FIELD_CUSTOMER_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否必填" prop="required">
        <el-switch v-model="form.required" />
      </el-form-item>
      <el-form-item label="目标版本" prop="versionNo">
        <el-select v-model="form.versionNo" style="width: 100%" :disabled="!!props.field">
          <el-option v-for="item in props.versions ?? []" :key="item.version" :label="`${item.version}（${item.status === 'Current' ? '已发布' : 'Draft'}）`" :value="item.version" />
        </el-select>
      </el-form-item>
      <el-form-item label="默认值" prop="defaultValue">
        <el-input v-model="form.defaultValue" placeholder="可选" />
      </el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="保存后为Draft；发布模型版本后，Business User表单自动渲染该字段。若设为必填，发布前应显示受影响的历史记录数量。"
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { saveMetadataField } from '@/api/demo/cmdPoc';
import type { MetadataFieldVO, ModelVersionVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { FIELD_BU_OPTIONS, FIELD_CUSTOMER_TYPE_OPTIONS, FIELD_SCOPE_OPTIONS, FIELD_TYPE_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocNewFieldDialog' });

const props = defineProps<{ field?: MetadataFieldVO; versions?: ModelVersionVO[]; defaultVersion?: string; payload?: Record<string, unknown> }>();
const emit = defineEmits<{ saved: [field: MetadataFieldVO] }>();

const { upsertMetadataField } = useCmdPoc();

const formRef = ref<FormInstance>();
/** 默认值对齐原型：store_grade / 门店等级 / BU Specific / Text */
const form = reactive<MetadataFieldVO>(
  props.field
    ? { ...props.field }
    : {
        code: 'store_grade',
        label: '门店等级',
        scope: 'BU Specific',
        type: 'Text',
        required: false,
        bu: 'High End',
        customerType: 'Door',
        versionNo: props.defaultVersion,
        status: 'Draft'
      }
);

const rules: FormRules<MetadataFieldVO> = {
  code: [{ required: true, message: '字段编码不能为空', trigger: 'blur' }],
  label: [{ required: true, message: '显示名称不能为空', trigger: 'blur' }]
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  const payload: MetadataFieldVO = { ...form, status: props.field?.status ?? 'Draft' };
  const message = await saveMetadataField(payload);
  upsertMetadataField(payload);
  emit('saved', payload);
  return message;
};

defineExpose({ submit });
</script>
