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
import BatchResultDialog from './dialogs/BatchResultDialog.vue';
import BatchUploadDialog from './dialogs/BatchUploadDialog.vue';
import HierarchyAddDialog from './dialogs/HierarchyAddDialog.vue';
import HierarchyAssignDialog from './dialogs/HierarchyAssignDialog.vue';
import LoopCheckDialog from './dialogs/LoopCheckDialog.vue';
import IntegrationDialog from './dialogs/IntegrationDialog.vue';
import IntegrationConnDialog from './dialogs/IntegrationConnDialog.vue';
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
import CustomerDetailDialog from './dialogs/CustomerDetailDialog.vue';
import MergeDialog from './dialogs/MergeDialog.vue';

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
  batchResult: BatchResultDialog,
  batchUpload: BatchUploadDialog,
  hierAdd: HierarchyAddDialog,
  hierAssign: HierarchyAssignDialog,
  loop: LoopCheckDialog,
  integration: IntegrationDialog,
  integrationConn: IntegrationConnDialog,
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
  flowGraph: FlowGraphDialog,
  customerDetail: CustomerDetailDialog,
  merge: MergeDialog
};

/** 弹窗内容组件约定：可选暴露 submit()，返回成功提示文案 */
interface DialogBody {
  submit?: () => Promise<string | void>;
  /**
   * 可选：提交成功后是否保持弹窗打开。
   * 模拟测试类弹窗（DQ 规则 / 匹配规则）需要在弹窗内展示模拟结果，不应提交后立即关闭。
   */
  keepOpenAfterSubmit?: boolean;
  /**
   * 可选：提交成功后需要自动切换到的下一个弹窗。
   * 例如全局「OCR识别结果」弹窗点「写回表单」→ 关闭本弹窗并自动打开「新建客户申请」完成预填。
   */
  nextDialog?: DialogKey | '';
}

const { dialog, closeDialog, openDialog } = useCmdPoc();

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
    const body = bodyRef.value as DialogBody | null;
    const submit = body?.submit;
    const message = typeof submit === 'function' ? await submit() : undefined;
    // 子组件要求提交后跳转（如 OCR 写回表单 → 新建客户申请），先取出来再切，避免被 closeDialog 清空
    const next = body?.nextDialog;
    ElMessage.success(message || `${displayTitle.value ?? '操作'}：模拟操作已完成并写入审计日志`);
    // 模拟测试类弹窗（keepOpenAfterSubmit）留在原地展示结果，由用户手动关闭
    if (!body?.keepOpenAfterSubmit) closeDialog();
    if (next) openDialog(next);
  } catch (error) {
    // 业务校验失败由子组件自行提示，此处仅兜底
    if (error instanceof Error) ElMessage.warning(error.message);
  } finally {
    submitting.value = false;
  }
};
</script>
