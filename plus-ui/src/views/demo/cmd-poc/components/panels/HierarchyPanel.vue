<template>
  <section class="page">
    <!-- 权限提示 -->
    <el-alert v-if="readOnly" type="info" :closable="false" show-icon class="poc-note m-b-12">
      <template #title>
        <b>Auditor 只读模式：</b>可搜索、定位并查看授权范围内的层级及 Payer，不显示新增或编辑操作。
      </template>
    </el-alert>
    <el-alert type="info" :closable="false" show-icon class="poc-note m-b-12">
      <template #title>
        <b>当前权限：{{ scopeLabel }}</b>
        <span class="note-sep">·</span>
        <span>{{ scopeHint }}</span>
      </template>
    </el-alert>

    <!-- Tab 分页：层级浏览（查询） + 待归位主数据（操作） -->
    <el-tabs v-model="activeTab" class="hier-tabs">
      <el-tab-pane name="browse" label="层级浏览">
        <el-alert type="success" :closable="false" show-icon class="poc-note m-b-12">
          <template #title>
            <b>主数据 ↔ 客户层级：</b>
            客户<strong>审批通过</strong>即成为主数据（「客户管理」可见），并自动登记为「<strong>待归位</strong>」节点（当前 {{ unassigned.length }} 个）；
            由 Data Steward <strong>归位</strong>到 A3-A2-A1 后，才进入中间层级树（当前 {{ treeNodeCount }} 个节点）。
            两部分合起来才是主数据的完整视图。
          </template>
        </el-alert>

        <!-- 三栏布局：搜索 + 树 + 详情 -->
        <el-card class="page-card hierarchy-layout" shadow="never" :body-style="{ padding: '0', height: '100%' }">
          <!-- 左：搜索与导航 -->
          <aside class="hier-left">
            <div class="hier-panel-head">搜索与导航</div>
            <div class="hier-panel-body">
              <div class="hier-search">
                <el-input
                  v-model="searchKeyword"
                  placeholder="输入客户名称 / One ID"
                  clearable
                  @keyup.enter="onSearch"
                  @clear="onSearch"
                />
                <el-button type="primary" icon="Search" :loading="searching" @click="onSearch">搜索</el-button>
              </div>
              <div class="hier-filter">
                <el-select v-model="filters.hierarchyType" placeholder="层级类型" @change="onFilterChange">
                  <el-option v-for="item in HIER_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-select v-model="filters.level" placeholder="全部层级" @change="onFilterChange">
                  <el-option v-for="item in HIER_LEVEL_OPTIONS" :key="item" :label="item" :value="item" />
                </el-select>
                <el-select v-model="filters.bu" placeholder="BU" @change="onFilterChange">
                  <el-option v-for="item in buOptions" :key="item" :label="item" :value="item" />
                </el-select>
                <el-select v-model="filters.status" placeholder="状态" @change="onFilterChange">
                  <el-option v-for="item in HIER_STATUS_OPTIONS" :key="item" :label="item" :value="item" />
                </el-select>
                <el-button size="small" plain class="hier-filter-reset" icon="RefreshLeft" @click="resetFilters">
                  重置条件
                </el-button>
              </div>

              <div v-loading="searching" class="hier-results">
                <div class="hier-results-tip">{{ leftTip }}</div>
                <div
                  v-for="node in leftList"
                  :key="node.id"
                  :class="['hier-result', { on: currentNode?.id === node.id }]"
                  @click="locateNode(node.id)"
                >
                  <b>{{ node.name }}</b>
                  <small>{{ node.oneId }} · {{ node.level }} · {{ node.status }}</small>
                  <small class="hier-path">{{ node.path }}</small>
                </div>
                <div v-if="resultTotal > searchResultLimit" class="hier-results-tip hier-cap-tip">
                  仅显示前 {{ searchResultLimit }} 条，请输入更精确的关键字缩小范围
                </div>
                <el-empty
                  v-if="!leftList.length && !searching"
                  description="没有符合条件的节点，试试放宽筛选或清空关键字"
                  :image-size="60"
                />
              </div>
            </div>
          </aside>

          <!-- 中：Legal Hierarchy 树 -->
          <main class="hier-center">
            <div class="hier-panel-head">
              <span>Legal Hierarchy</span>
              <div class="hier-center-tools">
                <el-button text size="small" icon="Back" :loading="locating === 'root'" @click="locateRoot()">
                  返回根节点
                </el-button>
                <el-button text size="small" icon="Location" :loading="locating === 'current'" @click="locateNode(currentNode?.id)">
                  定位当前节点
                </el-button>
                <el-button text size="small" icon="Fold" :loading="locating === 'collapse'" @click="collapseOthers()">
                  收起其他分支
                </el-button>
              </div>
            </div>
            <div ref="treeBodyRef" class="hier-panel-body hier-tree-body">
              <div class="hier-bread">{{ currentNode?.path ?? '-' }}</div>
              <el-tree
                ref="treeRef"
                :data="treeData"
                :props="treeProps"
                node-key="id"
                :default-expanded-keys="expandedKeys"
                highlight-current
                :current-node-key="currentNode?.id"
                class="hier-tree"
                @node-click="handleNodeClick"
              >
                <template #default="{ data }">
                  <!-- 「展开全部子节点」占位行：全量树已一次性取回，点击纯前端展开剩余子节点 -->
                  <div v-if="data.isLoadMore" class="hier-load-more" @click.stop="onLoadMore(data)">
                    <el-icon><Plus /></el-icon>
                    <span>展开全部子节点 · 还有 {{ data.loadMoreTotal - data.loadMoreShown }} 个未显示</span>
                  </div>
                  <div v-else :class="['hier-node', `level-${data.level?.toLowerCase()}`]" :data-node-id="data.id">
                    <div class="hier-node-main">
                      <span class="hier-node-title">[{{ data.level }}] {{ data.name }}</span>
                      <span class="hier-node-sub">{{ data.oneId }}</span>
                    </div>
                    <el-tag v-if="data.level === 'A1' && data.payerId" size="small" type="success" effect="plain">Payer {{ data.payerId }}</el-tag>
                    <el-tag size="small" type="info" effect="plain" class="hier-status">{{ data.status }}</el-tag>
                  </div>
                </template>
              </el-tree>
              <div class="hier-lazy-tip">
                <el-alert type="info" :closable="false" show-icon class="poc-note">
                  <template #title>
                    层级数据全量加载；每层默认展示前 {{ CHILD_PAGE_SIZE }} 个子节点，点击「展开全部子节点」一键展开剩余节点。
                  </template>
                </el-alert>
              </div>
            </div>
          </main>

          <!-- 右：节点详情与操作 -->
          <aside class="hier-right">
            <div class="hier-panel-head">节点详情与操作</div>
            <div class="hier-panel-body">
              <template v-if="currentNode">
                <div class="hier-detail-head">
                  <el-tag size="small" effect="dark" :type="levelTagType(currentNode.level)">{{ currentNode.level }}</el-tag>
                  <h3>{{ currentNode.name }}</h3>
                  <div class="hier-detail-id">{{ currentNode.oneId }}</div>
                </div>
                <div v-if="canManage" class="hier-detail-actions">
                  <el-button plain icon="Edit" @click="openRelation('edit')">编辑关系</el-button>
                  <el-button type="primary" plain icon="Plus" @click="openRelation('child')">增加子节点</el-button>
                </div>

                <div class="hier-section-title">节点信息</div>
                <div class="hier-kv">
                  <div class="hier-kv-row">
                    <span>节点级别</span>
                    <span>{{ currentNode.level }} · {{ currentNode.type }}</span>
                  </div>
                  <div class="hier-kv-row">
                    <span>BU</span>
                    <span>High End</span>
                  </div>
                  <div class="hier-kv-row">
                    <span>当前父节点</span>
                    <span>{{ currentNode.parentName || currentNode.parent }}</span>
                  </div>
                  <div class="hier-kv-row">
                    <span>直接子节点</span>
                    <span>{{ currentNode.childrenCount }}</span>
                  </div>
                  <div class="hier-kv-row">
                    <span>全部后代</span>
                    <span>{{ currentNode.descendants }}</span>
                  </div>
                  <div class="hier-kv-row">
                    <span>Payer</span>
                    <span>{{ currentNode.payerName }}</span>
                  </div>
                  <div class="hier-kv-row">
                    <span>有效期</span>
                    <span>{{ currentNode.validity }}</span>
                  </div>
                </div>

                <div class="hier-section-title">完整路径</div>
                <div class="hier-full-path">{{ currentNode.path }}</div>
              </template>
              <el-empty v-else description="请在左侧或树中选择节点" />
            </div>
          </aside>
        </el-card>
      </el-tab-pane>

      <!-- Tab 2：待归位主数据（操作任务） -->
      <el-tab-pane name="unassigned" :label="`待归位主数据 (${unassigned.length})`">
        <el-card class="page-card" shadow="never" :body-style="{ padding: '20px' }">
          <template #header>
            <div class="unassigned-header">
              <span class="card-title">待归位主数据</span>
              <el-input
                v-model="unassignedKeyword"
                size="small"
                placeholder="筛选 One ID / 客户名称"
                clearable
                style="width: 240px; margin-left: auto"
              />
            </div>
          </template>

          <el-alert type="info" :closable="false" show-icon class="poc-note m-b-12">
            <template #title>
              客户<strong>审批通过</strong>成为主数据后会自动出现在这里，由 Data Steward <strong>归位</strong>到 A3-A2-A1 后进入中间层级树。
            </template>
          </el-alert>

          <el-table v-loading="loadingUnassigned" :data="filteredUnassigned" border class="data-table">
            <el-table-column label="客户名称" prop="name" min-width="200" show-overflow-tooltip />
            <el-table-column label="One ID" prop="oneId" width="180" />
            <el-table-column label="BU" prop="bu" width="120" />
            <el-table-column label="建议层级" prop="suggestedLevel" width="100" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.registered ? 'warning' : 'info'" effect="plain">
                  {{ row.registered ? '已登记待归位' : '未登记节点' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审批通过时间" prop="approvedTime" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="canManage" type="primary" size="small" plain @click="onAssign(row as HierarchyUnassignedVO)">归位</el-button>
                <span v-else class="text-gray">—</span>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!filteredUnassigned.length && !loadingUnassigned" description="暂无待归位主数据" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, type ElTree } from 'element-plus';
import {
  getHierarchy,
  getHierarchyNode,
  getHierarchyRoots,
  getUnassignedNodes,
  searchHierarchy
} from '@/api/demo/cmdPoc';
import type { HierarchyNodeVO, HierarchySearchFilters, HierarchyUnassignedVO } from '@/api/demo/cmdPoc/types';
import {
  HIER_BU_OPTIONS,
  HIER_LEVEL_OPTIONS,
  HIER_STATUS_OPTIONS,
  HIER_TYPE_OPTIONS
} from '../../constants/options';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocHierarchyPanel' });

const { roleKey, readOnly, openDialog, hierarchyVersion, markHierarchyChanged } = useCmdPoc();

/** Tab 切换：层级浏览（查询） / 待归位主数据（操作） */
const activeTab = ref<'browse' | 'unassigned'>('browse');
const loadingUnassigned = ref(false);

const treeRef = ref<InstanceType<typeof ElTree>>();
/** 树容器（滚动定位时在其内部按 data-node-id 查找节点） */
const treeBodyRef = ref<HTMLElement>();
/** 当前正在执行的定位动作，用于按钮 loading 态 */
const locating = ref<'' | 'root' | 'current' | 'collapse'>('');
/**
 * 原始层级树（后端一次性取回，永不改写，作为展示树的唯一数据源）。
 * 展示层的截断 / 展开全部由 treeData 计算属性派生，绝不回写到这里，
 * 否则 slice 会把未展示的子节点永久丢掉（历史 bug：点了「加载更多」却展开不出东西）。
 */
const rawTree = ref<HierarchyNodeVO[]>([]);
const treeProps = { label: 'label', children: 'children' };

/** 每个节点默认展示的直接子节点数（对齐原型「加载更多子节点 · 已显示 X / Y」的每批 3 个） */
const CHILD_PAGE_SIZE = 3;
/** 左侧结果区最多展示条数（超过提示细化关键字） */
const searchResultLimit = 20;

/**
 * 已点击「展开全部子节点」的父节点 id 集合。
 * 全量层级树由后端一次性取回（getHierarchy → buildHierarchyTree），
 * 截断只是展示层行为，展开无需再调后端分页接口。
 */
const showAllChildren = ref(new Set<string>());

/** 生成「展开全部子节点」占位行 */
const makeLoadMore = (parent: HierarchyNodeVO, shown: number, total: number): HierarchyNodeVO => ({
  id: `__more__${parent.id}`,
  level: '',
  type: '',
  label: 'load-more',
  name: '',
  oneId: '',
  payerId: '',
  childrenCount: 0,
  descendants: 0,
  parent: '',
  path: '',
  validity: '',
  status: 'Active',
  isLoadMore: true,
  loadMoreParentId: parent.oneId || parent.id,
  loadMoreShown: shown,
  loadMoreTotal: total,
  children: []
});

/**
 * 由原始树派生「展示树」：每个节点默认只展示前 CHILD_PAGE_SIZE 个子节点，
 * 超出部分追加一个「展开全部子节点」占位行。
 *
 * - 每次都从原始树重新派生，同一父节点下最多只有一个占位行，不会重复或残留；
 * - 总数取「实际子节点数」而非 childrenCount —— childrenCount 是数据库全量
 *   （含 pending / 被筛选排除的行），直接用会导致「还有 N 个未显示」点了却展开不出东西；
 * - 不修改入参，原始树始终完整。
 */
const buildDisplayTree = (nodes: HierarchyNodeVO[], showAll: Set<string>): HierarchyNodeVO[] =>
  nodes
    .filter(node => !node.isLoadMore)
    .map(node => {
      const kids = buildDisplayTree((node.children ?? []).filter(c => !c.isLoadMore), showAll);
      const total = kids.length;
      if (showAll.has(node.id) || total <= CHILD_PAGE_SIZE) {
        return { ...node, children: kids };
      }
      return { ...node, children: [...kids.slice(0, CHILD_PAGE_SIZE), makeLoadMore(node, CHILD_PAGE_SIZE, total)] };
    });

const treeData = computed(() => buildDisplayTree(rawTree.value, showAllChildren.value));

const searchKeyword = ref('');
const searchResults = ref<HierarchyNodeVO[]>([]);
const currentNode = ref<HierarchyNodeVO | null>(null);
const expandedKeys = ref<string[]>([]);
/** 搜索 / 树加载中的 loading 态（下拉切换后立即反馈，避免「点了没反应」） */
const searching = ref(false);

/** 待归位主数据：已批准成为主数据，但尚未挂到 A3-A2-A1 树上 */
const unassigned = ref<HierarchyUnassignedVO[]>([]);
const unassignedKeyword = ref('');

const filteredUnassigned = computed(() => {
  const keyword = unassignedKeyword.value.trim().toLowerCase();
  if (!keyword) return unassigned.value;
  return unassigned.value.filter(
    item => item.name.toLowerCase().includes(keyword) || item.oneId.toLowerCase().includes(keyword)
  );
});

/** 层级树节点总数（已归位部分，不含「加载更多」占位行） */
const treeNodeCount = computed(() => {
  let count = 0;
  const walk = (nodes: HierarchyNodeVO[]) => {
    nodes.forEach(node => {
      if (node.isLoadMore) return;
      count += 1;
      walk(node.children ?? []);
    });
  };
  walk(treeData.value);
  return count;
});

const filters = reactive({
  hierarchyType: '全部类型',
  level: '全部层级',
  bu: 'High End',
  status: 'Active'
});

/** 下拉条件 → 后端查询参数（「全部 *」= 不过滤） */
const currentFilters = computed<HierarchySearchFilters>(() => ({
  hierarchyType: filters.hierarchyType,
  level: filters.level,
  buScope: filters.bu,
  status: filters.status
}));

/** 当前是否有生效的搜索 / 筛选条件（决定左侧结果区是「结果模式」还是「根节点入口模式」） */
const defaultBu = computed(() => (roleKey.value === 'gc' ? 'All Authorized BU' : 'High End'));
const hasActiveCondition = computed(
  () =>
    searchKeyword.value.trim() !== '' ||
    filters.hierarchyType !== '全部类型' ||
    filters.level !== '全部层级' ||
    filters.bu !== defaultBu.value ||
    filters.status !== 'Active'
);

/** 结果总数与截断后的展示列表（避免大结果集把左侧导航撑爆） */
const resultTotal = computed(() => searchResults.value.length);
const displayResults = computed(() => searchResults.value.slice(0, searchResultLimit));

/** 左侧列表数据：有搜索 / 筛选条件 → 匹配结果；否则 → 根节点快速入口（不与中间树重复铺全量数据） */
const leftList = computed(() => (hasActiveCondition.value ? displayResults.value : treeData.value));

const leftTip = computed(() => {
  if (!hasActiveCondition.value) {
    return '输入客户名称 / One ID 搜索定位；当前展示根节点快速入口，完整结构见中间层级树';
  }
  const suffix = searchKeyword.value.trim() ? '' : '（已按筛选条件过滤）';
  return `找到 ${resultTotal.value} 个授权范围内结果${suffix}`;
});

/** 恢复默认筛选并重新查询 */
const resetFilters = () => {
  filters.hierarchyType = '全部类型';
  filters.level = '全部层级';
  filters.status = 'Active';
  filters.bu = roleKey.value === 'gc' ? 'All Authorized BU' : 'High End';
  searchKeyword.value = '';
  void refreshTreeAndSearch();
};

const canManage = computed(() => roleKey.value === 'bu' || roleKey.value === 'gc');

const scopeLabel = computed(() => {
  if (roleKey.value === 'gc') return 'GC Scope · Cross-BU';
  if (roleKey.value === 'bu') return 'BU Scope · High End';
  if (roleKey.value === 'audit') return 'Authorized · Read Only';
  return 'High End · Frame';
});

const scopeHint = computed(() => {
  if (roleKey.value === 'business') return '可查看授权层级并发起申请；提交后由 BU Data Steward 审核。';
  if (roleKey.value === 'bu') return '可维护本 BU 关系并审核业务申请；跨 BU 关系升级至 GC Scope。';
  if (roleKey.value === 'gc') return '可处理跨 BU、多父冲突及重大 A2/A3 关系。';
  return '仅只读查看。';
});

const buOptions = computed(() => {
  if (roleKey.value === 'gc') return ['All Authorized BU'];
  return HIER_BU_OPTIONS;
});

const levelTagType = (level: string) => (level === 'A3' ? 'primary' : level === 'A2' ? 'warning' : 'success');

const loadNode = async (key?: string) => {
  if (!key) return;
  const node = await getHierarchyNode(key);
  if (node) {
    currentNode.value = node;
  }
};

/** 左侧结果区查询：关键字 + 四个下拉条件一起下传到后端 */
const onSearch = async () => {
  searching.value = true;
  try {
    searchResults.value = await searchHierarchy(searchKeyword.value, currentFilters.value);
  } catch {
    ElMessage.error('层级搜索失败，请检查后端服务');
    searchResults.value = [];
  } finally {
    searching.value = false;
  }
};

/**
 * 下拉切换：结果区 + 中间树一起按新条件刷新（树也跟着变，避免「筛了但树没动」）
 */
const onFilterChange = () => {
  void refreshTreeAndSearch();
};

/** 按当前筛选条件重载层级树 + 结果区，并自动定位到第一个节点 */
const refreshTreeAndSearch = async () => {
  searching.value = true;
  try {
    rawTree.value = await getHierarchy(currentFilters.value);
    // 换筛选条件等于换了一棵树，之前「展开全部」的记录一并清空
    showAllChildren.value = new Set();
    const firstLevel = treeData.value.map(n => n.id);
    const secondLevel = treeData.value.flatMap(n => n.children?.map(c => c.id) ?? []);
    expandedKeys.value = [...firstLevel, ...secondLevel];
    searchResults.value = await searchHierarchy(searchKeyword.value, currentFilters.value);
    if (searchResults.value.length) {
      await locateNode(searchResults.value[0].id);
    } else if (treeData.value.length) {
      await locateNode(treeData.value[0].id);
    } else {
      currentNode.value = null;
    }
  } catch {
    ElMessage.error('层级数据加载失败，请检查后端服务');
  } finally {
    searching.value = false;
  }
};

/**
 * 收起全部节点。
 * el-tree 未暴露批量收起 API，这里通过内部 store 的 nodesMap 逐个收起，
 * 全部做可选链防御，取不到时静默降级，不影响页面其它功能。
 */
const collapseAllNodes = () => {
  const store = (treeRef.value as unknown as { store?: { nodesMap?: Record<string, { expanded?: boolean; collapse?: () => void }> } })
    ?.store;
  const nodesMap = store?.nodesMap;
  if (!nodesMap) return;
  Object.values(nodesMap).forEach(node => {
    if (node?.expanded && typeof node.collapse === 'function') {
      node.collapse();
    }
  });
};

/** 滚动到目标节点并做一次高亮闪烁（节点由 data-node-id 定位） */
const scrollToNode = async (id: string) => {
  await nextTick();
  const el = treeBodyRef.value?.querySelector<HTMLElement>(`[data-node-id="${id}"]`);
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.classList.add('is-flash');
  window.setTimeout(() => el.classList.remove('is-flash'), 1400);
};

/** 展开从根到目标节点的整条路径 */
const expandPath = (node: HierarchyNodeVO) => {
  expandedKeys.value = Array.from(new Set([...(node.ancestorIds ?? []), node.id]));
};

/** ◎ 定位当前节点：展开祖先链 → 选中 → 滚动 → 高亮 */
const locateNode = async (key?: string) => {
  if (!key) {
    ElMessage.warning('请先在树或搜索结果中选择一个节点');
    return;
  }
  locating.value = 'current';
  try {
    await loadNode(key);
    const node = currentNode.value;
    if (!node) return;
    expandPath(node);
    await ensureLoaded(node);
    await nextTick();
    treeRef.value?.setCurrentKey(node.id);
    await scrollToNode(node.id);
  } finally {
    locating.value = '';
  }
};

/** ← 返回根节点：实时查库取顶层节点 → 收起其它分支 → 定位并滚动 */
const locateRoot = async () => {
  locating.value = 'root';
  try {
    const buScope = filters.bu === 'All Authorized BU' ? undefined : filters.bu;
    const roots = await getHierarchyRoots(buScope);
    if (!roots.length) {
      ElMessage.warning('当前 BU 范围内没有查询到根节点');
      return;
    }
    // 优先回到当前节点所在的那一棵树，其次回到第一个根节点
    const currentRootId = currentNode.value?.ancestorIds?.[0];
    const target = roots.find(item => item.oneId === currentRootId) ?? roots[0];
    collapseAllNodes();
    await loadNode(target.id);
    const node = currentNode.value;
    if (!node) return;
    expandPath(node);
    await nextTick();
    treeRef.value?.setCurrentKey(node.id);
    await scrollToNode(node.id);
  } finally {
    locating.value = '';
  }
};

const handleNodeClick = (data: HierarchyNodeVO) => {
  if (data.isLoadMore) {
    onLoadMore(data);
    return;
  }
  loadNode(data.id);
};

/** 在当前树中按 id / oneId 查找节点（跳过占位行） */
const findNodeById = (id: string): HierarchyNodeVO | undefined => {
  let hit: HierarchyNodeVO | undefined;
  const walk = (nodes: HierarchyNodeVO[]) => {
    for (const n of nodes) {
      if (n.isLoadMore) continue;
      if (n.id === id || n.oneId === id) {
        hit = n;
        return;
      }
      walk(n.children ?? []);
      if (hit) return;
    }
  };
  walk(treeData.value);
  return hit;
};

/** 「展开全部子节点」：纯前端展开（全量树已一次性取回，无需再调后端分页） */
const onLoadMore = (sentinel: HierarchyNodeVO) => {
  const parent = findNodeById(sentinel.loadMoreParentId ?? '');
  if (!parent) {
    ElMessage.warning('未找到父节点，请刷新后重试');
    return;
  }
  showAllChildren.value = new Set([...showAllChildren.value, parent.id]);
};

/** 定位前确保目标节点可见：解除祖先链上的展示截断（纯前端操作，瞬时完成） */
const ensureLoaded = (node: HierarchyNodeVO) => {
  const need = (node.ancestorIds ?? []).filter(pid => {
    const parent = findNodeById(pid);
    return parent && (parent.children ?? []).some(c => c.isLoadMore);
  });
  if (!need.length) return;
  showAllChildren.value = new Set([...showAllChildren.value, ...need]);
};

/** 三 收起其他分支：仅保留当前节点所在路径，其余全部收起 */
const collapseOthers = async () => {
  const target = currentNode.value?.id;
  if (!target) {
    ElMessage.warning('请先选择要保留的节点');
    return;
  }
  locating.value = 'collapse';
  try {
    collapseAllNodes();
    await locateNode(target);
    ElMessage.success('已收起其他分支，仅保留当前节点路径');
  } finally {
    locating.value = '';
  }
};

/** 展开全部层级（保留原能力，供后续扩展） */
const toggleAll = (collapse: boolean) => {
  if (collapse) {
    collapseAllNodes();
    expandedKeys.value = [];
    return;
  }
  const keys: string[] = [];
  const walk = (nodes: HierarchyNodeVO[]) => {
    nodes.forEach(node => {
      if (node.children?.length) {
        keys.push(node.id);
        walk(node.children);
      }
    });
  };
  walk(treeData.value);
  expandedKeys.value = keys;
};

const openRelation = (mode: 'request' | 'manage' | 'child' | 'edit') => {
  openDialog('hierAdd', { mode, nodeKey: currentNode.value?.id });
};

watch(
  () => roleKey.value,
  () => {
    filters.bu = roleKey.value === 'gc' ? 'All Authorized BU' : 'High End';
  },
  { immediate: true }
);

/** 待归位主数据：已批准成为主数据但尚未归位的客户 */
const loadUnassigned = async () => {
  unassigned.value = await getUnassignedNodes();
};

/** 归位：打开弹窗，把该主数据挂到某个 A3 / A2 之下 */
const onAssign = (item: HierarchyUnassignedVO) => {
  openDialog('hierAssign', {
    oneId: item.oneId,
    name: item.name,
    bu: item.bu,
    suggestedLevel: item.suggestedLevel,
    parentId: currentNode.value?.oneId
  });
};

/** 首次进入：按默认筛选条件拉树 + 结果区 + 待归位列表 */
const loadAll = async () => {
  await refreshTreeAndSearch();
  await loadUnassigned();
};

// 归位成功（或其他入口改了层级）后自动刷新，保证「树」与「待归位」两侧同步
watch(hierarchyVersion, () => {
  loadAll();
});

onMounted(loadAll);
</script>

<style lang="scss" scoped>
.hierarchy-layout {
  display: grid;
  grid-template-columns: 260px minmax(430px, 1fr) 300px;
  height: 620px;
  overflow: hidden;

  :deep(.el-card__body) {
    display: contents;
  }
}

.hier-left,
.hier-center,
.hier-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.hier-left,
.hier-center {
  border-right: 1px solid var(--g-divider);
}

.hier-panel-head {
  height: 44px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  background: #f8fbfd;
  border-bottom: 1px solid var(--g-divider);
  font-size: 13px;
  font-weight: 600;
}

.hier-panel-body {
  flex: 1;
  padding: 12px;
  overflow: auto;
}

.hier-search {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.hier-filter {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 10px;
}

.hier-results-tip {
  font-size: 12px;
  color: var(--g-text2);
  margin-bottom: 8px;
}

.hier-filter-on {
  color: var(--btn-primary);
}

/* 结果集截断提示（左侧导航最多展示 searchResultLimit 条） */
.hier-cap-tip {
  color: var(--btn-warning, #b8860b);
  padding: 6px 8px;
  border: 1px dashed var(--g-divider);
  border-radius: 6px;
  background: #fffdf6;
}

.hier-filter-reset {
  grid-column: 1 / -1;
}

.hier-result {
  border: 1px solid var(--g-divider);
  border-radius: 7px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;

  &:hover,
  &.on {
    border-color: var(--el-color-primary);
    background: #f1f8fc;
  }

  b {
    display: block;
    font-size: 13px;
  }

  small {
    display: block;
    font-size: 12px;
    color: var(--g-text2);
    margin-top: 4px;
    line-height: 1.4;
  }

  .hier-path {
    color: var(--btn-primary);
  }
}

.hier-center-tools {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.hier-tree-body {
  display: flex;
  flex-direction: column;
  padding: 0;
}

.hier-bread {
  position: sticky;
  top: 0;
  background: rgba(255, 255, 255, 0.95);
  border-bottom: 1px solid var(--g-divider);
  padding: 8px 12px;
  font-size: 12px;
  color: #60778a;
  z-index: 2;
}

.hier-tree {
  flex: 1;
  padding: 12px;
  overflow: auto;

  :deep(.el-tree-node__content) {
    height: auto;
    min-height: 44px;
    padding: 6px 0;
  }
}

.hier-node {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--g-divider);
  border-left: 4px solid var(--btn-primary);
  border-radius: 8px;
  background: #fff;

  &.level-a3 {
    border-left-color: #7955a8;
  }
  &.level-a2 {
    border-left-color: var(--btn-warning);
  }
  &.level-a1 {
    border-left-color: var(--btn-success);
  }
}

/* 「定位当前节点 / 返回根节点」命中后的一次性高亮闪烁 */
.hier-node.is-flash {
  animation: hier-node-flash 1.4s ease-in-out;
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px rgb(53 109 255 / 25%);
}

@keyframes hier-node-flash {
  0%,
  100% {
    background: #fff;
  }

  20%,
  60% {
    background: #e8f1ff;
  }
}

.hier-node-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hier-node-title {
  font-size: 13px;
  font-weight: 600;
}

.hier-node-sub {
  font-size: 12px;
  color: var(--g-text2);
}

.hier-status {
  margin-left: auto;
}

/* 「加载更多子节点」占位行（虚线框，居中，可点击） */
.hier-load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin: 4px 0;
  padding: 8px 10px;
  border: 1px dashed var(--el-color-primary-light-5, #a0cfff);
  border-radius: 8px;
  background: #f7fbff;
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, border-color 0.15s;

  &:hover {
    background: #ecf5ff;
    border-color: var(--el-color-primary);
  }

  &.loading {
    opacity: 0.6;
    cursor: wait;
  }
}

.hier-lazy-tip {
  padding: 10px 12px;
  border-top: 1px solid var(--g-divider);
}

.hier-detail-head {
  margin-bottom: 12px;

  h3 {
    margin: 8px 0 4px;
    font-size: 16px;
  }

  .hier-detail-id {
    font-size: 12px;
    color: var(--g-text2);
  }
}

.hier-detail-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.hier-section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--g-text);
  margin: 12px 0 8px;
}

.hier-kv {
  border: 1px solid var(--g-divider);
  border-radius: 8px;
  overflow: hidden;
}

.hier-kv-row {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 12px;
  padding: 8px 10px;
  font-size: 13px;

  &:not(:last-child) {
    border-bottom: 1px solid var(--g-divider);
  }

  span:first-child {
    color: var(--g-text2);
  }
}

.hier-full-path {
  font-size: 12px;
  line-height: 1.6;
  color: var(--btn-primary);
}

.hier-unassigned {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--g-divider);
}

.hier-unassigned-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.hier-unassigned-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--g-text2);
  margin-bottom: 8px;
}

.hier-unassigned-filter {
  margin-bottom: 8px;
}

.hier-unassigned-empty {
  font-size: 12px;
  color: var(--g-text2);
  padding: 10px;
  text-align: center;
  border: 1px dashed var(--g-divider);
  border-radius: 6px;
}

.hier-unassigned-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid #f0d8a8;
  border-left: 3px solid var(--btn-warning);
  border-radius: 7px;
  background: #fffdf6;
}

.hier-unassigned-main {
  flex: 1;
  min-width: 0;

  b {
    display: block;
    font-size: 13px;
  }

  small {
    display: block;
    font-size: 12px;
    color: var(--g-text2);
    margin-top: 3px;
  }
}

.hier-unassigned-meta {
  display: flex !important;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1280px) {
  .hierarchy-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .hier-left,
  .hier-center {
    border-right: none;
    border-bottom: 1px solid var(--g-divider);
  }
}

.hier-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.unassigned-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
