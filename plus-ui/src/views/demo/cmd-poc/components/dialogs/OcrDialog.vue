<template>
  <div class="poc-dialog-body">
    <!-- 上传区：选择营业执照后仅本地预览，需点击「开始识别」才展示识别结果 -->
    <div class="ocr-upload">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :show-file-list="false"
        accept="image/*"
        :on-change="onFileChange"
        :on-exceed="onExceed"
      >
        <el-button plain icon="Upload">选择营业执照</el-button>
      </el-upload>
      <span class="upload-name">{{ fileName || '未选择文件（支持 JPG / PNG，建议分辨率 ≥ 1280px）' }}</span>
      <el-button
        type="primary"
        :plain="!pendingRecognize"
        icon="View"
        :loading="recognizing"
        @click="onRecognize"
      >
        开始识别
      </el-button>
    </div>

    <!-- 营业执照预览 + 执照原件信息（原型 showOCR：左侧预览，右侧 4 行信息） -->
    <div class="ocr-preview">
      <div class="ocr-image">
        <el-image v-if="previewUrl" :src="previewUrl" fit="contain" class="ocr-img" :preview-src-list="[previewUrl]">
          <template #error>
            <div class="ocr-img-error">预览失败</div>
          </template>
        </el-image>
        <div v-else class="ocr-img-empty">营业执照预览</div>
        <div class="ocr-image-tip">营业执照预览</div>
      </div>

      <div v-loading="recognizing" class="ocr-license">
        <div class="ocr-license-row">
          <span>统一社会信用代码</span>
          <b>{{ license?.creditCode || '—' }}</b>
        </div>
        <div class="ocr-license-row">
          <span>名称</span>
          <b>{{ license?.name || '—' }}</b>
        </div>
        <div class="ocr-license-row">
          <span>类型</span>
          <b>{{ license?.type || '—' }}</b>
        </div>
        <div class="ocr-license-row">
          <span>住所</span>
          <b>{{ license?.address || '—' }}</b>
        </div>
      </div>
    </div>

    <!-- 识别结果：字段 / 识别值 / 置信度 -->
    <el-table
      v-loading="recognizing"
      border
      :data="fields"
      class="data-table"
      :empty-text="fileName ? '点击「开始识别」查看识别结果' : '请先上传营业执照'"
    >
      <el-table-column label="字段" prop="field" min-width="130" />
      <el-table-column label="识别值" prop="value" min-width="230" show-overflow-tooltip />
      <el-table-column label="置信度" prop="confidence" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="confidenceType(row.confidence)" size="small">{{ row.confidence }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="ocr-tip">低置信度字段需要人工确认后才能写入新建客户表单。</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage, type UploadFile, type UploadInstance } from 'element-plus';
import { ocrRecognize } from '@/api/demo/cmdPoc';
import type { OcrLicenseVO, OcrRecognizeVO, OcrResultVO } from '@/api/demo/cmdPoc/types';
import type { DialogKey } from '../../constants/dialogs';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocOcrDialog' });

// payload：全局弹窗宿主传入的上下文（本弹窗不使用）
// embedded：是否内嵌在「新建客户申请」弹窗内（true 时「写回表单」直接回填当前表单，
//            false 时由 DialogHost 在关闭本弹窗后自动打开「新建客户申请」并回填）
const props = withDefaults(defineProps<{ payload?: Record<string, unknown>; embedded?: boolean }>(), {
  embedded: false
});

const emit = defineEmits<{ apply: [results: OcrResultVO[]] }>();

/** OCR 结果共享通道：确认后暂存，供「新建客户申请」表单回填 */
const { setOcrPrefill } = useCmdPoc();

const uploadRef = ref<UploadInstance>();
const fields = ref<OcrResultVO[]>([]);
const license = ref<OcrLicenseVO | null>(null);
const fileName = ref('');
const previewUrl = ref('');
const recognizing = ref(false);
/** 「写回表单」后需要自动切换到的弹窗（全局入口用；内嵌模式恒为空） */
const nextDialog = ref<DialogKey | ''>('');

const confidenceType = (confidence: string) => {
  const value = parseInt(confidence, 10);
  if (value >= 98) return 'success';
  return value >= 90 ? 'warning' : 'danger';
};

/** 已选营业执照但尚未识别（用于高亮「开始识别」按钮，引导用户主动点击） */
const pendingRecognize = computed(() => !!fileName.value && !fields.value.length);

/**
 * 统一的「选中一张新营业执照」处理：
 * 更新文件名与本地预览 → 清空上一次的识别结果（右侧执照信息 + 下方字段表）。
 * <p>
 * 这里不再自动识别：换图后右侧一律先清空为「— / 暂无数据」，
 * 由用户点击「开始识别」再拉取并展示本次结果，避免旧结果被误认为新图的识别结果。
 */
const handleSelected = (name: string, raw?: File) => {
  fileName.value = name || '营业执照';
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
  }
  if (raw) {
    previewUrl.value = URL.createObjectURL(raw);
  }
  clearResult();
  ElMessage.success(`已选择文件：${fileName.value}，请点击「开始识别」`);
};

/** 清空上一次的识别结果（执照原件信息 + 字段表） */
const clearResult = () => {
  license.value = null;
  fields.value = [];
};

/**
 * 选择文件：仅更新本地预览并清空旧结果，等用户点「开始识别」。
 * <p>
 * 注意：el-upload 在 limit=1 时会把第二个文件挡在 on-exceed 里、不再触发 on-change，
 * 所以每次处理完都要 clearFiles() 清空组件内部的 fileList，
 * 否则「上传一次之后换新文件就没反应」（只能识别一次）。
 */
const onFileChange = (file: UploadFile) => {
  handleSelected(file.name ?? '', file.raw);
  uploadRef.value?.clearFiles();
};

/**
 * 超过 limit=1（例如上一次文件还在列表里又选了新文件）：
 * 清空旧文件并把新文件接过来处理，效果等同于「重新选择即替换」。
 */
const onExceed = (files: File[]) => {
  uploadRef.value?.clearFiles();
  const raw = files?.[0];
  if (raw) {
    handleSelected(raw.name, raw);
  }
};

const onRecognize = async () => {
  // 未选择营业执照时不发起识别：后端对空文件名会回退到默认素材，
  // 会出现「没上传任何图片却显示一堆识别值」的假结果，故在此直接拦截并提示。
  if (!fileName.value) {
    ElMessage.warning('请先上传营业执照，再点击「开始识别」');
    return;
  }
  recognizing.value = true;
  try {
    const data: OcrRecognizeVO = await ocrRecognize(fileName.value);
    license.value = data.license ?? null;
    fields.value = data.fields ?? [];
    ElMessage.success('OCR 识别完成');
  } finally {
    recognizing.value = false;
  }
};

/**
 * 写回表单：
 * 1. 结果先暂存到共享通道（全局入口时表单尚未打开，打开「新建客户申请」会自动回填）；
 * 2. 内嵌在「新建客户申请」内（embedded）时直接派发 apply，由父组件立即回填并打「OCR回填」标签；
 * 3. 全局入口（客户管理页顶部「查看OCR识别结果」）时设置 nextDialog，
 *    由 DialogHost 在关闭本弹窗后自动打开「新建客户申请」并预填识别结果。
 * <p>
 * 未上传营业执照 / 未点「开始识别」时抛错阻断（弹窗不会关闭），
 * 由 DialogHost 或父组件的 catch 统一提示。
 */
const submit = async (): Promise<string> => {
  if (!fileName.value) {
    throw new Error('请先上传营业执照并完成识别，再写回表单');
  }
  // 上传后不再自动识别，未点「开始识别」时字段表为空，此处直接拦截并提示，不做静默识别
  if (!fields.value.length) {
    throw new Error('请先点击「开始识别」，识别出字段后再写回表单');
  }
  const names = fields.value.map(item => item.field).join('、');
  const summary = `${fields.value.length} 个字段：${names}`;
  if (props.embedded) {
    // 内嵌：表单就在当前弹窗里，直接派发 apply 由父组件立即回填（不经过共享通道，避免重复回填）
    emit('apply', fields.value);
    return `OCR 结果已写入新建客户申请表（${summary}）`;
  }
  // 全局入口：结果走共享通道暂存，并请求 DialogHost 关闭本弹窗后打开「新建客户申请」自动回填
  setOcrPrefill(fields.value);
  nextDialog.value = 'newCustomer';
  return `OCR 结果已带入「新建客户申请」并自动回填（${summary}）`;
};

defineExpose({ submit, nextDialog });
</script>

<style scoped lang="scss">
.ocr-upload {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;

  .upload-name {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.ocr-preview {
  display: flex;
  gap: 14px;
  margin: 12px 0;
}

.ocr-image {
  width: 300px;
  flex-shrink: 0;
}

.ocr-img {
  width: 300px;
  height: 200px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.ocr-img-empty,
.ocr-img-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 300px;
  height: 200px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  background: var(--el-fill-color-lighter);
}

.ocr-image-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.ocr-license {
  flex: 1;
  min-height: 200px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.ocr-license-row {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--el-border-color-lighter);

  &:last-child {
    border-bottom: none;
  }

  span {
    width: 130px;
    flex-shrink: 0;
    color: var(--el-text-color-secondary);
  }

  b {
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

.ocr-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
