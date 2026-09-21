package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.ApprovalActionBo;
import org.dromara.cmd.domain.bo.CmdFlowInstanceBo;
import org.dromara.cmd.domain.bo.CmdFlowSceneConfigBo;
import org.dromara.cmd.domain.vo.CmdFlowInstanceVo;
import org.dromara.cmd.domain.vo.CmdFlowSceneConfigVo;
import org.dromara.cmd.domain.vo.CmdFlowSceneVO;
import org.dromara.cmd.domain.vo.CmdFlowTraceVo;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdFlowSceneConfigService;
import org.dromara.cmd.service.ICmdFlowTraceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程跟踪 控制层
 * <p>
 * 对应页面：治理与审批 approval 详情「流程跟踪」弹窗
 * （泳道图步骤条 + Warm-Flow 实例进度 + Data context state）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/flow")
public class CmdFlowTraceController extends BaseController {

    private final ICmdFlowTraceService flowTraceService;
    private final ICmdFlowEngineService flowEngineService;
    private final ICmdFlowSceneConfigService flowSceneConfigService;

    /**
     * 按任务编号查询流程跟踪视图
     *
     * @param taskNo 任务编号（cmd_approval_task.task_no，如 AP-20260915-0001）
     * @return 步骤状态 + 上下文变量 + 审批轨迹
     */
    @GetMapping("/trace/{taskNo}")
    public R<CmdFlowTraceVo> trace(@PathVariable String taskNo) {
        return R.ok(flowTraceService.selectTraceByTaskNo(taskNo));
    }

    /**
     * 部署业务场景对应的流程定义到 Warm-Flow（幂等）
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE / MERGE ...）
     * @return 流程定义 ID
     */
    @PostMapping("/deploy/{sceneCode}")
    public R<Long> deploy(@PathVariable String sceneCode) {
        return R.ok(flowEngineService.deployScene(sceneCode));
    }

    /**
     * 启动流程实例（业务单据提交后调用，回写 flow_instance_id）
     *
     * @param taskNo 任务编号
     * @return 流程实例 ID
     */
    @PostMapping("/instance/{taskNo}/start")
    public R<Long> startInstance(@PathVariable String taskNo) {
        return R.ok(flowEngineService.startInstance(taskNo));
    }

    /**
     * 推进流程（审批动作落到 Warm-Flow 引擎）
     *
     * @param taskNo   任务编号
     * @param bo       动作参数（actionType / opinion）
     * @return 推进后的当前节点名称
     */
    @PostMapping("/instance/{taskNo}/advance")
    public R<String> advance(@PathVariable String taskNo, @RequestBody ApprovalActionBo bo) {
        return R.ok(flowEngineService.advance(taskNo, bo.getActionType(), bo.getOpinion()));
    }

    /**
     * 查询流程图形（BPMN 风格：节点 + 连线 + 实例进度高亮）
     *
     * @param taskNo 任务编号
     * @return 图形视图
     */
    @GetMapping("/graph/{taskNo}")
    public R<CmdFlowTraceVo.GraphVo> graph(@PathVariable String taskNo) {
        return R.ok(flowEngineService.graph(taskNo));
    }

    /**
     * 列出全部 CMD 业务场景（流程中心页）
     * <p>
     * 每个场景给出在 Warm-Flow 的部署状态与节点数，对应 V6.1 总设计的业务流。
     *
     * @return 场景视图列表
     */
    @GetMapping("/scenes")
    public R<List<CmdFlowSceneVO>> scenes() {
        return R.ok(flowEngineService.listScenes());
    }

    /**
     * 按场景查询流程图形（部署并发布后读取引擎节点/连线）
     * <p>
     * 不传 taskNo = 定义视图（蓝图，节点全部待执行）；
     * 传 taskNo = 实例视图（按该次执行的实际进度点亮节点）。
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE / MERGE ...）
     * @param taskNo    任务编号（可空）
     * @return 图形视图
     */
    @GetMapping("/graph/scene/{sceneCode}")
    public R<CmdFlowTraceVo.GraphVo> graphByScene(@PathVariable String sceneCode,
                                                  @RequestParam(required = false) String taskNo) {
        return R.ok(flowEngineService.graphByScene(sceneCode, taskNo));
    }

    /**
     * 流程实例记录列表（每一次执行过的工作流都留一条记录，可查看 / 用 Graph 回看泳道图）
     *
     * @param bo        查询条件（关键字 / 状态 / 业务类型 / 运行状态）
     * @param pageQuery 分页参数
     * @return 流程实例记录分页结果
     */
    @GetMapping("/instances")
    public R<PageResult<CmdFlowInstanceVo>> instances(CmdFlowInstanceBo bo, PageQuery pageQuery) {
        return R.ok(flowTraceService.listInstances(bo, pageQuery));
    }

    /**
     * 查询场景的工作流配置（平台管理 › Workflow › 工作流定义 › 某一行「配置」）
     * <p>
     * 对应 V6.1 总设计第 16 页「Workflow配置」：流程节点、路由条件、SLA、超时升级和邮件通知。
     * 返回泳道节点蓝图（标注平台固定 / 可配置）+ 可增删的节点审批人规则。
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE / CUSTOMER_CHANGE / DEACTIVATE / HIER_RELATION / IMPORT_BATCH / MERGE）
     * @return 场景工作流配置
     */
    @GetMapping("/scene/{sceneCode}/config")
    public R<CmdFlowSceneConfigVo> sceneConfig(@PathVariable String sceneCode) {
        return R.ok(flowSceneConfigService.selectConfig(sceneCode));
    }

    /**
     * 保存场景的工作流配置（只更新场景级可配置项与节点规则，平台固定项不可改）
     *
     * @param sceneCode 场景编码
     * @param bo        配置入参（节点规则 id 为空 = 新增）
     * @return 操作结果
     */
    @PutMapping("/scene/{sceneCode}/config")
    public R<Void> updateSceneConfig(@PathVariable String sceneCode, @RequestBody CmdFlowSceneConfigBo bo) {
        return toAjax(flowSceneConfigService.updateConfig(sceneCode, bo));
    }
}
