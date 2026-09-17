<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="业务场景" prop="context">
            <el-select v-model="form.context" style="width: 100%">
              <el-option v-for="item in SCENE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板版本" prop="templateVersion">
            <el-select v-model="form.templateVersion" style="width: 100%">
              <el-option label="v1.3 Published" value="v1.3 Published" />
              <el-option label="v1.2 Draft" value="v1.2 Draft" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="上传Excel / CSV" prop="file">
            <el-upload
              drag
              action="#"
              :auto-upload="false"
              :on-change="onFileChange"
              :limit="1"
              class="upload-zone"
            >
              <el-icon class="upload-ico"><UploadFilled /></el-icon>
              <div class="upload-text">点击或拖拽文件到此处上传</div>
              <div class="upload-hint">支持 .xlsx / .csv，单个文件不超过 10MB</div>
            </el-upload>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="错误策略" prop="errorStrategy">
            <el-select v-model="form.errorStrategy" style="width: 100%">
              <el-option v-for="item in ERROR_STRATEGY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="重复策略" prop="duplicateStrategy">
            <el-select v-model="form.duplicateStrategy" style="width: 100%">
              <el-option v-for="item in DUPLICATE_STRATEGY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交后依次执行文件级预检、行级DQ、批次内去重和存量匹配。"
    />
  </div>
</template>

<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue';
import type { FormInstance } from 'element-plus';
import { reactive, ref } from 'vue';
import { createImportJob } from '@/api/demo/cmdPoc';
import { ERROR_STRATEGY_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocBatchUploadDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const SCENE_OPTIONS = ['Door · Mainstream · Lens', 'Door · High End · Frame', 'Payer · High End · Lens'];
const DUPLICATE_STRATEGY_OPTIONS = ['Exact自动关联，Suspect进入治理', '全部进入人工复核', '拒绝重复行'];

const formRef = ref<FormInstance>();
const fileName = ref('');

const form = reactive({
  context: 'Door · Mainstream · Lens',
  templateVersion: 'v1.3 Published',
  errorStrategy: '部分成功，异常行独立处理',
  duplicateStrategy: 'Exact自动关联，Suspect进入治理'
});

const onFileChange = (_file: unknown, files: unknown[]) => {
  const list = files as { name: string }[];
  fileName.value = list[list.length - 1]?.name ?? '';
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  return createImportJob(fileName.value || 'uploaded_file.xlsx');
};

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.upload-zone {
  width: 100%;

  :deep(.el-upload-dragger) {
    padding: 20px;
  }
}

.upload-ico {
  font-size: 28px;
  color: var(--g-text2);
}

.upload-text {
  font-size: 13px;
  color: var(--g-text);
  margin-top: 6px;
}

.upload-hint {
  font-size: 12px;
  color: var(--g-text2);
  margin-top: 4px;
}
</style>
