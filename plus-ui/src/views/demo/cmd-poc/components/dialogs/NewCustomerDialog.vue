<template>
  <div class="poc-dialog-body">
    <!-- 动态元数据提示条 -->
    <div class="meta-banner">
      <b>动态元数据表单</b>
      <span>根据 {{ contextText }} 加载字段</span>
      <el-tag type="success" size="small" effect="plain">模型版本 v1.5</el-tag>
      <el-button link type="primary" @click="onReload">刷新字段</el-button>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
      <div class="form-section">业务上下文</div>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Customer Type" prop="customerType">
            <el-select v-model="form.customerType" style="width: 100%">
              <el-option v-for="item in CUSTOMER_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BU" prop="bu">
            <el-select v-model="form.bu" style="width: 100%">
              <el-option v-for="item in BU_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Product Line" prop="productLine">
            <el-select v-model="form.productLine" style="width: 100%">
              <el-option v-for="item in PRODUCT_LINE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Source System" prop="sourceSystem">
            <el-select v-model="form.sourceSystem" style="width: 100%">
              <el-option v-for="item in SOURCE_SYSTEM_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <div class="form-section">动态客户字段</div>
      <el-row :gutter="12">
        <el-col v-for="field in dynamicFields" :key="field.code" :span="12">
          <el-form-item :label="field.label" :prop="`dynamicValues.${field.code}`" :required="field.required">
            <el-select
              v-if="field.type === 'Enum'"
              v-model="form.dynamicValues[field.code]"
              :placeholder="field.required ? '请选择' : '可选'"
              style="width: 100%"
            >
              <el-option label="A" value="A" />
              <el-option label="B" value="B" />
              <el-option label="C" value="C" />
            </el-select>
            <el-input v-else v-model="form.dynamicValues[field.code]" :placeholder="`请输入${field.label}`" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="营业执照">
        <el-button plain icon="Upload" @click="ocrVisible = true">上传并OCR识别</el-button>
        <span class="form-tip">识别结果将按字段名称自动回填</span>
      </el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="字段来自元数据配置，而不是写死在页面。发布新字段后刷新业务上下文即可加载。"
    />

    <!-- 嵌套 OCR 弹窗 -->
    <el-dialog v-model="ocrVisible" class="poc-dialog" title="OCR识别结果" width="640px" append-to-body destroy-on-close>
      <OcrDialog @apply="onApplyOcr" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { submitCustomer } from '@/api/demo/cmdPoc';
import type { CustomerForm, OcrResultVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { BU_OPTIONS, CUSTOMER_TYPE_OPTIONS, PRODUCT_LINE_OPTIONS, SOURCE_SYSTEM_OPTIONS } from '../../constants/options';
import OcrDialog from './OcrDialog.vue';

defineOptions({ name: 'CmdPocNewCustomerDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const { publishedFields } = useCmdPoc();

const formRef = ref<FormInstance>();
const ocrVisible = ref(false);

const form = reactive<CustomerForm & { dynamicValues: Record<string, string> }>({
  legalName: '',
  creditCode: '',
  address: '',
  customerType: 'Door',
  bu: 'High End',
  productLine: 'Frame',
  sourceSystem: 'Cloud',
  dynamicValues: {}
});

/** 动态必填字段的校验规则随业务上下文变化 */
const rules = computed<FormRules>(() => {
  const dynamicRules: FormRules = {};
  dynamicFields.value
    .filter(field => field.required)
    .forEach(field => {
      dynamicRules[`dynamicValues.${field.code}`] = [{ required: true, message: `请输入${field.label}`, trigger: 'blur' }];
    });
  return dynamicRules;
});

/** 按业务上下文过滤动态字段：BU / Customer Type 命中或取值为 All */
const dynamicFields = computed(() =>
  publishedFields.value.filter(
    field => (field.bu === form.bu || field.bu === 'All') && (field.customerType === form.customerType || field.customerType === 'All')
  )
);

const contextText = computed(() => `${form.customerType} · ${form.bu} · ${form.productLine} · ${form.sourceSystem}`);

/** 上下文变化时清理不适用字段的取值 */
watch(dynamicFields, fields => {
  const codes = fields.map(field => field.code);
  Object.keys(form.dynamicValues).forEach(code => {
    if (!codes.includes(code)) delete form.dynamicValues[code];
  });
});

const onReload = () => {
  // 重新触发上下文过滤即可（字段源为共享缓存，发布后自动包含新字段）
};

/** OCR 结果回填：按识别字段名称匹配动态字段标签 */
const onApplyOcr = (results: OcrResultVO[]) => {
  results.forEach(item => {
    const target = dynamicFields.value.find(field => field.label === item.field);
    if (target) {
      form.dynamicValues[target.code] = item.value;
    }
    if (item.field === '工商名称') form.legalName = item.value;
    if (item.field === '信用代码') form.creditCode = item.value;
    if (item.field === '注册地址') form.address = item.value;
  });
  ocrVisible.value = false;
};

/** 从动态字段取值中回填核心属性（字段编码 → 主档字段） */
const CORE_FIELD_MAP: Record<string, keyof CustomerForm> = {
  legal_name: 'legalName',
  credit_code: 'creditCode',
  business_address: 'address',
  payer_id: 'payerId'
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  Object.entries(CORE_FIELD_MAP).forEach(([code, key]) => {
    const value = form.dynamicValues[code];
    if (value) (form as unknown as Record<string, string>)[key as string] = value;
  });
  return submitCustomer(form);
};

defineExpose({ submit });
</script>
