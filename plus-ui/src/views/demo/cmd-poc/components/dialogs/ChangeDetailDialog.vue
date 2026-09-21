<template>
  <div class="poc-dialog-body">
    <div v-loading="loading">
      <!-- 概要 -->
      <el-descriptions :column="2" border size="small" class="m-b-12">
        <el-descriptions-item label="申请编号">{{ detail.requestId }}</el-descriptions-item>
        <el-descriptions-item label="One ID">{{ detail.oneId }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="所属 BU">{{ detail.bu || '—' }}</el-descriptions-item>
        <el-descriptions-item label="变更类型">
          <el-tag :type="detail.changeType === 'Deactivate' ? 'danger' : 'primary'" size="small">
            {{ detail.changeType === 'Deactivate' ? '逻辑停用' : '属性变更' }}
          </el-tag>
          <el-tag v-if="detail.isKeyChange" type="danger" size="small" style="margin-left: 6px">关键属性</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="(CHANGE_STATUS_MAP[detail.status]?.type as any)" size="small">
            {{ CHANGE_STATUS_MAP[detail.status]?.label || detail.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联影响检查">
          <el-tag :type="(CHANGE_RELATION_MAP[detail.relationCheck]?.type as any)" size="small">
            {{ CHANGE_RELATION_MAP[detail.relationCheck]?.label || '—' }}
          </el-tag>
          <span class="text-gray m-l-8">{{ detail.relationMsg || '' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="计划 / 实际生效">
          {{ detail.effectiveDate || '—' }}
          <span v-if="detail.effectiveTime" class="text-gray">（实际 {{ detail.effectiveTime }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="关联审批待办">{{ detail.approvalTaskNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.submittedAt || '—' }}</el-descriptions-item>
        <el-descriptions-item label="变更说明" :span="2">{{ detail.reason || '—' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 影响面 -->
      <div v-if="detail.impacts?.length" class="section-title">关联关系影响</div>
      <ul v-if="detail.impacts?.length" class="impact-list">
        <li v-for="(im, i) in detail.impacts" :key="i">{{ im }}</li>
      </ul>

      <!-- Before / After -->
      <div class="section-title m-t-16">Before / After 字段级差异</div>
      <el-table border :data="detail.diffs" class="data-table" empty-text="无字段级差异">
        <el-table-column label="字段" prop="field" min-width="150" />
        <el-table-column label="变化" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.changeFlag === '未变' ? 'info' : 'warning'">{{ row.changeFlag || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Before" min-width="180">
          <template #default="{ row }"><span class="diff-old">{{ row.before }}</span></template>
        </el-table-column>
        <el-table-column label="After" min-width="180">
          <template #default="{ row }"><span class="diff-new">{{ row.after }}</span></template>
        </el-table-column>
        <el-table-column label="属性" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isKey" type="danger" size="small">关键</el-tag>
            <el-tag v-if="row.sensitive" type="warning" size="small">敏感</el-tag>
            <span v-if="!row.isKey && !row.sensitive" class="text-gray">—</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 审批轨迹 -->
      <div class="section-title m-t-16">审批轨迹</div>
      <el-table border :data="detail.trail" class="data-table" empty-text="暂无审批轨迹">
        <el-table-column label="时间" prop="time" width="140" align="center" />
        <el-table-column label="节点" prop="node" width="120" align="center" />
        <el-table-column label="角色" prop="role" width="150" align="center" />
        <el-table-column label="动作 / 意见" prop="action" min-width="200" />
        <el-table-column label="结果" prop="result" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.result === 'Approved' ? 'success' : row.result === 'Rejected' ? 'danger' : 'info'"
              size="small"
            >{{ row.result }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 版本上下文：证明 One ID 不变 -->
      <div class="section-title m-t-16">
        主档版本上下文
        <span class="text-gray">（One ID {{ detail.oneId }} 不变，仅版本递增）</span>
      </div>
      <el-table border :data="detail.versions" class="data-table" empty-text="暂无版本记录">
        <el-table-column prop="versionNo" label="版本" width="80" align="center" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="(CHANGE_VERSION_TYPE_MAP[row.changeType]?.type as any)" size="small">
              {{ CHANGE_VERSION_TYPE_MAP[row.changeType]?.label || row.changeType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeReason" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="changedFields" label="变更字段" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="sourceSystem" label="来源" width="100" />
        <el-table-column prop="createTime" label="时间" width="150" />
      </el-table>

      <!-- 内联操作 -->
      <div class="detail-actions m-t-16">
        <el-button
          v-if="detail.rawStatus === 'APPROVED'"
          type="success"
          :loading="acting"
          @click="onEffect"
        >生效（写入新版本，One ID 不变）</el-button>
        <el-button
          v-if="detail.rawStatus === 'DRAFT' || detail.rawStatus === 'PENDING'"
          type="danger"
          :loading="acting"
          @click="onCancel"
        >撤回申请</el-button>
        <span v-if="detail.rawStatus === 'EFFECTIVE'" class="text-gray">该申请已生效</span>
        <span v-if="detail.rawStatus === 'REJECTED'" class="text-gray">该申请已被拒绝</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { cancelChangeRequest, effectChangeRequest, getChangeDetail } from '@/api/demo/cmdPoc';
import type { ChangeDetailVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { CHANGE_RELATION_MAP, CHANGE_STATUS_MAP, CHANGE_VERSION_TYPE_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocChangeDetailDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const ctx = useCmdPoc();
const loading = ref(false);
const acting = ref(false);
const detail = ref<ChangeDetailVO>({
  requestId: '',
  oneId: '',
  customerName: '',
  changeType: 'Update',
  targetStatus: '',
  isKeyChange: false,
  bu: '',
  reason: '',
  status: 'Draft',
  rawStatus: 'DRAFT',
  effectiveDate: '',
  effectiveTime: '',
  relationCheck: '',
  relationMsg: '',
  impacts: [],
  approvalTaskNo: '',
  submittedAt: '',
  approvedByName: '',
  approvedTime: '',
  diffs: [],
  trail: [],
  versions: [],
  remark: ''
});

async function load() {
  loading.value = true;
  try {
    detail.value = await getChangeDetail((props.payload?.requestId as string) ?? '');
  } catch (e) {
    ElMessage.error((e as Error).message || '加载详情失败');
  } finally {
    loading.value = false;
  }
}

async function onEffect() {
  try {
    await ElMessageBox.confirm('确认生效？将写入主档新版本，One ID 保持不变。', '生效确认', { type: 'warning' });
  } catch {
    return;
  }
  acting.value = true;
  try {
    const msg = await effectChangeRequest(detail.value.requestId);
    ElMessage.success(msg);
    ctx.markChangeChanged();
    ctx.refreshBadge();
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '生效失败');
  } finally {
    acting.value = false;
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认撤回该申请？关联审批待办将同步取消。', '撤回确认', { type: 'warning' });
  } catch {
    return;
  }
  acting.value = true;
  try {
    const msg = await cancelChangeRequest(detail.value.requestId);
    ElMessage.success(msg);
    ctx.markChangeChanged();
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '撤回失败');
  } finally {
    acting.value = false;
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
.impact-list {
  margin: 0 0 4px;
  padding-left: 18px;
  color: var(--el-text-color-regular);

  li {
    margin-bottom: 4px;
    font-size: 13px;
  }
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
