<template>
  <div class="poc-dialog-host">
    <!--
      全局弹窗宿主：所有弹窗收敛到单个 el-dialog 实例，
      通过 key → 组件映射动态渲染，避免页面上堆叠二十余个 dialog。
    -->
    <el-dialog
      v-model="visible"
      :title="displayTitle"
      :width="meta?.width"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      class="poc-dialog"
    >
      <component :is="currentComponent" v-if="currentComponent" ref="bodyRef" :payload="dialog.payload" @close="closeDialog" />

      <template #footer>
        <el-button @click="closeDialog">关闭</el-button>
          <el-button v-if="meta?.confirmable" type="primary" :loading="submitting" @click="onConfirm">
          {{ confirmText ?? '确认' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type Component } from 'vue';
import { ElMessage } from 'element-plus';
import { useCmdPoc } from '../composables/useCmdPoc';
import type { DialogKey } from '../constants/dialogs';
import { DIALOG_MAP } from '../constants/dialogs';

// 弹窗内容组件
import FieldsDialog from './dialogs/FieldsDialog.vue';
import NewFieldDialog from './dialogs/NewFieldDialog.vue';
import NewCustomerDialog from './dialogs/NewCustomerDialog.vue';
import DqSimulateDialog from './dialogs/DqSimulateDialog.vue';
import MatchSimulateDialog from './dialogs/MatchSimulateDialog.vue';
import TemplateDialog from './dialogs/TemplateDialog.vue';
import WorkflowDialog from './dialogs/WorkflowDialog.vue';
import PermissionsDialog from './dialogs/PermissionsDialog.vue';
import SearchDialog from './dialogs/SearchDialog.vue';
import BatchResultDialog from './dialogs/BatchResultDialog.vue';
import BatchUploadDialog from './dialogs/BatchUploadDialog.vue';
import HierarchyAddDialog from './dialogs/HierarchyAddDialog.vue';
import LoopCheckDialog from './dialogs/LoopCheckDialog.vue';
import IntegrationDialog from './dialogs/IntegrationDialog.vue';
import AuditExportDialog from './dialogs/AuditExportDialog.vue';
import OneIdHistoryDialog from './dialogs/OneIdHistoryDialog.vue';
import ChangeRequestDialog from './dialogs/ChangeRequestDialog.vue';
import DeactivateDialog from './dialogs/DeactivateDialog.vue';
import ChangeDetailDialog from './dialogs/ChangeDetailDialog.vue';
import DeactivateResultDialog from './dialogs/DeactivateResultDialog.vue';
import ApprovalHeDialog from './dialogs/ApprovalHeDialog.vue';
import ApprovalMsDialog from './dialogs/ApprovalMsDialog.vue';
import FlowTraceDialog from './dialogs/FlowTraceDialog.vue';
import ReEvaluateDialog from './dialogs/ReEvaluateDialog.vue';
import OcrDialog from './dialogs/OcrDialog.vue';
import FlowGraphDialog from './dialogs/FlowGraphDialog.vue';

defineOptions({ name: 'CmdPocDialogHost' });

/** 弹窗 key → 内容组件 */
const COMPONENT_MAP: Record<DialogKey, Component> = {
  fields: FieldsDialog,
  newFieldForm: NewFieldDialog,
  newCustomer: NewCustomerDialog,
  dq: DqSimulateDialog,
  match: MatchSimulateDialog,
  template: TemplateDialog,
  workflow: WorkflowDialog,
  permissions: PermissionsDialog,
  search: SearchDialog,
  batchResult: BatchResultDialog,
  batchUpload: BatchUploadDialog,
  hierAdd: HierarchyAddDialog,
  loop: LoopCheckDialog,
  integration: IntegrationDialog,
  auditExport: AuditExportDialog,
  oneIdHistory: OneIdHistoryDialog,
  changeRequest: ChangeRequestDialog,
  deactivate: DeactivateDialog,
  changeDetail: ChangeDetailDialog,
  deactivateResult: DeactivateResultDialog,
  approvalHE: ApprovalHeDialog,
  approvalMS: ApprovalMsDialog,
  flowTrace: FlowTraceDialog,
  reEvaluate: ReEvaluateDialog,
  ocr: OcrDialog,
  flowGraph: FlowGraphDialog
};

/** 弹窗内容组件约定：可选暴露 submit()，返回成功提示文案 */
interface DialogBody {
  submit?: () => Promise<string | void>;
}

const { dialog, closeDialog } = useCmdPoc();

const bodyRef = ref<unknown>(null);
const submitting = ref(false);

const visible = computed({
  get: () => !!dialog.current,
  set: (val: boolean) => {
    if (!val) closeDialog();
  }
});

const meta = computed(() => (dialog.current ? DIALOG_MAP[dialog.current] : undefined));
const currentComponent = computed(() => (dialog.current ? COMPONENT_MAP[dialog.current] : undefined));

const payload = computed(() => dialog.payload);

const displayTitle = computed(() => {
  const t = meta.value?.title;
  return typeof t === 'function' ? t(payload.value) : t;
});

const confirmText = computed(() => {
  const t = meta.value?.confirmText;
  return typeof t === 'function' ? t(payload.value) : t;
});

const onConfirm = async () => {
  submitting.value = true;
  try {
    const submit = (bodyRef.value as DialogBody | null)?.submit;
    const message = typeof submit === 'function' ? await submit() : undefined;
    ElMessage.success(message || `${displayTitle.value ?? '操作'}：模拟操作已完成并写入审计日志`);
    closeDialog();
  } catch (error) {
    // 业务校验失败由子组件自行提示，此处仅兜底
    if (error instanceof Error) ElMessage.warning(error.message);
  } finally {
    submitting.value = false;
  }
};
</script>
