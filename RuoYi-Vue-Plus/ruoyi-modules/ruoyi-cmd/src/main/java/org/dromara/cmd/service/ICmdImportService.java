package org.dromara.cmd.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.cmd.domain.bo.CmdImportJobBo;
import org.dromara.cmd.domain.vo.CmdImportJobVo;
import org.dromara.cmd.domain.vo.CmdImportResultVo;
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
     * 数据流向：文件保存到本地目录 → 按字段映射解析为行 →
     * 写 cmd_import_job（文件级）+ cmd_import_row（行级）。
     *
     * @param file              上传的文件
     * @param templateCode      使用的模板编码
     * @param errorStrategy     错误策略
     * @param duplicateStrategy 重复策略
     * @return 任务编号
     */
    String uploadJob(MultipartFile file, String templateCode, String errorStrategy, String duplicateStrategy);
}
