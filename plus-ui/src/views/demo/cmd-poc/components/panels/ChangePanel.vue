<template>
  <section class="page">
    <!-- 指标卡：4 张（待审批变更 / 待审批停用 / 本月已生效 / One ID 重生成） -->
    <div class="stat-grid">
      <el-card
        v-for="kpi in kpis"
        :key="kpi.label"
        class="stat-card"
        shadow="never"
        :body-style="{ padding: '18px 20px' }"
      >
        <div class="stat-top"></div>
        <div class="stat-label">{{ kpi.label }}</div>
        <div class="stat-value" :class="{ 'stat-zero': kpi.value === 0 }">{{ kpi.value }}</div>
        <div class="stat-foot">{{ kpi.hint }}</div>
      </el-card>
    </div>

    <!-- 操作条：发起属性变更 / 申请逻辑停用 -->
    <div class="toolbar m-b-12">
      <el-button type="primary" @click="openChangeRequest">发起属性变更</el-button>
      <el-button type="warning" plain @click="openDeactivate">申请逻辑停用</el-button>
      <div class="toolbar-right text-gray">
        One ID 稳定：变更只递增版本，不重新生成
      </div>
    </div>

    <el-tabs v-model="activeTab" class="m-t-12">
      <!-- ============ 变更申请 ============ -->
      <el-tab-pane label="变更申请" name="change">
        <div class="filter-bar">
          <el-input
            v-model="changeState.keyword"
            placeholder="申请编号 / One ID / 客户名称"
            clearable
            style="width: 240px"
            @keyup.enter="fetchChange"
            @clear="fetchChange"
          />
          <el-select v-model="changeState.status" placeholder="状态" clearable style="width: 150px" @change="fetchChange">
            <el-option v-for="s in CHANGE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" @click="fetchChange">查询</el-button>
          <el-button @click="resetChange">重置</el-button>
        </div>

        <el-table v-loading="changeState.loading" :data="changeState.rows" class="data-table" border stripe>
          <el-table-column prop="requestId" label="申请编号" width="160" />
          <el-table-column prop="oneId" label="One ID" width="120" />
          <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="bu" label="BU" width="110" />
          <el-table-column label="关键属性" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isKeyChange === 'Y'" type="danger" size="small">关键</el-tag>
              <span v-else class="text-gray">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="变更摘要" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="(CHANGE_STATUS_MAP[row.status]?.type as any)" size="small">
                {{ CHANGE_STATUS_MAP[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="150" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(toChangeRow(row))">详情</el-button>
              <el-button
                v-if="row.rawStatus === 'APPROVED'"
                link
                type="success"
                @click="effect(toChangeRow(row))"
              >生效</el-button>
              <el-button
                v-if="row.rawStatus === 'DRAFT' || row.rawStatus === 'PENDING'"
                link
                type="danger"
                @click="cancel(toChangeRow(row))"
              >撤回</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="m-t-12"
          layout="total, prev, pager, next"
          :total="changeState.total"
          :current-page="changeState.pageNum"
          :page-size="changeState.pageSize"
          @current-change="(p: number) => { changeState.pageNum = p; fetchChange(); }"
        />
      </el-tab-pane>

      <!-- ============ 停用申请 ============ -->
      <el-tab-pane label="停用申请" name="deactivate">
        <div class="filter-bar">
          <el-input
            v-model="deactState.keyword"
            placeholder="申请编号 / One ID / 客户名称"
            clearable
            style="width: 240px"
            @keyup.enter="fetchDeactivate"
            @clear="fetchDeactivate"
          />
          <el-select v-model="deactState.status" placeholder="状态" clearable style="width: 150px" @change="fetchDeactivate">
            <el-option v-for="s in CHANGE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" @click="fetchDeactivate">查询</el-button>
          <el-button @click="resetDeactivate">重置</el-button>
        </div>

        <el-table v-loading="deactState.loading" :data="deactState.rows" class="data-table" border stripe>
          <el-table-column prop="requestId" label="申请编号" width="160" />
          <el-table-column prop="oneId" label="One ID" width="120" />
          <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="targetStatus" label="目标状态" width="110">
            <template #default="{ row }">{{ row.targetStatus === 'archived' ? 'Archived' : 'Inactive' }}</template>
          </el-table-column>
          <el-table-column prop="content" label="停用原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="关联影响" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="(CHANGE_RELATION_MAP[row.relationCheck]?.type as any)" size="small">
                {{ CHANGE_RELATION_MAP[row.relationCheck]?.label || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="(CHANGE_STATUS_MAP[row.status]?.type as any)" size="small">
                {{ CHANGE_STATUS_MAP[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="150" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(toChangeRow(row))">详情</el-button>
              <el-button link type="info" @click="openDeactivateResult(toChangeRow(row))">停用结果</el-button>
              <el-button
                v-if="row.rawStatus === 'DRAFT' || row.rawStatus === 'PENDING'"
                link
                type="danger"
                @click="cancel(toChangeRow(row))"
              >撤回</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="m-t-12"
          layout="total, prev, pager, next"
          :total="deactState.total"
          :current-page="deactState.pageNum"
          :page-size="deactState.pageSize"
          @current-change="(p: number) => { deactState.pageNum = p; fetchDeactivate(); }"
        />
      </el-tab-pane>

      <!-- ============ 版本历史 ============ -->
      <el-tab-pane label="版本历史" name="version">
        <div class="filter-bar">
          <span class="text-gray">选择客户（One ID 不变，版本递增）：</span>
          <el-select
            v-model="versionOneId"
            filterable
            placeholder="选择客户查看版本链"
            style="width: 320px"
            @change="fetchVersions"
          >
            <el-option
              v-for="c in customers"
              :key="c.oneId"
              :label="`${c.legalName}（${c.oneId}）`"
              :value="c.oneId"
            />
          </el-select>
          <span v-if="versionOneId" class="version-stable">
            One ID：<b>{{ versionOneId }}</b> · 共 {{ versions.length }} 个版本
          </span>
        </div>

        <el-table v-loading="versionLoading" :data="versions" class="data-table" border stripe>
          <el-table-column prop="versionNo" label="版本号" width="90" align="center" />
          <el-table-column label="变更类型" width="120">
            <template #default="{ row }">
              <el-tag :type="(CHANGE_VERSION_TYPE_MAP[row.changeType]?.type as any)" size="small">
                {{ CHANGE_VERSION_TYPE_MAP[row.changeType]?.label || row.changeType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="changeReason" label="变更说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="changedFields" label="变更字段" min-width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="sourceSystem" label="来源系统" width="110" />
          <el-table-column prop="dqScore" label="DQ" width="80" align="center" />
          <el-table-column prop="createTime" label="生成时间" width="150" />
        </el-table>
        <div v-if="!versionOneId" class="poc-note m-t-12">
          选择一个客户以查看其完整版本链 —— 直观证明「换版本不换 One ID」：无论属性如何变更、是否被停用，
          主数据始终以 One ID 为锚点写入新版本，绝不会重新生成编号。
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as cmdPocApi from '@/api/demo/cmdPoc';
import type { ChangeRequestVO, ChangeVersionVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import {
  CHANGE_RELATION_MAP,
  CHANGE_STATUS_MAP,
  CHANGE_STATUS_OPTIONS,
  CHANGE_VERSION_TYPE_MAP
} from '../../constants/options';

defineOptions({ name: 'CmdPocChangePanel' });

const ctx = useCmdPoc();
const customers = computed(() => ctx.customers.value);

const activeTab = ref<'change' | 'deactivate' | 'version'>('change');
const kpis = ref<Array<{ label: string; value: number; hint: string }>>([]);

/** 变更申请列表状态 */
const changeState = reactive({
  loading: false,
  rows: [] as ChangeRequestVO[],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: ''
});

/** 停用申请列表状态 */
const deactState = reactive({
  loading: false,
  rows: [] as ChangeRequestVO[],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: ''
});

const versionOneId = ref('');
const versions = ref<ChangeVersionVO[]>([]);
const versionLoading = ref(false);

async function loadKpi() {
  try {
    kpis.value = await cmdPocApi.getChangeKpi();
  } catch {
    kpis.value = [];
  }
}

async function fetchChange() {
  changeState.loading = true;
  try {
    const page = await cmdPocApi.listChangeRequests({
      pageNum: changeState.pageNum,
      pageSize: changeState.pageSize,
      keyword: changeState.keyword || undefined,
      changeType: 'Update',
      status: (changeState.status || undefined) as ChangeRequestVO['status'] | ''
    });
    changeState.rows = page.rows ?? [];
    changeState.total = page.total ?? 0;
  } catch {
    changeState.rows = [];
    changeState.total = 0;
  } finally {
    changeState.loading = false;
  }
}

function resetChange() {
  changeState.keyword = '';
  changeState.status = '';
  changeState.pageNum = 1;
  fetchChange();
}

async function fetchDeactivate() {
  deactState.loading = true;
  try {
    const page = await cmdPocApi.listChangeRequests({
      pageNum: deactState.pageNum,
      pageSize: deactState.pageSize,
      keyword: deactState.keyword || undefined,
      changeType: 'Deactivate',
      status: (deactState.status || undefined) as ChangeRequestVO['status'] | ''
    });
    deactState.rows = page.rows ?? [];
    deactState.total = page.total ?? 0;
  } catch {
    deactState.rows = [];
    deactState.total = 0;
  } finally {
    deactState.loading = false;
  }
}

function resetDeactivate() {
  deactState.keyword = '';
  deactState.status = '';
  deactState.pageNum = 1;
  fetchDeactivate();
}

async function fetchVersions() {
  if (!versionOneId.value) {
    versions.value = [];
    return;
  }
  versionLoading.value = true;
  try {
    versions.value = await cmdPocApi.getChangeVersions(versionOneId.value);
  } catch {
    versions.value = [];
  } finally {
    versionLoading.value = false;
  }
}

/** 当前页签数据重新拉取（提交/生效/撤回后调用） */
function refetchActive() {
  if (activeTab.value === 'change') fetchChange();
  else if (activeTab.value === 'deactivate') fetchDeactivate();
  else if (activeTab.value === 'version' && versionOneId.value) fetchVersions();
  loadKpi();
}

/* ---------------- 操作入口 ---------------- */
function openChangeRequest() {
  ctx.openDialog('changeRequest');
}

function openDeactivate() {
  ctx.openDialog('deactivate');
}

/**
 * el-table 作用域插槽解构出来的 `row` 是框架内置的 DefaultRow（宽索引类型），
 * 与业务 VO 的必填字段对不上。统一在这里收口转换，模板里一律走 toChangeRow(row)。
 */
function toChangeRow(row: unknown): ChangeRequestVO {
  return row as ChangeRequestVO;
}

function openDetail(row: ChangeRequestVO) {
  ctx.openDialog('changeDetail', { requestId: row.requestId });
}

function openDeactivateResult(row: ChangeRequestVO) {
  ctx.openDialog('deactivateResult', { oneId: row.oneId });
}

async function effect(row: ChangeRequestVO) {
  try {
    await ElMessageBox.confirm(
      `确认对 ${row.customerName}（${row.oneId}）的变更申请生效？生效将写入主档新版本，One ID 保持不变。`,
      '生效确认',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  try {
    const msg = await cmdPocApi.effectChangeRequest(row.requestId);
    ElMessage.success(msg);
    ctx.markChangeChanged();
    ctx.refreshBadge();
    refetchActive();
  } catch (e) {
    ElMessage.error((e as Error).message || '生效失败');
  }
}

async function cancel(row: ChangeRequestVO) {
  try {
    await ElMessageBox.confirm(`确认撤回申请 ${row.requestId}？关联审批待办将同步取消。`, '撤回确认', { type: 'warning' });
  } catch {
    return;
  }
  try {
    const msg = await cmdPocApi.cancelChangeRequest(row.requestId);
    ElMessage.success(msg);
    ctx.markChangeChanged();
    refetchActive();
  } catch (e) {
    ElMessage.error((e as Error).message || '撤回失败');
  }
}

/* ---------------- 生命周期 ---------------- */
watch(activeTab, tab => {
  if (tab === 'change' && changeState.rows.length === 0) fetchChange();
  if (tab === 'deactivate' && deactState.rows.length === 0) fetchDeactivate();
  if (tab === 'version' && versionOneId.value) fetchVersions();
});

/** 提交 / 生效 / 撤回成功后，由 composable 的 changeVersion 信号驱动刷新 */
watch(
  () => ctx.changeVersion.value,
  () => refetchActive()
);

onMounted(async () => {
  if (customers.value.length === 0) {
    try {
      await ctx.loadCustomers();
    } catch {
      /* 忽略，版本历史页签仅在用户选择时依赖客户列表 */
    }
  }
  await loadKpi();
  await fetchChange();
});
</script>
