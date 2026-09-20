<template>
  <div class="poc-dialog-body">
    <!-- 动态元数据提示条 -->
    <div class="meta-banner">
      <b>动态元数据表单</b>
      <span>根据 {{ contextText }} 加载字段（{{ dynamicFields.length }} 个）</span>
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
          <el-form-item :prop="`dynamicValues.${field.code}`" :required="field.required">
            <template #label>
              <span class="field-label">
                {{ field.label }}
                <el-tag v-if="ocrFieldCodes.includes(field.code)" size="small" type="success" effect="plain">OCR回填</el-tag>
              </span>
            </template>
            <el-select
              v-if="field.type === 'Enum'"
              v-model="form.dynamicValues[field.code]"
              :placeholder="field.required ? '请选择' : '可选'"
              style="width: 100%"
            >
              <el-option v-for="option in enumOptions(field.code)" :key="option" :label="option" :value="option" />
            </el-select>
            <el-input v-else v-model="form.dynamicValues[field.code]" :placeholder="`请输入${field.label}`" />
          </el-form-item>
        </el-col>
        <el-col v-if="!dynamicFields.length" :span="24">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="未加载到已发布字段：请在「元数据管理」发布字段后点击「刷新字段」"
          />
        </el-col>
      </el-row>

      <el-form-item label="营业执照">
        <el-button plain icon="Upload" @click="openOcr">上传并OCR识别</el-button>
        <span class="form-tip">识别结果按字段名称回填到上方「动态客户字段」，并标记 OCR回填 标签</span>
      </el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="字段来自元数据配置，而不是写死在页面。发布新字段后刷新业务上下文即可加载。"
    />
    <el-alert
      class="submit-alert"
      type="success"
      :closable="false"
      show-icon
      title="提交申请：写入客户主档（status=pending）→ 生成统一待办 → 启动「客户创建」流程实例，进入 BU Scope 初审。"
    />

    <!-- 嵌套 OCR 弹窗：与全局 OCR 弹窗同一组件，确认后立即回填 -->
    <el-dialog v-model="ocrVisible" class="poc-dialog" title="OCR识别结果" width="640px" append-to-body destroy-on-close>
      <OcrDialog ref="ocrDialogRef" @apply="onApplyOcr" />
      <template #footer>
        <el-button @click="ocrVisible = false">关闭</el-button>
        <el-button type="primary" @click="onOcrConfirm">写回表单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { submitCustomer } from '@/api/demo/cmdPoc';
import type { CustomerForm, OcrResultVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import {
  BU_OPTIONS,
  CUSTOMER_TYPE_OPTIONS,
  FIELD_ENUM_OPTIONS,
  PRODUCT_LINE_OPTIONS,
  SOURCE_SYSTEM_OPTIONS
} from '../../constants/options';
import OcrDialog from './OcrDialog.vue';

defineOptions({ name: 'CmdPocNewCustomerDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const { publishedFields, loadCustomers, ocrPrefill, setOcrPrefill } = useCmdPoc();

/** 已在「业务上下文」维护或由系统托管的字段，不在动态区重复渲染 */
const CONTEXT_FIELD_CODES = ['customer_type', 'bu_scope', 'product_line', 'source_system', 'status'];

/** 动态字段演示默认值（国家/地区默认中国，可在表单中修改） */
const FIELD_DEFAULTS: Record<string, string> = { country: '中国' };

/** OCR 结果 → 元数据字段名称 → 动态字段编码（写回目标） */
const CORE_FIELD_MAP: Record<string, keyof CustomerForm> = {
  legal_name: 'legalName',
  credit_code: 'creditCode',
  address: 'address',
  payer_id: 'payerId'
};

const formRef = ref<FormInstance>();
const ocrDialogRef = ref<InstanceType<typeof OcrDialog>>();
const ocrVisible = ref(false);
/** 本次 OCR 回填命中的字段编码（用于打「OCR回填」标签，让回填结果可见） */
const ocrFieldCodes = ref<string[]>([]);

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
    field =>
      !CONTEXT_FIELD_CODES.includes(field.code) &&
      (field.bu === form.bu || field.bu === 'All') &&
      (field.customerType === form.customerType || field.customerType === 'All')
  )
);

const contextText = computed(() => `${form.customerType} · ${form.bu} · ${form.productLine} · ${form.sourceSystem}`);

/** 枚举字段选项：值集明细未落库，POC 阶段按字段编码取前端常量 */
const enumOptions = (code: string): string[] => FIELD_ENUM_OPTIONS[code] ?? ['A', 'B', 'C'];

/** 上下文 / 字段集变化：清理不适用字段取值，并为演示字段补默认值 */
watch(
  dynamicFields,
  fields => {
    const codes = fields.map(field => field.code);
    Object.keys(form.dynamicValues).forEach(code => {
      if (!codes.includes(code) && !CONTEXT_FIELD_CODES.includes(code)) delete form.dynamicValues[code];
    });
    Object.entries(FIELD_DEFAULTS).forEach(([code, value]) => {
      if (codes.includes(code) && !form.dynamicValues[code]) form.dynamicValues[code] = value;
    });
  },
  { immediate: true }
);

/** 消费全局 OCR 弹窗暂存的结果（顶部「查看OCR识别结果」→ 写回表单 后打开本弹窗） */
onMounted(() => {
  const pending = ocrPrefill.value;
  if (pending?.length) {
    onApplyOcr(pending);
    ElMessage.success(`已回填上次 OCR 识别结果（${pending.length} 个字段）`);
  }
});

/** 表单弹窗已打开时，监听共享通道变化并立即回填（解决「先打开表单再点全局 OCR 写回」不生效） */
watch(
  ocrPrefill,
  (pending) => {
    if (pending?.length) {
      onApplyOcr(pending);
      ElMessage.success(`已回填 OCR 识别结果（${pending.length} 个字段）`);
    }
  }
);

const onReload = () => {
  // 重新触发上下文过滤即可（字段源为共享缓存，发布后自动包含新字段）
};

const openOcr = () => {
  ocrVisible.value = true;
};

/** 嵌套 OCR 弹窗的「写回表单」 */
const onOcrConfirm = async () => {
  const message = await ocrDialogRef.value?.submit();
  ocrVisible.value = false;
  if (message) ElMessage.success(message);
};

/**
 * OCR 结果回填：
 * 1. 按字段编码写入 dynamicValues（兜底，防止当前字段不可见时丢失值）；
 * 2. 按识别字段名称匹配可见动态字段标签，回填并打「OCR回填」标签；
 * 3. 同步核心主档列，保证提交时主档列有值。
 */
const onApplyOcr = (results: OcrResultVO[]) => {
  const filledLabels: string[] = [];
  results.forEach(item => {
    if (item.code) form.dynamicValues[item.code] = item.value;
    const coreKey = CORE_FIELD_MAP[item.code];
    if (coreKey) {
      (form as unknown as Record<string, string>)[coreKey] = item.value;
    }
    const target = dynamicFields.value.find(field => field.label === item.field);
    if (target) {
      form.dynamicValues[target.code] = item.value;
      filledLabels.push(target.label);
    }
  });
  ocrFieldCodes.value = dynamicFields.value
    .filter(field => results.some(result => result.field === field.label))
    .map(field => field.code);
  if (!filledLabels.length) {
    ElMessage.warning('识别字段未匹配到已发布元数据字段，请检查字段名称');
  }
  // 已消费，避免下次打开表单重复回填
  setOcrPrefill(null);
};

/** 提交申请：后端落主档 + 生成待办 + 启动流程实例，随后刷新客户列表 */
const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  Object.entries(CORE_FIELD_MAP).forEach(([code, key]) => {
    const value = form.dynamicValues[code];
    if (value) (form as unknown as Record<string, string>)[key as string] = value;
  });
  const result = await submitCustomer(form);
  await loadCustomers();
  const node = result.currentNodeName ?? 'BU Scope 初审';
  return `客户申请已提交：One ID ${result.oneId ?? '-'}｜申请编号 ${result.taskNo ?? '-'}，已进入「${node}」，可在「治理与审批」查看待办`;
};

defineExpose({ submit });
</script>

<style scoped lang="scss">
.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.submit-alert {
  margin-top: 8px;
}
</style>
