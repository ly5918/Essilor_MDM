<template>
  <div class="poc-dialog-body">
    <div v-loading="loading">
      <div class="cust-head">
        <div class="cust-head-main">
          <div class="cust-head-name">{{ detail?.legalName || '（无工商名称）' }}</div>
          <div class="cust-head-sub">
            <span class="cust-oneid">One ID · {{ detail?.oneId || EMPTY_TEXT }}</span>
            <span v-if="detail?.legalNameEn" class="cust-en">{{ detail.legalNameEn }}</span>
          </div>
        </div>
        <div class="cust-head-tags">
          <el-tag :type="statusMeta.type" size="small" effect="dark">{{ statusMeta.label }}</el-tag>
          <el-tag v-if="detail?.gcScopeFlag === 'Y'" size="small" type="danger" effect="plain">跨 BU 全局</el-tag>
          <el-tag v-if="detail?.duplicateFlag === 'Y'" size="small" type="danger" effect="dark">疑似重复</el-tag>
          <el-tag v-if="detail?.matchState" size="small" type="info" effect="plain">Match · {{ detail.matchState }}</el-tag>
          <!-- 总设计 MERGE 场景入口：存量主档疑似重复 → 发起跨BU客户合并请求（BU 初审 → GC 决策 → 执行合并） -->
          <el-button
            v-if="detail?.oneId && detail?.status === 'active' && !readOnly"
            class="cust-merge-btn"
            size="small"
            type="warning"
            plain
            @click="onLaunchMerge"
          >发起合并</el-button>
        </div>
      </div>

      <div class="kpi-row">
        <div class="kpi">
          <b :class="{ 'is-empty': !dqFilled }">{{ dqShown }}</b>
          <span>数据质量总分{{ detail?.dqGrade ? ` · ${detail.dqGrade} 级` : '' }}</span>
          <el-progress
            class="cust-bar"
            :percentage="dqPercent"
            :stroke-width="4"
            :show-text="false"
            :color="dqFilled ? dqColor : '#dcdfe6'"
          />
        </div>
        <div class="kpi">
          <b>{{ completeness.filled }}<i class="cust-of">/{{ completeness.total }}</i></b>
          <span>字段完整度 · {{ completeness.percent }}%</span>
          <el-progress
            class="cust-bar"
            :percentage="completeness.percent"
            :stroke-width="4"
            :show-text="false"
            :color="barColor(completeness.percent)"
          />
        </div>
        <div class="kpi">
          <b><DetailValue :value="detail?.bu" tip="" /></b>
          <span>所属 BU</span>
        </div>
        <div class="kpi">
          <b>{{ detail?.customerLevel || '未分层' }}</b>
          <span>客户层级</span>
        </div>
        <div class="kpi">
          <b><DetailValue :value="detail?.sourceSystem" tip="" /></b>
          <span>来源系统</span>
        </div>
      </div>

      <el-alert
        v-if="dqFilled && Number(detail?.dqScore) < 80"
        class="m-b-12"
        type="warning"
        :closable="false"
        show-icon
        :title="`当前数据质量总分 ${detail?.dqScore}（${detail?.dqGrade || EMPTY_TEXT} 级），低于 80 分建议先在「数据质量」执行规则修复，或通过「变更与停用」发起属性变更。`"
      />

      <el-tabs v-model="activeTab" class="cust-tabs">
        <el-tab-pane v-for="section in sections" :key="section.id" :name="section.id">
          <template #label>
            <span class="cust-tab-txt">{{ section.title }}</span>
            <span class="cust-tab-num" :class="{ 'is-full': section.filled === section.total }">
              {{ section.filled }}/{{ section.total }}
            </span>
          </template>

          <!-- 空值图例与收敛开关跟着字段走：切到历史页签时不会让页签行整体位移 -->
          <div class="cust-toolbar">
            <span class="cust-legend">
              <i class="cust-legend-mark">{{ EMPTY_TEXT }}</i>
              表示该字段在 cmd_customer 中为空（不是「查询失败」）
            </span>
            <el-checkbox v-model="hideEmpty" class="cust-only-filled">只看已填写字段</el-checkbox>
          </div>

          <div class="cust-pane-meta">
            本组 {{ section.total }} 个主档字段 · 已填写 {{ section.filled }} 个
            <span v-if="section.filled < section.total" class="cust-pane-miss">
              · 缺 {{ section.total - section.filled }} 个
            </span>
            <span v-if="section.derived" class="cust-pane-derived">· 另有 {{ section.derived }} 个派生字段（不计入完整度）</span>
          </div>

          <el-descriptions v-if="section.fields.length" class="cust-desc" :column="3" size="small" border>
            <el-descriptions-item v-for="field in section.fields" :key="field.key" :label="field.label" :span="field.span ?? 1">
              <template v-if="field.kind === 'hier'">
                <template v-if="hierItem?.mounted">
                  <el-tag size="small" :type="levelTagType(hierItem.level)" effect="plain">{{ hierItem.level }}</el-tag>
                  <span class="cust-path">{{ hierItem.path }}</span>
                </template>
                <template v-else-if="detail?.status === 'active'">
                  <el-tag size="small" type="warning" effect="plain">待归位</el-tag>
                  <span class="cust-path">已批准成为主数据，尚未归位到 A3-A2-A1 层级树，请到「客户层级 → 待归位主数据」处理</span>
                </template>
                <DetailValue v-else value="" tip="未进入层级管理：仅 Active 主数据参与 A3-A2-A1 归位" />
              </template>

              <template v-else-if="field.kind === 'ext'">
                <div v-if="extEntries.length" class="cust-ext">
                  <template v-for="item in extEntries" :key="item.key">
                    <span class="cust-ext-key">{{ item.key }}</span>
                    <span class="cust-ext-val">{{ item.value }}</span>
                  </template>
                </div>
                <DetailValue v-else value="" tip="该主档没有未建模的动态字段（ext_json 为空）" />
              </template>

              <template v-else-if="field.kind === 'tag'">
                <el-tag size="small" :type="tagOf(field.key).type" effect="plain">{{ tagOf(field.key).label }}</el-tag>
              </template>

              <template v-else-if="field.kind === 'score'">
                <!-- 质量等级单独成字段（dqGrade），这里只显示分数本身 -->
                <span v-if="dqFilled" class="cust-score" :class="`is-${dqTagType}`">{{ detail?.dqScore }}</span>
                <DetailValue v-else value="" tip="尚未跑过 DQ 规则，该主档没有质量分" />
              </template>

              <DetailValue
                v-else
                :value="rawOf(field.key)"
                :mono="field.kind === 'mono'"
                :tip="field.tip ?? DEFAULT_EMPTY_TIP"
              />
            </el-descriptions-item>
          </el-descriptions>

          <el-empty
            v-else
            description="本组字段在 cmd_customer 中全部为空（已开启「只看已填写字段」）"
            :image-size="60"
          />
        </el-tab-pane>

        <!-- 以下 4 个页签借鉴 MDM 客户 360 视图（关联 / Remote Key / Merge Log / 追溯），均为切到才加载 -->

        <el-tab-pane name="relations">
          <template #label>
            <!-- 参考页签不放数字角标：数量在面板内说明行可见，且避免数据加载后页签行变宽溢出 -->
            <span class="cust-tab-txt">层级关联</span>
          </template>

          <div v-loading="relationsLoading" class="cust-subpane">
            <template v-if="relations.length">
              <el-table :data="relations" size="small" class="cust-subtable">
                <el-table-column label="方向" width="72">
                  <template #default="{ row }">
                    <el-tag size="small" :type="isChildSide(row) ? 'success' : 'primary'" effect="plain">
                      {{ isChildSide(row) ? '上级' : '下级' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="对端节点" min-width="200">
                  <template #default="{ row }">
                    <span>{{ peerOf(row).name }}</span>
                    <span class="cust-peer-id">{{ peerOf(row).oneId }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="级别" width="64">
                  <template #default="{ row }">
                    <span v-if="peerOf(row).level">{{ peerOf(row).level }}</span>
                    <DetailValue v-else value="" tip="对端节点尚未归位到层级树" />
                  </template>
                </el-table-column>
                <el-table-column prop="relationType" label="关系类型" width="90" />
                <el-table-column prop="hierarchyType" label="层级类型" width="100" />
                <el-table-column label="生效期" width="180">
                  <template #default="{ row }">
                    <span class="cust-validity">{{ row.effectiveFrom || '—' }} → {{ row.effectiveTo || '至今' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="84" />
              </el-table>
              <div class="cust-pane-meta">含历史关系版本；「上级」表示本主档作为子节点挂在其下，「下级」表示挂在本主档之下。</div>
            </template>
            <el-empty
              v-else-if="!relationsLoading"
              description="该主档没有任何层级关系：若已批准成为主数据，请到「客户层级 → 待归位主数据」完成归位"
              :image-size="60"
            />

            <!-- 关系版本历史（总设计：A3-A2-A1、Payer 与关系版本，改挂 / 还原全留痕） -->
            <template v-if="relHistory.length">
              <div class="cust-pane-title">关系版本历史</div>
              <el-table :data="relHistory" size="small" class="cust-subtable">
                <el-table-column label="版本" width="64">
                  <template #default="{ row }">
                    <span class="cust-ver">v{{ row.versionNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.operation === 'CREATE' ? 'success' : 'warning'" effect="plain">
                      {{ row.operation === 'CREATE' ? '新建' : row.operation === 'EXPIRE' ? '失效' : '改挂' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="父节点（改挂前 → 改挂后）" min-width="220">
                  <template #default="{ row }">
                    <span class="cust-mono-cell">{{ row.parentOneId || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="relationType" label="关系类型" width="90" />
                <el-table-column prop="payerOneId" label="Payer" width="120">
                  <template #default="{ row }">
                    <span class="cust-mono-cell">{{ row.payerOneId || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="变更原因" min-width="180">
                  <template #default="{ row }">
                    <span>{{ row.changeReason || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="留痕时间" width="150" />
              </el-table>
              <div class="cust-pane-meta">关系只增不改：每次改挂父节点都写入新版本（含改挂前后快照），当前生效版本以「当前关系」表为准。</div>
            </template>
          </div>
        </el-tab-pane>

        <el-tab-pane name="keys">
          <template #label>
            <span class="cust-tab-txt">来源ID映射</span>
          </template>

          <div v-loading="keysLoading" class="cust-subpane">
            <template v-if="legacyKeys.length">
              <div class="cust-pane-meta">
                该 One ID 在各来源系统中的主键对照（跨系统按 One ID 互认，主档本身登记为
                {{ detail?.sourceSystem || '—' }} / {{ detail?.sourceId || '—' }}）
              </div>
              <el-table :data="legacyKeys" size="small" class="cust-subtable">
                <el-table-column label="类型" width="110">
                  <template #default="{ row }">
                    <el-tooltip v-if="row.mappingType === 'MERGE'" :content="row.remark || '客户合并产生的交叉引用'" placement="top">
                      <el-tag size="small" type="warning" effect="dark">合并交叉引用</el-tag>
                    </el-tooltip>
                    <el-tag v-else size="small" type="info" effect="plain">来源登记</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceSystem" label="来源系统" width="100" />
                <el-table-column prop="legacyCode" label="来源系统编码" min-width="160">
                  <template #default="{ row }">
                    <span class="cust-mono-cell">{{ row.legacyCode || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="来源系统名称" min-width="170">
                  <template #default="{ row }">
                    <span>{{ row.sourceName || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="bu" label="BU" width="100" />
                <el-table-column label="生效自" width="110">
                  <template #default="{ row }">
                    <span class="cust-validity">{{ row.effectiveFrom || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="映射状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'" effect="plain">
                      {{ row.status === 'active' ? '生效' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <div class="cust-pane-meta">
                「合并交叉引用」由合并审批执行时自动写入：被并入方 One ID 保留为映射编码，下游按旧编码仍可路由到本主档。
              </div>
            </template>
            <el-empty
              v-else-if="!keysLoading"
              description="该 One ID 没有登记遗留系统映射（仅有主档自身的来源系统主键，见「来源与质量」页签）"
              :image-size="60"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="merge">
          <template #label>
            <span class="cust-tab-txt">合并记录</span>
          </template>

          <div v-loading="historyLoading || mergeRecordsLoading" class="cust-subpane">
            <el-alert
              v-if="detail?.mergedToOneId"
              class="m-b-12"
              type="warning"
              :closable="false"
              show-icon
              :title="`该 One ID 已合并至 ${detail.mergedToOneId}，当前记录为保留历史的非主记录`"
            />
            <el-alert
              v-else
              class="m-b-12"
              type="success"
              :closable="false"
              show-icon
              title="该 One ID 未被合并，是当前保留的主记录（Golden Record）"
            />
            <el-alert
              v-if="detail?.duplicateFlag === 'Y'"
              class="m-b-12"
              type="error"
              :closable="false"
              show-icon
              title="疑似重复标记为 Y：需在「治理与审批」中人工确认后执行合并，合并后本记录将指向保留的 One ID"
            />

            <!-- 合并单台账（cmd_merge_record，总设计「审计与合并记录」） -->
            <template v-if="mergeRecords.length">
              <div class="cust-pane-title">合并单记录</div>
              <el-table :data="mergeRecords" size="small" class="cust-subtable">
                <el-table-column prop="mergeCode" label="合并单号" width="140">
                  <template #default="{ row }">
                    <span class="cust-mono-cell">{{ row.mergeCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="本记录角色" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="isSurvivorSide(row) ? 'success' : 'warning'" effect="plain">
                      {{ isSurvivorSide(row) ? '保留方' : '被并入' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="合并方向" min-width="200">
                  <template #default="{ row }">
                    <span class="cust-mono-cell">{{ row.mergedOneId }} → {{ row.survivorOneId }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="字段补全" min-width="180">
                  <template #default="{ row }">
                    <span v-if="mergeFieldEntries(row).length">
                      {{ mergeFieldEntries(row).map(item => item.key).join('、') }}
                    </span>
                    <span v-else class="cust-validity">目标字段已齐全，无补全</span>
                  </template>
                </el-table-column>
                <el-table-column label="原因" min-width="190">
                  <template #default="{ row }">
                    <span>{{ row.reason || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'EFFECTIVE' ? 'success' : 'info'" effect="plain">
                      {{ row.status === 'EFFECTIVE' ? '已生效' : '已回滚' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="执行时间" width="150" />
                <el-table-column label="审批留痕" min-width="160">
                  <template #default="{ row }">
                    <span class="cust-validity">{{ row.remark || EMPTY_TEXT }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </template>

            <template v-if="mergeEvents.length">
              <div class="cust-pane-title">合并类生命周期事件</div>
              <div class="timeline-v">
                <div v-for="(event, index) in mergeEvents" :key="`merge-${index}`" class="event">
                  <b>{{ fmtTime(event.date) || EMPTY_TEXT }} · {{ event.stage || EMPTY_TEXT }}</b>
                  <div class="event-desc">{{ event.description || EMPTY_TEXT }}</div>
                </div>
              </div>
            </template>
            <el-empty
              v-else-if="!mergeRecords.length && !historyLoading && !mergeRecordsLoading"
              description="该主档暂无合并事件（未被合并、也未并入其它主档）"
              :image-size="60"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="versions">
          <template #label>
            <span class="cust-tab-txt">版本追溯</span>
          </template>

          <div v-loading="versionsLoading" class="cust-subpane">
            <template v-if="versions.length">
              <el-table :data="versions" size="small" class="cust-subtable">
                <el-table-column type="expand" width="36">
                  <template #default="{ row }">
                    <div class="cust-verdiff">
                      <template v-if="versionDiffOf(row).length">
                        <div v-for="diff in versionDiffOf(row)" :key="diff.field" class="cust-verdiff-row">
                          <span class="cust-verdiff-field">{{ diff.field }}</span>
                          <span class="cust-verdiff-before">{{ diff.before }}</span>
                          <span class="cust-verdiff-arrow">→</span>
                          <span class="cust-verdiff-after">{{ diff.after }}</span>
                        </div>
                      </template>
                      <span v-else class="cust-validity">初始版本：主档首次生效时的完整快照，无属性差异</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="版本" width="76">
                  <template #default="{ row }">
                    <span class="cust-ver" :class="{ 'is-current': row.versionNo === currentVersionNo }">v{{ row.versionNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="changeType" label="变更类型" width="96" />
                <el-table-column label="变更字段" min-width="200">
                  <template #default="{ row }">
                    <span>{{ row.changedFields || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="变更原因" min-width="160">
                  <template #default="{ row }">
                    <span>{{ row.changeReason || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceSystem" label="来源" width="100" />
                <el-table-column prop="createTime" label="版本时间" width="150" />
              </el-table>
              <div class="cust-pane-meta">版本只增不改：属性变更批准生效后写入新版本，One ID 保持不变（v{{ currentVersionNo }} 为当前生效版本）。</div>
            </template>
            <el-empty v-else-if="!versionsLoading" description="该主档尚无版本记录（首次审批通过后会生成 v1）" :image-size="60" />
          </div>
        </el-tab-pane>

        <el-tab-pane name="history">
          <template #label>
            <span class="cust-tab-txt">生命周期历史</span>
          </template>

          <div v-loading="historyLoading" class="cust-history">
            <div v-if="history.length" class="timeline-v">
              <div v-for="(event, index) in history" :key="`${event.date}-${index}`" class="event">
                <b>
                  {{ fmtTime(event.date) || EMPTY_TEXT }} · {{ event.stage || EMPTY_TEXT }}
                  <span v-if="event.operator" class="cust-event-op">· {{ event.operator }}</span>
                </b>
                <div class="event-desc">{{ event.description || EMPTY_TEXT }}</div>
              </div>
            </div>
            <el-empty v-else-if="!historyLoading" description="该 One ID 暂无生命周期事件" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-empty v-if="!loading && !detail" description="未找到该客户主档" :image-size="70" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getChangeVersions,
  getCustomerDetail,
  getHierarchyNode,
  getHierarchyRelationHistory,
  getHierarchyRelations,
  getMergeRecords,
  getOneIdHistory,
  listLegacyMappings
} from '@/api/demo/cmdPoc';
import type {
  ChangeVersionVO,
  CustomerVO,
  HierarchyRelationHistVO,
  HierarchyRelationVO,
  LegacyMappingVO,
  MergeRecordVO,
  OneIdEventVO
} from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { EMPTY_TEXT, customerStatusMeta, isEmptyValue } from '../../constants/options';
import DetailValue from '../DetailValue.vue';

defineOptions({ name: 'CmdPocCustomerDetailDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();
const { hierarchyIndex, openDialog, readOnly } = useCmdPoc();

/** 发起合并（总设计 MERGE 场景）：以当前客户为合并源打开合并申请弹窗 */
const onLaunchMerge = () => {
  openDialog('merge', { oneId: detail.value?.oneId, name: detail.value?.legalName });
};

const loading = ref(false);
/** 调用方若已带上整行数据，先渲染再刷新，弹窗不出现空白帧 */
const detail = ref<CustomerVO | null>((props.payload?.row as CustomerVO | undefined) ?? null);

/** 空值悬浮提示默认文案（可按字段覆盖） */
const DEFAULT_EMPTY_TIP = '该字段在当前主档中为空，可由 Data Steward 通过「变更与停用」补充';

const statusMeta = computed(() => customerStatusMeta(detail.value?.status));

/** 后端时间为 ISO，详情里统一按「日期 时分」展示 */
const fmtTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 16) : '');

/* ------------------------------ 字段分组配置 ------------------------------ */

/**
 * 渲染方式
 * <ul>
 *   <li><code>text</code> 普通文本（默认）</li>
 *   <li><code>mono</code> 等宽字体（编码类字段）</li>
 *   <li><code>tag</code> 布尔 / 枚举标签</li>
 *   <li><code>score</code> 质量分（带等级与着色）</li>
 *   <li><code>hier</code> 层级归属（派生字段，非 cmd_customer 列）</li>
 *   <li><code>ext</code> 扩展属性键值表</li>
 * </ul>
 */
type ValueKind = 'text' | 'mono' | 'tag' | 'score' | 'hier' | 'ext';

interface FieldDef {
  /** 对应 CustomerVO 的字段名（派生字段用自定义 key） */
  key: string;
  label: string;
  /** 跨列数，默认 1 */
  span?: number;
  kind?: ValueKind;
  /** 是否计入「字段完整度」分母：派生字段（层级归属）不计 */
  countable?: boolean;
  /** 该字段专属的空值提示 */
  tip?: string;
}

interface SectionDef {
  /** 分组标识：同时作为 tab 的 name（用英文，避免中文当 name 出现转义问题） */
  id: string;
  title: string;
  fields: FieldDef[];
}

/**
 * cmd_customer 全部业务列（38 个）+ 派生的层级归属，共 40 格。
 * <p>
 * 分组按「用户阅读动线」合并成 4 个字段页签 + 1 个历史页签：
 * 每个页签内容足够充实，避免「一页签六七个字段、大片留白」的空旷感。
 * 页签集合 = 数据库列集合，不多不漏（含此前只作后缀展示的「质量等级」）。
 */
const SECTIONS: SectionDef[] = [
  {
    id: 'basic',
    title: '基本信息',
    fields: [
      { key: 'oneId', label: 'One ID', kind: 'mono', tip: 'One ID 由系统生成，不允许为空' },
      { key: 'legalName', label: '工商名称（中文）' },
      { key: 'legalNameEn', label: '工商名称（英文）' },
      { key: 'shortName', label: '客户简称' },
      { key: 'creditCode', label: '统一社会信用代码', kind: 'mono' },
      { key: 'taxNo', label: '税号', kind: 'mono' },
      { key: 'customerType', label: '客户类型' },
      { key: 'customerLevel', label: '客户层级' },
      { key: 'bu', label: '所属 BU' },
      { key: 'gcScopeFlag', label: '跨 BU 全局可见', kind: 'tag' },
      { key: 'productLine', label: '产品线' },
      { key: 'payerId', label: 'Payer 编码', kind: 'mono' },
      { key: 'hier', label: '层级归属', span: 3, kind: 'hier', countable: false }
    ]
  },
  {
    id: 'contact',
    title: '联络与地址',
    fields: [
      { key: 'country', label: '国家/地区' },
      { key: 'province', label: '省份' },
      { key: 'city', label: '城市' },
      { key: 'address', label: '经营地址', span: 2 },
      { key: 'postalCode', label: '邮编' },
      { key: 'contactName', label: '联系人' },
      { key: 'contactPhone', label: '联系电话' },
      { key: 'contactEmail', label: '客户邮箱' }
    ]
  },
  {
    id: 'quality',
    // 页签宽度紧张（9 个一行），取名从简；空状态文案引用的也是「来源与质量」
    title: '来源与质量',
    fields: [
      { key: 'sourceSystem', label: '来源系统' },
      { key: 'sourceId', label: '来源系统主键', kind: 'mono' },
      { key: 'dqScore', label: '数据质量总分', kind: 'score' },
      { key: 'dqGrade', label: '质量等级' },
      { key: 'matchState', label: '匹配状态' },
      { key: 'duplicateFlag', label: '疑似重复', kind: 'tag' },
      { key: 'mergedToOneId', label: '合并指向 One ID', kind: 'mono', tip: '为空表示该 One ID 未被合并进其它主记录' }
    ]
  },
  {
    id: 'lifecycle',
    title: '治理与版本',
    fields: [
      { key: 'status', label: '主档状态', kind: 'tag' },
      { key: 'versionNo', label: '当前版本' },
      { key: 'effectiveFrom', label: '生效时间' },
      { key: 'effectiveTo', label: '失效时间' },
      { key: 'approvedBy', label: '审批人' },
      { key: 'approvedTime', label: '审批时间' },
      { key: 'flowInstanceId', label: '关联流程实例' },
      { key: 'flowStatus', label: '工作流状态' },
      { key: 'createdAt', label: '创建时间' },
      { key: 'updatedAt', label: '最近更新时间' },
      { key: 'remark', label: '备注', span: 3 },
      { key: 'extJson', label: '扩展属性（未建模字段）', span: 3, kind: 'ext' }
    ]
  }
];

/** 「只看已填写字段」开关：默认关，保证默认视图仍展示全部字段 */
const hideEmpty = ref(false);

/**
 * 字段取值（统一出口）
 * <p>
 * 派生 / 需格式化的字段在这里归一，模板只用 key 取数，
 * 因此空值判定、完整度统计、条件渲染三处口径天然一致。
 */
const rawOf = (key: string): unknown => {
  const row = detail.value;
  if (!row) return undefined;
  switch (key) {
    case 'versionNo':
      return row.versionNo ? `v${row.versionNo}` : '';
    case 'effectiveFrom':
      return fmtTime(row.effectiveFrom);
    case 'effectiveTo':
      return fmtTime(row.effectiveTo);
    case 'approvedBy':
      return row.approvedBy ? `用户 #${row.approvedBy}` : '';
    case 'approvedTime':
      return fmtTime(row.approvedTime);
    case 'createdAt':
      return fmtTime(row.createdAt);
    case 'updatedAt':
      return fmtTime(row.updatedAt);
    // 0 分是「尚未跑 DQ」，不是「质量 0 分」，按空值处理避免误导
    case 'dqScore':
      return Number(row.dqScore ?? 0) > 0 ? row.dqScore : '';
    case 'flowStatus':
      return row.flowStatus ? flowStatusLabel.value : '';
    case 'hier': {
      const hit = hierItem.value;
      if (hit?.mounted) return hit.path;
      // 「待归位」是有价值的结论，纳入完整度，别被空值过滤掉
      return row.status === 'active' ? '待归位' : '';
    }
    case 'extJson':
      return extEntries.value.length ? extEntries.value.map(item => item.key).join('、') : '';
    default:
      return (row as unknown as Record<string, unknown>)[key];
  }
};

/**
 * 分区数据：带「已填写 / 总数」，并按开关过滤空字段
 * <p>
 * 注意这里**不**丢弃空分组 —— tab 数量随开关变化会让页签忽增忽减，
 * 空分组改为在面板内给空状态提示。
 */
const sections = computed(() =>
  SECTIONS.map(section => {
    const countable = section.fields.filter(field => field.countable !== false);
    const filled = countable.filter(field => !isEmptyValue(rawOf(field.key))).length;
    const fields = hideEmpty.value ? section.fields.filter(field => !isEmptyValue(rawOf(field.key))) : section.fields;
    return {
      id: section.id,
      title: section.title,
      fields,
      filled,
      total: countable.length,
      /** 派生字段数（如层级归属）：展示出来但不算进完整度分母 */
      derived: section.fields.length - countable.length
    };
  })
);

/** 字段完整度：分母为 cmd_customer 业务列（不含派生的层级归属） */
const completeness = computed(() => {
  const fields = SECTIONS.flatMap(section => section.fields).filter(field => field.countable !== false);
  const filled = fields.filter(field => !isEmptyValue(rawOf(field.key))).length;
  return { filled, total: fields.length, percent: fields.length ? Math.round((filled / fields.length) * 100) : 0 };
});

const dqFilled = computed(() => Number(detail.value?.dqScore ?? 0) > 0);
const dqShown = computed(() => (dqFilled.value ? String(detail.value?.dqScore) : EMPTY_TEXT));
const dqPercent = computed(() => Math.min(100, Math.max(0, Number(detail.value?.dqScore ?? 0))));

const DQ_COLOR: Record<string, string> = {
  success: '#1f9254',
  info: '#176c9f',
  warning: '#b88230',
  danger: '#c45656'
};

const dqTagType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  const score = Number(detail.value?.dqScore ?? 0);
  if (score >= 90) return 'success';
  if (score >= 80) return 'info';
  if (score >= 60) return 'warning';
  return 'danger';
});

const dqColor = computed(() => DQ_COLOR[dqTagType.value]);
const barColor = (percent: number) => (percent >= 80 ? '#1f9254' : percent >= 50 ? '#176c9f' : '#b88230');

/** 布尔 / 枚举字段 → 标签文案与颜色 */
const tagOf = (key: string): { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' } => {
  const row = detail.value;
  if (key === 'gcScopeFlag') {
    return row?.gcScopeFlag === 'Y' ? { label: '是（GC 可见）', type: 'danger' } : { label: '否', type: 'info' };
  }
  if (key === 'duplicateFlag') {
    return row?.duplicateFlag === 'Y' ? { label: 'Y · 需人工合并', type: 'danger' } : { label: 'N', type: 'info' };
  }
  if (key === 'status') {
    const meta = statusMeta.value;
    return { label: meta.label, type: meta.type };
  }
  return { label: EMPTY_TEXT, type: 'info' };
};

const hierItem = computed(() => (detail.value ? hierarchyIndex.value.get(detail.value.oneId) : undefined));
const levelTagType = (level: string) => (level === 'A3' ? 'primary' : level === 'A2' ? 'warning' : 'success');

/**
 * Warm-Flow 实例状态码 → 中文
 * <p>
 * 业务表只存 flow_instance.flow_status 数值镜像（与 warm-flow-core FlowStatus 枚举一致），
 * 未知码不猜，直接以「状态码 N」透出，避免误标审批结论。
 */
const FLOW_STATUS_MAP: Record<string, string> = {
  '0': '待提交',
  '1': '审批中',
  '2': '通过',
  '3': '自动通过',
  '4': '终止',
  '5': '作废',
  '6': '撤销',
  '7': '取回',
  '8': '已完成',
  '9': '已退回（拒绝）',
  '10': '已失效',
  '11': '任务退回（待补充）',
  '12': '重新开始',
  '13': '待处理'
};

const flowStatusLabel = computed(() => {
  const code = detail.value?.flowStatus;
  if (!code) return '';
  return FLOW_STATUS_MAP[code] ? `${FLOW_STATUS_MAP[code]}（${code}）` : `状态码 ${code}`;
});

/** ext_json → 可读键值对（非 JSON 时原样展示，不让一段字符串撑破布局） */
const extEntries = computed<Array<{ key: string; value: string }>>(() => {
  const raw = detail.value?.extJson;
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return Object.entries(parsed).map(([key, value]) => ({
      key,
      value: value === null || value === undefined ? EMPTY_TEXT : typeof value === 'object' ? JSON.stringify(value) : String(value)
    }));
  } catch {
    return [{ key: 'raw', value: raw }];
  }
});

/* ------------------------------ Tab 切换 ------------------------------ */

/** 生命周期历史 tab 的 name（非字段分组） */
const HISTORY_TAB = 'history';
/** 进弹窗默认落在第一个分组（基本信息），其余分组按需切换 */
const activeTab = ref(SECTIONS[0].id);

const history = ref<OneIdEventVO[]>([]);
const historyLoading = ref(false);
/** 懒加载标记：切到历史 tab 才请求，且只请求一次 */
const historyLoaded = ref(false);

async function loadHistory() {
  // 历史数据挂在流程与审计表上，属于「切到才看」的次要信息，不必随弹窗一起拉
  if (historyLoaded.value || historyLoading.value) return;
  const oneId = detail.value?.oneId ?? (props.payload?.oneId as string) ?? '';
  if (!oneId) return;
  historyLoading.value = true;
  try {
    history.value = await getOneIdHistory(oneId);
    historyLoaded.value = true;
  } catch (error) {
    ElMessage.error((error as Error).message || '加载 One ID 生命周期历史失败');
  } finally {
    historyLoading.value = false;
  }
}

watch(activeTab, tab => ensureTabData(tab));

// 主档详情后到（payload 只带了行数据）时，若用户正停在懒加载页签上，补一次加载
watch(
  () => detail.value?.oneId,
  () => ensureTabData(activeTab.value)
);

/* ------------------------------ 借鉴 MDM 客户 360 的四个页签 ------------------------------ */
/** 层级关联（借鉴「关联」）：父子挂载关系，含历史版本 */
const RELATIONS_TAB = 'relations';
/** 来源 ID 映射（借鉴「Remote Key」）：One ID 与各遗留系统主键的对照 */
const KEYS_TAB = 'keys';
/** 合并记录（借鉴「Merge Log」）：合并指向、疑似重复与合并类事件 */
const MERGE_TAB = 'merge';
/** 版本追溯（借鉴「追溯」）：主档版本链 */
const VERSIONS_TAB = 'versions';

const oneIdOf = () => detail.value?.oneId ?? (props.payload?.oneId as string) ?? '';

/** 层级关联 */
const relations = ref<HierarchyRelationVO[]>([]);
const relationsLoading = ref(false);
const relationsLoaded = ref(false);

async function loadRelations() {
  if (relationsLoaded.value || relationsLoading.value) return;
  if (!oneIdOf()) return;
  relationsLoading.value = true;
  try {
    relations.value = await getHierarchyRelations(oneIdOf());
    relationsLoaded.value = true;
    // 关系行只有 One ID 没有名称：对端节点逐个补拉（每个只拉一次，单个失败不阻塞整页）
    const peers = new Set(
      relations.value.map(row => (isChildSide(row) ? row.parentOneId : row.childOneId)).filter(Boolean)
    );
    await Promise.all(
      [...peers].map(async peerId => {
        try {
          const node = await getHierarchyNode(peerId);
          if (node) peerInfo.value[peerId] = { name: node.name, level: node.level };
        } catch {
          /* 名称补拉失败时回退展示 One ID */
        }
      })
    );
  } catch (error) {
    ElMessage.error((error as Error).message || '加载层级关联失败');
  } finally {
    relationsLoading.value = false;
  }
}

/** 对端节点名称/级别缓存（relations 加载后异步回填） */
const peerInfo = ref<Record<string, { name: string; level: string }>>({});

/** 本主档在关系里是子端（对端即上级）还是父端（对端即下级） */
const isChildSide = (row: { childOneId?: string }) => row.childOneId === detail.value?.oneId;

/** 对端节点解析：名称优先取补拉的节点信息，取不到再透出 One ID */
const peerOf = (row: { childOneId?: string; parentOneId?: string }) => {
  const peerId = (isChildSide(row) ? row.parentOneId : row.childOneId) ?? '';
  const info = peerInfo.value[peerId];
  return { oneId: peerId || EMPTY_TEXT, name: info?.name || peerId || EMPTY_TEXT, level: info?.level || '' };
};

/** 来源 ID 映射 */
const legacyKeys = ref<LegacyMappingVO[]>([]);
const keysLoading = ref(false);
const keysLoaded = ref(false);

async function loadKeys() {
  if (keysLoaded.value || keysLoading.value) return;
  if (!oneIdOf()) return;
  keysLoading.value = true;
  try {
    // 遗留映射清单接口为全量返回，按当前 One ID 在前端过滤（POC 数据量小，无需后端参数）
    const all = await listLegacyMappings();
    legacyKeys.value = all.filter(row => row.oneId === oneIdOf());
    keysLoaded.value = true;
  } catch (error) {
    ElMessage.error((error as Error).message || '加载来源 ID 映射失败');
  } finally {
    keysLoading.value = false;
  }
}

/** 版本追溯 */
const versions = ref<ChangeVersionVO[]>([]);
const versionsLoading = ref(false);
const versionsLoaded = ref(false);

async function loadVersions() {
  if (versionsLoaded.value || versionsLoading.value) return;
  if (!oneIdOf()) return;
  versionsLoading.value = true;
  try {
    versions.value = await getChangeVersions(oneIdOf());
    versionsLoaded.value = true;
  } catch (error) {
    ElMessage.error((error as Error).message || '加载版本历史失败');
  } finally {
    versionsLoading.value = false;
  }
}

/** 当前生效版本号 = 版本链中的最大版本 */
const currentVersionNo = computed(() => versions.value.reduce((max, row) => Math.max(max, Number(row.versionNo ?? 0)), 0));

/** 合并类生命周期事件（合并记录页签复用生命周期数据，不重复请求） */
const mergeEvents = computed(() =>
  history.value.filter(event => {
    const text = `${event.stage ?? ''}${event.description ?? ''}`;
    return text.includes('合并') || text.toLowerCase().includes('merge');
  })
);

/** 按页签按需拉数（每个页签只拉一次，失败后切走再切回可重试） */
function ensureTabData(tab: string) {
  if (tab === HISTORY_TAB || tab === MERGE_TAB) {
    void loadHistory();
    void loadMergeRecords();
  } else if (tab === RELATIONS_TAB) {
    void loadRelations();
    void loadRelationHistory();
  } else if (tab === KEYS_TAB) {
    void loadKeys();
  } else if (tab === VERSIONS_TAB) {
    void loadVersions();
  }
}

/* ------------------------- 合并记录单（cmd_merge_record） ------------------------- */
/** 总设计「审计与合并记录」：原因 / 操作者 / 审批流 / Before-After 落库可回溯 */
const mergeRecords = ref<MergeRecordVO[]>([]);
const mergeRecordsLoading = ref(false);
const mergeRecordsLoaded = ref(false);

async function loadMergeRecords() {
  if (mergeRecordsLoaded.value || mergeRecordsLoading.value) return;
  if (!oneIdOf()) return;
  mergeRecordsLoading.value = true;
  try {
    mergeRecords.value = await getMergeRecords(oneIdOf());
    mergeRecordsLoaded.value = true;
  } catch (error) {
    ElMessage.error((error as Error).message || '加载合并记录失败');
  } finally {
    mergeRecordsLoading.value = false;
  }
}

/** 本主档在该合并单里的角色：保留方（Golden Record）还是被合并方（参数放宽以适配 el-table 插槽行） */
const isSurvivorSide = (row: { survivorOneId?: string }) => row.survivorOneId === detail.value?.oneId;

/** 字段级合并决策：源记录补进了目标哪些字段（fieldJson 解析，失败不阻塞） */
const mergeFieldEntries = (row: { fieldJson?: string | null }): Array<{ key: string; value: string }> => {
  if (!row.fieldJson) return [];
  try {
    const parsed = JSON.parse(row.fieldJson) as Record<string, unknown>;
    return Object.entries(parsed).map(([key, value]) => ({ key, value: value == null ? '' : String(value) }));
  } catch {
    return [];
  }
};

/* ------------------------- 层级关系版本历史（cmd_hierarchy_relation_hist） ------------------------- */
/** 总设计「A3-A2-A1、Payer 与关系版本」：改挂 / 还原的历史版本不覆盖 */
const relHistory = ref<HierarchyRelationHistVO[]>([]);
const relHistoryLoading = ref(false);
const relHistoryLoaded = ref(false);

async function loadRelationHistory() {
  if (relHistoryLoaded.value || relHistoryLoading.value) return;
  if (!oneIdOf()) return;
  relHistoryLoading.value = true;
  try {
    relHistory.value = await getHierarchyRelationHistory({ oneId: oneIdOf() });
    relHistoryLoaded.value = true;
  } catch (error) {
    ElMessage.error((error as Error).message || '加载关系版本历史失败');
  } finally {
    relHistoryLoading.value = false;
  }
}

/* ------------------------- 版本 Before / After 差异 ------------------------- */
interface VersionDiffRow {
  field: string;
  before: string;
  after: string;
}

const shortVal = (value: unknown) => {
  if (value === null || value === undefined || value === '') return EMPTY_TEXT;
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return text.length > 60 ? `${text.slice(0, 60)}…` : text;
};

/** 从 beforeJson / snapshotJson 解析 changedFields 的旧值 → 新值（解析失败只展示字段名；参数放宽以适配插槽行） */
const versionDiffOf = (row: { changedFields?: string | null; beforeJson?: string | null; snapshotJson?: string | null }): VersionDiffRow[] => {
  const fields = (row.changedFields || '').split(',').map(item => item.trim()).filter(Boolean);
  if (!fields.length) return [];
  let before: Record<string, unknown> = {};
  let after: Record<string, unknown> = {};
  try {
    before = row.beforeJson ? (JSON.parse(row.beforeJson) as Record<string, unknown>) : {};
  } catch {
    /* 快照损坏时按空对象降级 */
  }
  try {
    after = row.snapshotJson ? (JSON.parse(row.snapshotJson) as Record<string, unknown>) : {};
  } catch {
    /* 同上 */
  }
  return fields.map(field => ({ field, before: shortVal(before[field]), after: shortVal(after[field]) }));
};

async function load() {
  const oneId = (props.payload?.oneId as string) ?? detail.value?.oneId ?? '';
  if (!oneId) return;
  loading.value = true;
  try {
    const fresh = await getCustomerDetail(oneId);
    if (fresh) detail.value = fresh;
  } catch (error) {
    ElMessage.error((error as Error).message || '加载客户主档失败');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
/* ------------------------------ 抬头 ------------------------------ */
.cust-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: linear-gradient(90deg, var(--app-accent-soft) 0%, transparent 55%), var(--g-card);
  border: 1px solid var(--g-divider);
  border-radius: var(--app-radius-md);
}

.cust-head-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--g-text);
  line-height: 1.4;
}

.cust-head-sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--g-text2);
}

.cust-oneid {
  font-family: Consolas, Monaco, monospace;
  color: #176c9f;
  font-weight: 600;
}

.cust-en {
  color: var(--g-text2);
}

.cust-head-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* ------------------------------ 指标带 ------------------------------ */
.kpi-row .kpi {
  position: relative;

  b {
    display: flex;
    align-items: baseline;
    gap: 2px;
  }

  /*
    全局 `.poc-dialog .kpi span` 会把「指标标签」的 12px 灰字套到任意后代 span 上，
    而 DetailValue 内部正是 span，取值会被误降级成标签样式。
    这里把取值节点的字号/颜色还原为指标值，只让真正的标签保持小灰字。
  */
  :deep(.dv),
  :deep(.dv-val) {
    font-size: 18px;
    color: var(--g-text);
    line-height: 1.3;
  }

  /* 指标为空时同步降噪，避免一堆「—」把指标带染花 */
  b.is-empty {
    color: var(--g-text2);
    opacity: 0.55;
  }
}

.cust-of {
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  color: var(--g-text2);
}

.cust-bar {
  margin-top: 6px;
}

/* ------------------------------ 工具条 ------------------------------ */
.cust-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  padding: 6px 12px;
  background: var(--g-content);
  border: 1px solid var(--g-divider);
  border-radius: var(--app-radius-sm);
}

.cust-legend {
  font-size: 12px;
  color: var(--g-text2);
}

.cust-legend-mark {
  display: inline-block;
  min-width: 16px;
  margin-right: 4px;
  font-style: normal;
  color: var(--app-text-muted);
  opacity: 0.6;
}

.cust-only-filled {
  flex: none;
}

/* ------------------------------ 页签 ------------------------------ */
/* 普通 Element Plus 页签（下划线式）：不做卡片化修饰，保持默认观感 */
.cust-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.cust-tab-txt {
  vertical-align: middle;
}

/* 页签上的「已填写 / 总数」：一眼看出哪组缺字段，比切进去才知道要省事 */
.cust-tab-num {
  display: inline-block;
  min-width: 30px;
  margin-left: 6px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
  color: var(--g-text);
  text-align: center;
  background: var(--g-divider);
  border-radius: 8px;
  vertical-align: middle;

  /* 填满的组分用绿色标注，提示「这组字段齐全」 */
  &.is-full {
    color: #1f9254;
    background: rgba(31, 146, 84, 0.12);
  }
}

.cust-pane-meta {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--app-text-muted);
}

/* 页签面板内的小节标题（合并单记录 / 关系版本历史） */
.cust-pane-title {
  margin: 10px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--g-text);
}

/* 生命周期事件行里的操作者 */
.cust-event-op {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-muted);
}

/* 版本 Before / After 差异展开区 */
.cust-verdiff {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 10px;
}

.cust-verdiff-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.cust-verdiff-field {
  min-width: 120px;
  font-family: Consolas, Monaco, monospace;
  color: var(--app-text-muted);
}

.cust-verdiff-before {
  max-width: 320px;
  color: #c45656;
  text-decoration: line-through;
  word-break: break-all;
}

.cust-verdiff-arrow {
  flex: none;
  color: var(--app-text-muted);
}

.cust-verdiff-after {
  max-width: 320px;
  font-weight: 600;
  color: #1f9254;
  word-break: break-all;
}

.cust-pane-miss {
  color: #b88230;
}

.cust-pane-derived {
  color: var(--app-text-muted);
  opacity: 0.85;
}

/* ------------------------------ 描述表格 ------------------------------ */
.cust-desc {
  /* Element Plus 的标签底色变量，统一换成应用级浅灰（深色主题自动跟随） */
  --el-descriptions-item-bordered-label-background: var(--app-elevated-soft-bg);
}

/* 圆角裁切：EP 边框模式的单元格边框拼出外框，overflow 裁掉直角 */
.cust-desc :deep(.el-descriptions__body) {
  border-radius: var(--app-radius-md);
  overflow: hidden;
  background: var(--g-card);
}

.cust-desc :deep(.el-descriptions__label) {
  width: 150px;
  white-space: nowrap;
  font-weight: 500;
  color: var(--app-text-muted);
  /* 顶对齐：扩展属性那一行会有十几行键值，标签居中会飘在中间 */
  vertical-align: top;
}

.cust-desc :deep(.el-descriptions__content) {
  color: var(--g-text);
  word-break: break-word;
  vertical-align: top;
}

/* 行悬浮高亮：字段横向跨度大，需要一条「横向导轨」帮助对齐 label 与 value */
.cust-desc :deep(.el-descriptions__table tbody tr) {
  transition: background-color 0.15s ease;
}

.cust-desc :deep(.el-descriptions__table tbody tr:hover) {
  background: var(--app-accent-soft);
}

.cust-desc :deep(.el-descriptions__table tbody tr:hover .el-descriptions__label) {
  background: transparent;
}

/* ------------------------------ 内容单元 ------------------------------ */
.cust-path {
  margin-left: 8px;
  font-size: 12px;
  color: var(--g-text2);
}

.cust-score {
  font-weight: 700;

  &.is-success {
    color: #1f9254;
  }
  &.is-info {
    color: #176c9f;
  }
  &.is-warning {
    color: #b88230;
  }
  &.is-danger {
    color: #c45656;
  }
}

/* 扩展属性：两列小表，键列走浅灰底，与主表形成层级差 */
.cust-ext {
  display: grid;
  grid-template-columns: minmax(120px, 200px) 1fr;
  border: 1px solid var(--app-surface-border);
  border-radius: var(--app-radius-sm);
  overflow: hidden;
  font-size: 12px;
}

.cust-ext-key {
  padding: 4px 10px;
  color: var(--app-text-muted);
  background: var(--app-elevated-soft-bg);
  border-bottom: 1px solid var(--app-surface-border);
}

.cust-ext-val {
  padding: 4px 10px;
  font-family: Consolas, Monaco, monospace;
  border-bottom: 1px solid var(--app-surface-border);
}

/* 最后一行不留分隔线，避免与容器下边框叠成双线 */
.cust-ext > :nth-last-child(-n + 2) {
  border-bottom: 0;
}

/* ------------------------------ 生命周期历史 ------------------------------ */
/* 时间线节点样式复用全局 .poc-dialog .timeline-v，这里只补页签内的留白 */
.cust-history {
  min-height: 160px;
  padding: 4px 0 8px;
}

/* ------------------------------ 借鉴页签（关联 / 映射 / 合并 / 版本） ------------------------------ */
.cust-subpane {
  min-height: 160px;
  padding: 4px 0 8px;
}

.cust-subtable {
  margin-bottom: 8px;

  :deep(.el-table__cell) {
    padding: 5px 0;
  }

  :deep(.cell) {
    font-size: 12.5px;
    line-height: 1.4;
  }
}

/* 关系对端节点的 One ID：小号等宽弱化展示，与名称区分主次 */
.cust-peer-id {
  margin-left: 8px;
  font-family: Consolas, Monaco, monospace;
  font-size: 11.5px;
  color: var(--app-text-muted);
}

.cust-validity {
  font-size: 12px;
  color: var(--g-text2);
}

.cust-mono-cell {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

/* 版本号：当前生效版本加主色强调 */
.cust-ver {
  font-family: Consolas, Monaco, monospace;
  font-weight: 600;

  &.is-current {
    color: var(--el-color-primary);

    &::after {
      margin-left: 4px;
      font-size: 11px;
      font-weight: 400;
      content: '当前';
    }
  }
}
</style>
