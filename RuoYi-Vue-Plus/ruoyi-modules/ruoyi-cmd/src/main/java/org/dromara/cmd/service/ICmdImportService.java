package org.dromara.cmd.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.cmd.domain.bo.CmdImportJobBo;
import org.dromara.cmd.domain.vo.CmdImportJobVo;
import org.dromara.cmd.domain.vo.CmdImportResultVo;
import org.dromara.cmd.domain.vo.CmdImportRowVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateMappingVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 批量导入 服务层
 * <p>
 * 对应页面：批量导入 batch（任务列表 / 新建任务 / 查看结果 / 模板与字段映射）。
 *
 * @author Essilor CMD POC
 */
public interface ICmdImportService {

    /**
     * 分页查询导入任务
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页结果
     */
    PageResult<CmdImportJobVo> selectPage(CmdImportJobBo bo, PageQuery pageQuery);

    /**
     * 按任务编号查询导入结果分流
     *
     * @param jobCode 任务编号
     * @return 结果分流
     */
    CmdImportResultVo selectResult(String jobCode);

    /**
     * 新建导入任务
     *
     * @param bo 任务信息（文件名 / 场景 / BU / 总行数）
     * @return 任务编号
     */
    String createJob(CmdImportJobBo bo);

    /**
     * 查询导入模板列表
     *
     * @return 模板列表
     */
    List<CmdImportTemplateVo> selectTemplateList();

    /**
     * 查询模板字段映射
     *
     * @param templateCode 模板编码，为空时取默认模板
     * @return 字段映射列表
     */
    List<CmdImportTemplateMappingVo> selectTemplateMapping(String templateCode);

    /**
     * 下载导入模板
     * <p>
     * 模板不落物理文件：按 cmd_import_template_mapping 的列定义动态生成
     * 仅含表头的 Excel，业务填写后再上传。
     *
     * @param templateCode 模板编码
     * @param response     响应流
     */
    void downloadTemplate(String templateCode, HttpServletResponse response);

    /**
     * 上传填写好的模板文件，创建导入任务并落行明细
     * <p>
     * 数据流向（对齐总设计场景二泳道图）：
     * 文件落盘 → 文件级预检（模板 / 表头 / 行数，失败整批退回）→
     * 行级 DQ 与批次内去重 → 与 CMD 存量匹配 → 四类分流
     *（Exact / Suspected / New / Invalid）→ 统计回写 cmd_import_job。
     * 存在 New 行时自动创建「批量导入确认」审批待办（IMPORT_BATCH 场景）。
     *
     * @param file              上传的文件
     * @param templateCode      使用的模板编码
     * @param errorStrategy     错误策略
     * @param duplicateStrategy 重复策略
     * @param scene             业务场景（为空时取模板定义）
     * @param buScope           归属 BU（为空时取模板定义）
     * @param sourceSystem      来源系统（写入任务备注）
     * @return 任务编号
     */
    String uploadJob(MultipartFile file, String templateCode, String errorStrategy, String duplicateStrategy,
                     String scene, String buScope, String sourceSystem);

    /**
     * 分页查询导入行明细（结果分流下钻「查看 N 条」）
     *
     * @param jobCode    任务编号
     * @param resultType 结果分流（EXACT / SUSPECTED / NEW / INVALID，为空查全部）
     * @param pageQuery  分页参数
     * @return 行明细分页
     */
    PageResult<CmdImportRowVo> selectRowPage(String jobCode, String resultType, PageQuery pageQuery);

    /**
     * 行级治理动作（BU Scope 治理：批量关联、排除或退回修复）
     *
     * @param rowId  行明细主键
     * @param action 动作（LINK 关联已有 One ID / EXCLUDE 排除 / RETURN 退回修复）
     * @param oneId  LINK 时关联的 One ID（为空时使用行上记录的候选）
     * @return 处理结果说明
     */
    String rowAction(Long rowId, String action, String oneId);

    /**
     * 审批结果回调（批量导入确认流 IMPORT_BATCH 的审批动作联动）
     * <p>
     * APPROVE：为 New 行生成客户主档（One ID），任务置 COMPLETED；
     * REJECT：任务置 FAILED；RETURN：任务回到待复核（WAIT_REVIEW）。
     *
     * @param jobCode    任务编号
     * @param actionType 审批动作（APPROVE / REJECT / RETURN 等）
     * @param operator   审批人姓名（审计用）
     */
    void onApproval(String jobCode, String actionType, String operator);
}
