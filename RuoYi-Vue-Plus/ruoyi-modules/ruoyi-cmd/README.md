# ruoyi-cmd · Essilor CMD 客户主数据业务模块

## 1. 模块定位

| 关注点 | 归属 |
|---|---|
| **数据库连接配置** | `ruoyi-admin/src/main/resources/application-*.yml` → `spring.datasource.dynamic.datasource.master` |
| **业务逻辑** | 本模块（Controller / Service / Mapper 三层） |
| **前端展示** | `plus-ui/src/views/demo/cmd-poc/**` |

业务表与框架表**同库 `ruoyi_plus`**，不使用独立 schema。隔离靠表前缀：
业务表 `cmd_ / md_ / dq_ / match_ / oneid_ / int_ / audit_ / cfg_ / poc_`，
框架表 `sys_ / flow_ / gen_ / sj_`，两者不冲突，且不建外键、不修改框架表结构，
因此框架自带表不受任何影响。

> 同库而非分库的原因：审批需 JOIN Warm-Flow 的 `flow_*` 表与 `sys_user` / `sys_dept`，
> 并需与流程引擎同事务。独立 schema 会导致跨库无法 JOIN、`@Transactional` 失效、迁移需同步两库。

## 2. 目录结构

```
ruoyi-cmd/src/main/java/org/dromara/cmd/
├── common/CmdConstants.java          业务常量（状态/场景/动作/风险/SLA）
├── controller/                       控制层：接收参数、封装 R<T>、记录操作日志
│   ├── CmdCustomerController.java       /cmd/customer/**     客户主档
│   ├── CmdApprovalController.java       /cmd/approval/**     统一待办审批
│   ├── CmdGovernanceController.java     /cmd/governance/**   治理任务
│   ├── CmdHierarchyController.java      /cmd/hierarchy/**    客户层级
│   └── CmdDashboardController.java      /cmd/dashboard/**    工作台统计
├── domain/                           实体（与表一一对应，@TableName 标注）
├── domain/bo/                        业务对象：新增/修改/查询入参（带校验注解）
├── domain/vo/                        视图对象：出参（带 @ExcelProperty 支持导出）
├── mapper/                           数据访问层（extends BaseMapperPlus，无 XML）
├── service/                          服务层接口
└── service/impl/                     服务层实现（@Transactional + 业务编排，不出现数据源注解）
```

## 3. 关键开发约定

1. **数据源**：统一走框架主库 `ruoyi_plus`（master 数据源），代码中不出现 `@DS` 等数据源注解；
   连接参数只在 `ruoyi-admin` 的 yml 中维护。若未来需读写分离，再在对应 Service 上标注 `@DS`。
2. **One ID 稳定**：`oneId` 生成后永不变更，字段变更只追加 `cmd_customer_version`。
3. **无物理删除**：统一 `@TableLogic delFlag`；客户停用走 `status=inactive + effectiveTo`。
4. **历史不覆盖**：版本表 / 层级关系历史 / DQ Scorecard 全部追加式写入。
5. **扩展属性**：新增未建模字段优先用 `extJson`（MySQL JSON 列），避免频繁 ALTER TABLE。
6. **可配置优先**：阈值、路由、编码规则、下拉选项一律落库（cfg_* / md_* / dq_rule / match_rule / cmd_flow_*），改配置不改代码。

## 4. 已实现域与待扩展域

| 状态 | 域 | 对应页面 |
|---|---|---|
| ✅ 已实现 | 客户主档、统一审批、治理任务、客户层级、工作台统计 | customers / approval / gov / hier / dash |
| ⏳ 待扩展（同一模式） | 批量导入、变更停用、元数据、数据质量、匹配规则、One ID、权限、集成、审计 | batch / change / admin / dqscore / oneid / integration / audit / coverage |

扩展方式：按现有域复制 `domain → mapper → service → controller` 四层即可；
也可用框架自带的「代码生成」（ruoyi-gen）直接由表生成骨架，再补业务规则。

## 5. 接入 Warm-Flow 工作流（后续升级）

框架已集成 Warm-Flow（`flow_definition / flow_instance / flow_task / flow_his_task` 等表），本模块**不自建流程引擎表**，仅通过以下字段关联：

- `cmd_approval_task.flow_instance_id` / `flow_task_id` / `flow_definition_id` / `flow_status`
- `cmd_flow_scene.scene_code → flow_code`（场景到流程的映射，可配置）
- `cmd_flow_node_rule`（节点审批人路由规则，可配置）

升级步骤：
1. 在 Warm-Flow 中设计流程，取得 `flow_code`；
2. 在 `cmd_flow_scene` 中配置 `scene_code → flow_code`；
3. 在 `CmdApprovalServiceImpl#doAction` 中调用 `InsService` 推进引擎并回填实例 ID；
4. 业务表结构无需任何调整。

## 6. 编译

```bash
mvn -o -pl ruoyi-modules/ruoyi-cmd -am -DskipTests compile
```
