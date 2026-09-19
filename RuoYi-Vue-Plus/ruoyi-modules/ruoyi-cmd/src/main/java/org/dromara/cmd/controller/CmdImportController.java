package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdImportJobBo;
import org.dromara.cmd.domain.vo.CmdImportJobVo;
import org.dromara.cmd.domain.vo.CmdImportResultVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateMappingVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateVo;
import org.dromara.cmd.service.ICmdImportService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 批量导入 控制层
 * <p>
 * 对应页面：批量导入 batch（任务列表 / 新建任务 / 查看结果 / 模板与字段映射）。
 * 本层只做参数接收与结果封装，业务逻辑全部在 ICmdImportService 中实现。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/import")
public class CmdImportController extends BaseController {

    private final ICmdImportService importService;

    /**
     * 分页查询导入任务
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页结果
     */
    @GetMapping("/job/list")
    public R<PageResult<CmdImportJobVo>> list(CmdImportJobBo bo, PageQuery pageQuery) {
        return R.ok(importService.selectPage(bo, pageQuery));
    }

    /**
     * 查询导入结果分流
     *
     * @param jobCode 任务编号
     * @return 结果分流
     */
    @GetMapping("/job/{jobCode}/result")
    public R<CmdImportResultVo> result(@PathVariable String jobCode) {
        return R.ok(importService.selectResult(jobCode));
    }

    /**
     * 新建导入任务
     *
     * @param bo 任务信息
     * @return 任务编号
     */
    @Log(title = "批量导入", businessType = BusinessType.INSERT)
    @PostMapping("/job")
    public R<String> create(@Validated @RequestBody CmdImportJobBo bo) {
        return R.ok("导入任务已创建", importService.createJob(bo));
    }

    /**
     * 查询导入模板列表
     *
     * @return 模板列表
     */
    @GetMapping("/template/list")
    public R<List<CmdImportTemplateVo>> templateList() {
        return R.ok(importService.selectTemplateList());
    }

    /**
     * 查询模板字段映射
     *
     * @param templateCode 模板编码，为空时取默认模板
     * @return 字段映射列表
     */
    @GetMapping("/template/mapping")
    public R<List<CmdImportTemplateMappingVo>> templateMapping(@RequestParam(required = false) String templateCode) {
        return R.ok(importService.selectTemplateMapping(templateCode));
    }

    /**
     * 下载导入模板
     * <p>
     * 模板本身不存物理文件：后端按 cmd_import_template_mapping 的列定义
     * 动态生成「仅含表头」的 Excel，业务填写完成后再上传。
     *
     * @param templateCode 模板编码
     * @param response     响应流
     */
    @GetMapping("/template/{templateCode}/download")
    public void downloadTemplate(@PathVariable String templateCode, HttpServletResponse response) {
        importService.downloadTemplate(templateCode, response);
    }

    /**
     * 上传填写好的模板文件
     * <p>
     * 数据流向：文件落盘 → 按字段映射解析为行 →
     * 写 cmd_import_job（文件级）+ cmd_import_row（行级）。
     *
     * @param file              上传的文件
     * @param templateCode      使用的模板编码
     * @param errorStrategy     错误策略
     * @param duplicateStrategy 重复策略
     * @return 任务编号
     */
    @Log(title = "批量导入", businessType = BusinessType.INSERT)
    @PostMapping("/job/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam String templateCode,
                            @RequestParam(required = false) String errorStrategy,
                            @RequestParam(required = false) String duplicateStrategy) {
        return R.ok("导入任务已创建", importService.uploadJob(file, templateCode, errorStrategy, duplicateStrategy));
    }
}
