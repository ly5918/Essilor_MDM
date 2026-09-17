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

    <!-- 三栏布局 -->
    <el-card class="page-card hierarchy-layout" shadow="never" :body-style="{ padding: '0', height: '100%' }">
      <!-- 左：搜索与导航 -->
      <aside class="hier-left">
        <div class="hier-panel-head">搜索与导航</div>
        <div class="hier-panel-body">
          <div class="hier-search">
            <el-input v-model="searchKeyword" placeholder="输入客户名称 / One ID" clearable />
            <el-button type="primary" icon="Search" @click="onSearch">搜索</el-button>
          </div>
          <div class="hier-filter">
            <el-select v-model="filters.hierarchyType" placeholder="层级类型">
              <el-option v-for="item in HIER_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="filters.level" placeholder="全部层级">
              <el-option v-for="item in HIER_LEVEL_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="filters.bu" placeholder="BU">
              <el-option v-for="item in buOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="filters.status" placeholder="状态">
              <el-option v-for="item in HIER_STATUS_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </div>

          <div class="hier-results">
            <div class="hier-results-tip">找到 {{ searchResults.length }} 个授权范围内结果</div>
            <div
              v-for="node in searchResults"
              :key="node.id"
              :class="['hier-result', { on: currentKey === keyOf(node.id) }]"
              @click="locateNode(keyOf(node.id))"
            >
              <b>{{ node.name }}</b>
              <small>{{ node.oneId }} · {{ node.level }} · {{ node.status }}</small>
              <small class="hier-path">{{ node.path }}</small>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中：Legal Hierarchy 树 -->
      <main class="hier-center">
        <div class="hier-panel-head">
          <span>Legal Hierarchy</span>
          <div class="hier-center-tools">
            <el-button text size="small" icon="Back" @click="locateNode('group')">返回根节点</el-button>
            <el-button text size="small" icon="Location" @click="locateNode(currentKey ?? 'store')">定位当前节点</el-button>
            <el-button text size="small" icon="Fold" @click="toggleAll(false)">收起其他分支</el-button>
          </div>
        </div>
        <div class="hier-panel-body hier-tree-body">
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
              <div :class="['hier-node', `level-${data.level?.toLowerCase()}`]">
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
              <template #title>Lazy Load：仅加载祖先路径、目标节点与第一批子节点，展开时按需加载。</template>
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
                <span>{{ currentNode.parent }}</span>
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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type { ElTree } from 'element-plus';
import { getHierarchy, getHierarchyNode, searchHierarchy } from '@/api/demo/cmdPoc';
import type { HierarchyNodeVO } from '@/api/demo/cmdPoc/types';
import {
  HIER_BU_OPTIONS,
  HIER_LEVEL_OPTIONS,
  HIER_STATUS_OPTIONS,
  HIER_TYPE_OPTIONS
} from '../../constants/options';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocHierarchyPanel' });

const { roleKey, readOnly, openDialog } = useCmdPoc();

const treeRef = ref<InstanceType<typeof ElTree>>();
const treeData = ref<HierarchyNodeVO[]>([]);
const treeProps = { label: 'label', children: 'children' };

const searchKeyword = ref('上海优视');
const searchResults = ref<HierarchyNodeVO[]>([]);
const currentKey = ref<string>('store');
const currentNode = ref<HierarchyNodeVO | null>(null);
const expandedKeys = ref<string[]>(['A3-001', 'A2-0188']);

const filters = reactive({
  hierarchyType: 'Legal Hierarchy',
  level: '全部层级',
  bu: 'High End',
  status: 'Active'
});

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

const keyOf = (id: string) => {
  if (id === 'A3-001') return 'group';
  if (id === 'A2-0188') return 'legal';
  if (id === 'A1-000126') return 'suzhou';
  return 'store';
};

const levelTagType = (level: string) => (level === 'A3' ? 'primary' : level === 'A2' ? 'warning' : 'success');

const loadNode = async (key: string) => {
  const node = await getHierarchyNode(key);
  if (node) {
    currentNode.value = node;
    currentKey.value = key;
  }
};

const onSearch = async () => {
  const res = await searchHierarchy(searchKeyword.value || '上海优视');
  searchResults.value = res;
};

const locateNode = async (key: string) => {
  await loadNode(key);
  const id = currentNode.value?.id;
  if (id) {
    treeRef.value?.setCurrentKey(id);
    if (!expandedKeys.value.includes(id)) {
      expandedKeys.value = [...expandedKeys.value, id];
    }
    // 展开父节点
    const parentId = currentNode.value?.parent === '远见集团' ? 'A3-001' : 'A2-0188';
    if (parentId && !expandedKeys.value.includes(parentId)) {
      expandedKeys.value = [...expandedKeys.value, parentId];
    }
  }
};

const handleNodeClick = (data: HierarchyNodeVO) => {
  const key = keyOf(data.id);
  loadNode(key);
};

const toggleAll = (collapse: boolean) => {
  if (collapse) {
    expandedKeys.value = [];
  } else {
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
  }
};

const openRelation = (mode: 'request' | 'manage' | 'child' | 'edit') => {
  openDialog('hierAdd', { mode, nodeKey: currentKey.value });
};

watch(
  () => roleKey.value,
  () => {
    filters.bu = roleKey.value === 'gc' ? 'All Authorized BU' : 'High End';
  },
  { immediate: true }
);

onMounted(async () => {
  treeData.value = await getHierarchy();
  await onSearch();
  await loadNode('store');
});
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
</style>
