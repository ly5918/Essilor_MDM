package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.IntConnBo;
import org.dromara.cmd.domain.bo.IntRunBo;
import org.dromara.cmd.domain.vo.IntRunVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 集成监控 服务层
 * <p>
 * 对应页面：集成监控 integration（运行记录 / Retry / 集成配置保存）。
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
     * 重试指定运行记录
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
}
