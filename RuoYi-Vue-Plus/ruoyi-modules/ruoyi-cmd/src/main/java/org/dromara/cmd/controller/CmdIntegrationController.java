package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.IntConnBo;
import org.dromara.cmd.domain.bo.IntRunBo;
import org.dromara.cmd.domain.vo.IntEndpointVo;
import org.dromara.cmd.domain.vo.IntRunVo;
import org.dromara.cmd.service.ICmdIntegrationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成监控 / 集成配置 控制层
 * <p>
 * 对应页面：集成配置 integration（端点配置 / 运行记录 / Retry / 手动发布）。
 * 本控制器同时内置「模拟下游接收台」{@code /mock/deliver}，供 POC 阶段没有真实下游系统时对接演示。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/integration")
public class CmdIntegrationController extends BaseController {

    private final ICmdIntegrationService integrationService;

    /**
     * 分页查询集成运行记录
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 运行记录分页结果
     */
    @GetMapping("/run/list")
    public R<PageResult<IntRunVo>> list(IntRunBo bo, PageQuery pageQuery) {
        return R.ok(integrationService.selectPage(bo, pageQuery));
    }

    /**
     * 重试指定运行记录
     *
     * @param runCode 运行编号
     * @return 提示文案
     */
    @Log(title = "集成监控", businessType = BusinessType.UPDATE)
    @PutMapping("/run/{runCode}/retry")
    public R<String> retry(@PathVariable String runCode) {
        return R.ok(integrationService.retry(runCode));
    }

    /**
     * 保存集成连接配置（旧路径，保留兼容；新前端统一走 /endpoint）
     *
     * @param bo 连接配置
     * @return 端点编码
     */
    @Log(title = "集成配置", businessType = BusinessType.INSERT)
    @PostMapping("/conn")
    public R<String> saveConn(@Validated @RequestBody IntConnBo bo) {
        return R.ok("集成连接已保存，等待连通性测试", integrationService.saveConn(bo));
    }

    /**
     * 查询端点配置列表（集成配置 Tab）
     *
     * @return 端点列表
     */
    @GetMapping("/endpoint/list")
    public R<List<IntEndpointVo>> listEndpoints() {
        return R.ok(integrationService.listEndpoints());
    }

    /**
     * 保存端点配置（新增或编辑）
     *
     * @param bo 端点配置
     * @return 端点编码
     */
    @Log(title = "集成配置", businessType = BusinessType.INSERT)
    @PostMapping("/endpoint")
    public R<String> saveEndpoint(@Validated @RequestBody IntConnBo bo) {
        return R.ok("集成端点已保存，等待连通性测试", integrationService.saveConn(bo));
    }

    /**
     * 删除端点配置
     *
     * @param id 端点主键
     * @return 提示文案
     */
    @Log(title = "集成配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/endpoint/{id}")
    public R<String> deleteEndpoint(@PathVariable Long id) {
        return R.ok(integrationService.deleteEndpoint(id));
    }

    /**
     * 连通性测试：向端点地址发送探活报文
     *
     * @param id 端点主键
     * @return 测试结果文案
     */
    @Log(title = "集成配置", businessType = BusinessType.UPDATE)
    @PostMapping("/endpoint/{id}/test")
    public R<String> testConn(@PathVariable Long id) {
        return R.ok(integrationService.testConn(id));
    }

    /**
     * 手动发布：把生效的客户主数据推送到指定端点
     *
     * @param id    端点主键
     * @param count 推送条数（默认 5）
     * @return 发布结果文案
     */
    @Log(title = "集成配置", businessType = BusinessType.UPDATE)
    @PostMapping("/endpoint/{id}/publish")
    public R<String> publish(@PathVariable Long id, @RequestParam(required = false) Integer count) {
        return R.ok(integrationService.publishToSystem(id, count));
    }

    /**
     * 模拟下游接收台：真实 HTTP 对接点（POC 阶段作为"自测接收台"）。
     * 端点地址配置为 {@code local://deliver}（回环到本应用）或
     * {@code http://<host>:<port>/cmd/integration/mock/deliver} 即可闭环演示发布成功。
     * 该接口放开登录校验，便于本应用回环调用与联调。
     *
     * @param body 收到的客户主数据报文
     * @return 成功应答
     */
    @SaIgnore
    @PostMapping("/mock/deliver")
    public ResponseEntity<Map<String, Object>> mockDeliver(@RequestBody(required = false) String body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("received", true);
        res.put("messageId", "MOCK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        res.put("echo", body == null ? "" : body.substring(0, Math.min(body.length(), 500)));
        return ResponseEntity.ok(res);
    }

    /**
     * 模拟下游故障台：用于演示失败 / Retry 场景。
     * 端点地址配置为 {@code local://fail}（回环到本应用）或
     * {@code .../mock/deliver/fail} 时发布必然失败（HTTP 500）。
     * 该接口放开登录校验，便于本应用回环调用与联调。
     *
     * @return 故障应答
     */
    @SaIgnore
    @PostMapping("/mock/deliver/fail")
    public ResponseEntity<Map<String, Object>> mockDeliverFail(@RequestBody(required = false) String body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("received", false);
        res.put("message", "模拟下游系统故障：目标不可达");
        res.put("echo", body == null ? "" : body.substring(0, Math.min(body.length(), 500)));
        return ResponseEntity.status(500).body(res);
    }
}
