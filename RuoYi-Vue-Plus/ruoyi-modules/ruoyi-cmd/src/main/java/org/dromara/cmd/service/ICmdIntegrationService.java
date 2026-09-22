package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.IntConnBo;
import org.dromara.cmd.domain.bo.IntRunBo;
import org.dromara.cmd.domain.vo.IntEndpointVo;
import org.dromara.cmd.domain.vo.IntRunVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 集成监控 / 集成配置 服务层
 * <p>
 * 对应页面：集成监控 integration（端点配置 / 运行记录 / Retry / 手动发布）。
 *
 * @author Essilor CMD POC
 */
public interface ICmdIntegrationService {

    /**
     * 分页查询集成运行记录
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 运行记录分页结果
     */
    PageResult<IntRunVo> selectPage(IntRunBo bo, PageQuery pageQuery);

    /**
     * 重试指定运行记录（重新向目标系统推送一次）
     *
     * @param runCode 运行编号
     * @return 提示文案
     */
    String retry(String runCode);

    /**
     * 保存集成连接配置（按系统做新增或更新）
     *
     * @param bo 连接配置
     * @return 端点编码
     */
    String saveConn(IntConnBo bo);

    /**
     * 查询端点配置列表（集成配置 Tab）
     *
     * @return 端点列表
     */
    List<IntEndpointVo> listEndpoints();

    /**
     * 删除端点配置（逻辑删除）
     *
     * @param id 端点主键
     * @return 提示文案
     */
    String deleteEndpoint(Long id);

    /**
     * 连通性测试：向端点地址发送一条探活报文，记录本次测试到 int_run
     *
     * @param endpointId 端点主键
     * @return 测试提示文案
     */
    String testConn(Long endpointId);

    /**
     * 手动发布：把启用的客户主数据推送到指定端点（模拟下游或真实系统）
     *
     * @param endpointId 端点主键
     * @param count      推送条数（默认 5）
     * @return 发布提示文案
     */
    String publishToSystem(Long endpointId, Integer count);
}
