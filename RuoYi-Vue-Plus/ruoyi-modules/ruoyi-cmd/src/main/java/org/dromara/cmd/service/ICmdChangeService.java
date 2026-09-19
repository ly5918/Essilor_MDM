package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdChangeRequestBo;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 变更与停用 服务层
 *
 * @author Essilor CMD POC
 */
public interface ICmdChangeService {

    /**
     * 分页查询变更 / 停用申请
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<CmdChangeRequestVo> selectPage(CmdChangeRequestBo bo, PageQuery pageQuery);

    /**
     * 按申请编号查询详情
     *
     * @param requestCode 申请编号
     * @return 申请详情
     */
    CmdChangeRequestVo selectByRequestCode(String requestCode);

    /**
     * 提交变更 / 停用申请
     *
     * @param bo 申请信息
     * @return 影响行数
     */
    int submit(CmdChangeRequestBo bo);
}
