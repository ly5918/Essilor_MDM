package org.dromara.cmd.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.fesod.sheet.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.common.HeaderColumnWidthStyleStrategy;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdImportJob;
import org.dromara.cmd.domain.CmdImportRow;
import org.dromara.cmd.domain.CmdImportTemplate;
import org.dromara.cmd.domain.CmdImportTemplateMapping;
import org.dromara.cmd.domain.bo.CmdImportJobBo;
import org.dromara.cmd.domain.bo.CmdImportTemplateMappingBo;
import org.dromara.cmd.domain.vo.CmdImportJobVo;
import org.dromara.cmd.domain.vo.CmdImportResultVo;
import org.dromara.cmd.domain.vo.CmdImportRowVo;
import org.dromara.cmd.domain.vo.CmdImportStatsVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateMappingVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdFlowSceneMapper;
import org.dromara.cmd.mapper.CmdImportJobMapper;
import org.dromara.cmd.mapper.CmdImportRowMapper;
import org.dromara.cmd.mapper.CmdImportTemplateMapper;
import org.dromara.cmd.mapper.CmdImportTemplateMappingMapper;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdImportService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 批量导入 服务层实现
 * <p>
 * 关键设计（对齐总设计 V6.1 场景二泳道图）：
 * <ol>
 *   <li>文件级预检：模板存在 + 上传表头必须包含模板全部必填列 + 至少一行数据，失败整批退回（不建任务）；
 *       选填列缺失不阻断（按默认值 / 空值放行），保证模板新增选填字段后存量文件仍可导入</li>
 *   <li>行级 DQ：必填列（cmd_import_template_mapping.is_required）缺失、信用代码格式错误 → Invalid（退回修复）</li>
 *   <li>批次内去重：同批次信用代码 / 客户名称重复 → Suspected（进入治理）</li>
 *   <li>存量匹配：信用代码命中 active 主档 → Exact（关联已有 One ID）；
 *       名称命中 → Suspected（候选 One ID 随行记录，供 BU/GC 治理决策）；其余 → New（审批后生成 One ID）</li>
 *   <li>统计与状态回写：Invalid&gt;0 → Partial Success（部分成功）；有 Suspected/New → Waiting for Review；否则 Completed</li>
 *   <li>审批发布：存在 New 行时自动创建 IMPORT_BATCH 批量导入确认待办并启动流程实例，
 *       审批通过后由 {@link #onApproval} 为 New 行生成客户主档（One ID）</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdImportServiceImpl implements ICmdImportService {

    /** 任务状态：待复核（有 Suspected / New 行待治理或审批） */
    private static final String STATUS_WAIT_REVIEW = "WAIT_REVIEW";

    /** 任务状态：部分成功 */
    private static final String STATUS_PARTIAL = "PARTIAL_SUCCESS";

    /** 任务状态：失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 任务状态：完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 模板状态：已发布 */
    private static final String STATUS_PUBLISHED = "Published";

    /** 模板状态：草稿 */
    private static final String STATUS_DRAFT = "Draft";

    /** 错误策略：整行拒绝 */
    private static final String STRATEGY_REJECT = "Reject Row";

    /** 错误策略：告警放行 */
    private static final String STRATEGY_WARNING = "Warning Row";

    /** 行状态：处理成功 */
    private static final String ROW_STATUS_SUCCESS = "SUCCESS";

    /** 行状态：失败（Invalid，退回修复） */
    private static final String ROW_STATUS_FAILED = "FAILED";

    /** 行状态：治理中（Suspected） */
    private static final String ROW_STATUS_GOVERNANCE = "GOVERNANCE";

    /** 行状态：已跳过（人工排除） */
    private static final String ROW_STATUS_SKIPPED = "SKIPPED";

    /** 结果分流：Exact（关联已有 One ID） */
    private static final String RESULT_EXACT = CmdConstants.MATCH_EXACT;

    /** 结果分流：Suspected（进入人工治理） */
    private static final String RESULT_SUSPECTED = CmdConstants.MATCH_SUSPECTED;

    /** 结果分流：New（审批后生成 One ID） */
    private static final String RESULT_NEW = CmdConstants.MATCH_NEW;

    /** 结果分流：Invalid（退回修复） */
    private static final String RESULT_INVALID = CmdConstants.MATCH_INVALID;

    /** 处理策略：退回修复 */
    private static final String HANDLING_FIX = "FIX";

    /** 处理策略：人工治理 */
    private static final String HANDLING_GOVERNANCE = "GOVERNANCE";

    /** 处理策略：忽略（人工排除） */
    private static final String HANDLING_IGNORE = "IGNORE";

    /** 模板中「客户名称」对应的字段编码 */
    private static final String FIELD_LEGAL_NAME = "legal_name";

    /** 模板中「统一社会信用代码」对应的字段编码 */
    private static final String FIELD_CREDIT_CODE = "credit_code";

    /** 首次提交后的业务节点名（含 BU 关键字，引擎启动后据此定位到 BU_REVIEW 节点） */
    private static final String NODE_NAME_BU_REVIEW = "BU Scope 批量确认";

    /** 批量导入确认流场景（cmd_flow_scene.scene_code） */
    private static final String SCENE_IMPORT_BATCH = CmdConstants.SCENE_IMPORT_BATCH;

    /** 合并场景编码（总设计 MERGE：跨 BU 客户合并 / 迁移） */
    private static final String SCENE_MERGE = CmdConstants.SCENE_MERGE;

    /** 首次提交的处理角色（Data Steward BU Scope） */
    private static final String ROLE_BU_STEWARD = "BU_STEWARD";
    private static final String NAME_BU_STEWARD = "BU Steward";

    /** 场景未配置 SLA 时的兜底时长（小时） */
    private static final long DEFAULT_SLA_HOURS = 24L;

    /** POC 免登录：提交人取当前登录人，取不到时用演示账号兜底 */
    private static final Long DEMO_APPLICANT_ID = 1L;
    private static final String DEMO_APPLICANT_NAME = "Business User";

    /** 申请编号日期段格式（AP-yyyyMMdd-0001） */
    private static final DateTimeFormatter TASK_NO_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 信用代码合法格式（18 位大写字母数字） */
    private static final String CREDIT_CODE_PATTERN = "[0-9A-Z]{18}";

    private final CmdImportJobMapper jobMapper;
    private final CmdImportTemplateMapper templateMapper;
    private final CmdImportTemplateMappingMapper mappingMapper;
    private final CmdImportRowMapper rowMapper;
    private final CmdCustomerMapper customerMapper;
    private final CmdApprovalTaskMapper approvalTaskMapper;
    private final CmdApprovalActionMapper approvalActionMapper;
    private final CmdFlowSceneMapper flowSceneMapper;
    private final ICmdFlowEngineService flowEngineService;
    private final ICmdAuditService auditService;

    /**
     * 上传文件落盘目录（运维可在 application.yml 的 cmd.poc.import-upload-path 调整）。
     */
    @Value("${cmd.poc.import-upload-path:./uploads/cmd-import}")
    private String uploadPath;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdImportJobVo> selectPage(CmdImportJobBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdImportJob> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(bo.getJobCode())) {
            lqw.eq(CmdImportJob::getJobCode, bo.getJobCode());
        }
        if (StringUtils.isNotBlank(bo.getScene())) {
            lqw.eq(CmdImportJob::getScene, bo.getScene());
        }
        if (StringUtils.isNotBlank(bo.getBuScope())) {
            lqw.eq(CmdImportJob::getBuScope, bo.getBuScope());
        }
        if (StringUtils.isNotBlank(bo.getJobStatus())) {
            lqw.eq(CmdImportJob::getJobStatus, bo.getJobStatus());
        }
        // 多状态过滤（页面「仅看待处置」= 待复核 / 进行中 / 部分成功，与侧栏「批量治理」角标同口径）。
        // 此前角标与列表各按一套条件统计，出现「角标 6、列表 0」的自相矛盾（测试报告 BUG-5）。
        if (bo.getJobStatusList() != null && !bo.getJobStatusList().isEmpty()) {
            lqw.in(CmdImportJob::getJobStatus, bo.getJobStatusList());
        }
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            lqw.and(w -> w.like(CmdImportJob::getJobCode, bo.getKeyword())
                .or().like(CmdImportJob::getFileName, bo.getKeyword()));
        }
        lqw.orderByDesc(CmdImportJob::getCreateTime);
        Page<CmdImportJobVo> page = jobMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdImportResultVo selectResult(String jobCode) {
        CmdImportJobVo job = jobMapper.selectVoOne(
            Wrappers.<CmdImportJob>lambdaQuery().eq(CmdImportJob::getJobCode, jobCode));
        if (job == null) {
            throw new ServiceException("导入任务不存在：{}", jobCode);
        }
        CmdImportResultVo vo = new CmdImportResultVo();
        vo.setJobCode(job.getJobCode());
        vo.setExact(nvl(job.getExactCount()));
        vo.setSuspected(nvl(job.getSuspectedCount()));
        vo.setCreated(nvl(job.getNewCount()));
        vo.setReview(nvl(job.getReviewCount()));
        vo.setInvalid(nvl(job.getInvalidCount()));
        vo.setRoutes(buildRoutes(vo));
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createJob(CmdImportJobBo bo) {
        if (StringUtils.isBlank(bo.getFileName())) {
            throw new ServiceException("文件名不能为空");
        }
        CmdImportJob entity = new CmdImportJob();
        entity.setJobCode("IMP-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        entity.setJobName(StringUtils.blankToDefault(bo.getJobName(), bo.getFileName()));
        entity.setFileName(bo.getFileName());
        entity.setScene(StringUtils.blankToDefault(bo.getScene(), "DOOR"));
        entity.setBuScope(bo.getBuScope());
        entity.setTotalCount(nvl(bo.getTotalCount()));
        entity.setSubmitBy(StringUtils.blankToDefault(bo.getSubmitBy(), "Business User"));
        entity.setSubmitTime(LocalDateTime.now());
        entity.setJobStatus(STATUS_WAIT_REVIEW);
        entity.setRemark(bo.getRemark());
        jobMapper.insert(entity);
        return entity.getJobCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdImportTemplateVo> selectTemplateList() {
        List<CmdImportTemplate> list = templateMapper.selectList(
            Wrappers.<CmdImportTemplate>lambdaQuery().orderByAsc(CmdImportTemplate::getId));
        List<CmdImportTemplateVo> vos = new ArrayList<>();
        for (CmdImportTemplate entity : list) {
            CmdImportTemplateVo vo = new CmdImportTemplateVo();
            vo.setId(entity.getId());
            vo.setTemplateCode(entity.getTemplateCode());
            vo.setTemplateName(entity.getTemplateName());
            vo.setScene(entity.getScene());
            vo.setBuScope(entity.getBuScope());
            vo.setCustomerType(entity.getCustomerType());
            vo.setProductLine(entity.getProductLine());
            vo.setSourceSystem(entity.getSourceSystem());
            vo.setVersionNo(entity.getVersionNo());
            vo.setFilePath(entity.getFilePath());
            vo.setRemark(entity.getRemark());
            vo.setStatus("0".equals(entity.getStatus()) ? STATUS_PUBLISHED : STATUS_DRAFT);
            Long fieldCount = mappingMapper.selectCount(
                Wrappers.<CmdImportTemplateMapping>lambdaQuery()
                    .eq(CmdImportTemplateMapping::getTemplateCode, entity.getTemplateCode()));
            vo.setFieldCount(fieldCount == null ? 0 : fieldCount.intValue());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdImportTemplateMappingVo> selectTemplateMapping(String templateCode) {
        LambdaQueryWrapper<CmdImportTemplateMapping> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(templateCode)) {
            lqw.eq(CmdImportTemplateMapping::getTemplateCode, templateCode);
        }
        lqw.orderByAsc(CmdImportTemplateMapping::getOrderNum);
        List<CmdImportTemplateMappingVo> vos = mappingMapper.selectVoList(lqw);
        for (CmdImportTemplateMappingVo vo : vos) {
            vo.setErrorStrategy("Y".equals(vo.getIsRequired()) ? STRATEGY_REJECT : STRATEGY_WARNING);
        }
        return vos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveMapping(CmdImportTemplateMappingBo bo) {
        if (bo == null || bo.getId() == null) {
            return insertMapping(bo);
        }
        return updateMapping(bo);
    }

    /**
     * 新增上传字段：管线全链路由 cmd_import_template_mapping 驱动，
     * 新列自动进入模板下载表头、上传表头预检、行级 DQ（is_required）与行明细 JSON。
     */
    private String insertMapping(CmdImportTemplateMappingBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getTemplateCode())
            || StringUtils.isBlank(bo.getColumnName()) || StringUtils.isBlank(bo.getFieldCode())) {
            throw new ServiceException("模板编码、源列与目标字段编码不能为空");
        }
        CmdImportTemplate template = getTemplate(bo.getTemplateCode().trim());
        List<CmdImportTemplateMapping> existing = selectMappings(template.getTemplateCode());
        String newColumn = bo.getColumnName().trim();
        String newField = bo.getFieldCode().trim();
        for (CmdImportTemplateMapping m : existing) {
            if (newField.equalsIgnoreCase(m.getFieldCode())) {
                throw new ServiceException("目标字段编码 [{}] 在模板中已存在", newField);
            }
            if (newColumn.equalsIgnoreCase(StringUtils.blankToDefault(m.getColumnName(), m.getFieldCode()))) {
                throw new ServiceException("源列 [{}] 在模板中已存在", newColumn);
            }
        }
        int next = existing.stream()
            .map(CmdImportTemplateMapping::getColumnIndex)
            .filter(java.util.Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0) + 1;

        CmdImportTemplateMapping entity = new CmdImportTemplateMapping();
        entity.setTemplateId(template.getId());
        entity.setTemplateCode(template.getTemplateCode());
        entity.setColumnIndex(next);
        entity.setOrderNum(next);
        entity.setStatus("0");
        entity.setIsRequired(CmdConstants.YES.equals(bo.getIsRequired()) ? CmdConstants.YES : "N");
        entity.setColumnName(newColumn);
        entity.setFieldCode(newField);
        entity.setFieldName(bo.getFieldName());
        entity.setDataType(StringUtils.blankToDefault(bo.getDataType(), "Text"));
        entity.setDefaultValue(bo.getDefaultValue());
        entity.setConvertRule(bo.getConvertRule());
        entity.setRemark(bo.getRemark());
        mappingMapper.insert(entity);
        return "已新增上传字段「" + newColumn + "」，模板下载 / 表头预检 / 行级 DQ 将自动包含该列";
    }

    /**
     * 更新字段映射：仅开放展示与校验相关字段；主键字段（客户名称 / 统一社会信用代码）
     * 是批次内去重与存量匹配的锚点，不允许取消必填或改名。
     */
    private String updateMapping(CmdImportTemplateMappingBo bo) {
        CmdImportTemplateMapping entity = mappingMapper.selectById(bo.getId());
        if (entity == null) {
            throw new ServiceException("字段映射不存在：{}", bo.getId());
        }
        boolean core = isCoreField(entity.getFieldCode());
        if (core && !CmdConstants.YES.equals(bo.getIsRequired())) {
            throw new ServiceException("主键字段 [{}] 是去重与存量匹配的锚点，必须保持必填", entity.getFieldCode());
        }
        if (StringUtils.isNotBlank(bo.getColumnName())) {
            String newColumn = bo.getColumnName().trim();
            List<CmdImportTemplateMapping> others = selectMappings(entity.getTemplateCode());
            for (CmdImportTemplateMapping m : others) {
                if (m.getId() != null && !m.getId().equals(entity.getId())
                    && newColumn.equalsIgnoreCase(StringUtils.blankToDefault(m.getColumnName(), m.getFieldCode()))) {
                    throw new ServiceException("源列 [{}] 与其他字段重复", newColumn);
                }
            }
            entity.setColumnName(newColumn);
        }
        entity.setFieldName(bo.getFieldName());
        if (!core) {
            entity.setIsRequired(CmdConstants.YES.equals(bo.getIsRequired()) ? CmdConstants.YES : "N");
            if (StringUtils.isNotBlank(bo.getDataType())) {
                entity.setDataType(bo.getDataType());
            }
        }
        entity.setDefaultValue(bo.getDefaultValue());
        entity.setConvertRule(bo.getConvertRule());
        entity.setRemark(bo.getRemark());
        mappingMapper.updateById(entity);
        return "字段「" + StringUtils.blankToDefault(entity.getColumnName(), entity.getFieldCode()) + "」已更新";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteMapping(Long id) {
        CmdImportTemplateMapping entity = mappingMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("字段映射不存在：{}", id);
        }
        if (isCoreField(entity.getFieldCode())) {
            throw new ServiceException("主键字段 [{}] 是去重 / 存量匹配 / 发布建主档的锚点，不允许删除",
                entity.getFieldCode());
        }
        mappingMapper.deleteById(id);
        return "字段「" + StringUtils.blankToDefault(entity.getColumnName(), entity.getFieldCode()) + "」已移除";
    }

    /**
     * 是否管线主键字段（客户名称 / 统一社会信用代码）
     *
     * @param fieldCode 字段编码
     * @return true = 主键字段
     */
    private boolean isCoreField(String fieldCode) {
        return FIELD_LEGAL_NAME.equals(fieldCode) || FIELD_CREDIT_CODE.equals(fieldCode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void downloadTemplate(String templateCode, HttpServletResponse response) {
        CmdImportTemplate template = getTemplate(templateCode);
        List<CmdImportTemplateMapping> mappings = selectMappings(template.getTemplateCode());
        if (CollUtil.isEmpty(mappings)) {
            throw new ServiceException("模板[{}]未配置字段映射，无法生成模板文件", template.getTemplateName());
        }
        // 模板不落物理文件：按字段映射动态生成「仅表头」的 Excel，业务填写后再上传
        List<List<String>> head = new ArrayList<>();
        for (CmdImportTemplateMapping mapping : mappings) {
            head.add(Collections.singletonList(
                StringUtils.blankToDefault(mapping.getColumnName(), mapping.getFieldCode())));
        }
        String filename = template.getTemplateName() + "_" + template.getVersionNo() + ".xlsx";
        String sheetName = StringUtils.blankToDefault(template.getSheetName(), "Template");
        try {
            FileUtils.setAttachmentResponseHeader(response, filename);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
            FesodSheet.write(response.getOutputStream())
                .head(head)
                .autoCloseStream(false)
                // 列宽按表头文字计算：模板只有表头没有数据行，用 LongestMatchColumnWidthStyleStrategy
                // 会因「无内容可测」退化成窄列，表头被折成 CustomerN/ame
                .registerWriteHandler(new HeaderColumnWidthStyleStrategy())
                .registerWriteHandler(headStyleStrategy())
                .sheet(sheetName)
                .doWrite(Collections.<List<String>>emptyList());
        } catch (IOException e) {
            throw new ServiceException("模板文件生成失败：" + e.getMessage());
        }
    }

    /**
     * 模板表头样式：加粗 + 浅灰底 + 居中，与业务侧看到的数据表头保持一致观感
     *
     * @return 表头/内容两段式样式策略
     */
    private HorizontalCellStyleStrategy headStyleStrategy() {
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setFontHeightInPoints((short) 11);

        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setWriteFont(headFont);
        headStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadJob(MultipartFile file, String templateCode, String errorStrategy, String duplicateStrategy,
                            String scene, String buScope, String sourceSystem) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的文件");
        }
        CmdImportTemplate template = getTemplate(templateCode);
        List<CmdImportTemplateMapping> mappings = selectMappings(template.getTemplateCode());
        if (CollUtil.isEmpty(mappings)) {
            throw new ServiceException("模板[{}]未配置字段映射，无法解析上传文件", template.getTemplateName());
        }

        String originalFilename = StringUtils.blankToDefault(file.getOriginalFilename(), "upload.xlsx");
        Path saved = saveFile(file, originalFilename);
        RowCollector collector = readRows(saved, template);

        // 文件级预检（对应泳道图「文件级预检」节点）：表头缺列或无数据行 → 整批退回，不建任务
        String missing = preCheckHeader(collector, mappings);
        if (StringUtils.isNotBlank(missing)) {
            throw new ServiceException("文件预检未通过：表头缺少列 " + missing + "，已整批退回（请使用最新模板填写）");
        }
        if (collector.getRows().isEmpty()) {
            throw new ServiceException("文件预检未通过：未解析到数据行，已整批退回");
        }

        CmdImportJob job = buildJob(file, template, originalFilename, saved, collector.getRows().size(),
            errorStrategy, duplicateStrategy, scene, buScope, sourceSystem);
        jobMapper.insert(job);

        // 行级 DQ + 批次内去重 + 存量匹配 → 四类分流（Exact / Suspected / New / Invalid）
        Outcome outcome = classifyRows(job, template, mappings, collector);
        applyOutcome(job, outcome);

        // 存在 New 行 → 提交「批量导入确认」审批（New 审批后生成 One ID）
        if (outcome.created > 0) {
            submitApproval(job, outcome);
        }

        recordImportAudit(job, outcome);
        return job.getJobCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdImportStatsVo selectStats() {
        List<CmdImportJob> jobs = jobMapper.selectList(Wrappers.lambdaQuery());
        CmdImportStatsVo vo = new CmdImportStatsVo();
        vo.setJobCount((long) jobs.size());
        long total = 0L;
        long exact = 0L;
        long suspected = 0L;
        long created = 0L;
        long review = 0L;
        long invalid = 0L;
        for (CmdImportJob job : jobs) {
            total += nz(job.getTotalCount());
            exact += nz(job.getExactCount());
            suspected += nz(job.getSuspectedCount());
            created += nz(job.getNewCount());
            review += nz(job.getReviewCount());
            invalid += nz(job.getInvalidCount());
        }
        vo.setTotalRows(total);
        vo.setExactCount(exact);
        vo.setSuspectedCount(suspected);
        vo.setNewCount(created);
        vo.setReviewCount(review);
        vo.setInvalidCount(invalid);
        return vo;
    }

    /** 数值空值兜底（导入任务统计列允许为 NULL，且建表类型为 INT） */
    private long nz(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdImportRowVo> selectRowPage(String jobCode, String resultType, PageQuery pageQuery) {
        CmdImportJob job = getJobByCode(jobCode);
        LambdaQueryWrapper<CmdImportRow> lqw = Wrappers.lambdaQuery();
        lqw.eq(CmdImportRow::getJobId, job.getId());
        if (StringUtils.isNotBlank(resultType)) {
            lqw.eq(CmdImportRow::getResultType, resultType.trim().toUpperCase(Locale.ROOT));
        }
        lqw.orderByAsc(CmdImportRow::getRowNo);
        Page<CmdImportRowVo> page = rowMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rowAction(Long rowId, String action, String oneId) {
        if (rowId == null) {
            throw new ServiceException("行明细主键不能为空");
        }
        CmdImportRow row = rowMapper.selectById(rowId);
        if (row == null) {
            throw new ServiceException("导入行不存在：{}", rowId);
        }
        CmdImportJob job = getJobByCode(row.getJobCode());
        String type = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        CmdImportRow patch = new CmdImportRow();
        patch.setId(row.getId());
        String message;
        // LINK 需要把 handling / error_summary 置空，而 MyBatis-Plus updateById 默认跳过 null 字段，
        // 故此类「清空型」治理动作在 updateById 之后再显式 update 一次
        boolean clearNullableFields = false;
        switch (type) {
            case "LINK" -> {
                // BU Scope 治理：关联已有 One ID（Suspected → Exact）
                String target = StringUtils.blankToDefault(oneId, row.getOneId());
                if (StringUtils.isBlank(target)) {
                    throw new ServiceException("请填写要关联的 One ID");
                }
                // 跨BU 命中 → 不直接关联，按总设计 MERGE 场景发起「跨BU客户合并」审批
                //（BU 初审 → GC 决策 → 批准后由治理服务执行关联 / 合并）
                CmdCustomer targetCustomer = selectActiveCustomer(target);
                if (targetCustomer != null && job.getBuScope() != null
                    && !job.getBuScope().equals(targetCustomer.getBuScope())) {
                    // 行保持 Suspected，仅标记治理中说明，批准合并后由 execMergeTask 回写为 Exact
                    patch.setHandling("跨BU合并审批中（" + target + "）");
                    rowMapper.updateById(patch);
                    recalcJobCounts(job);
                    return launchRowMerge(job, row, targetCustomer);
                }
                patch.setResultType(RESULT_EXACT);
                patch.setRowStatus(ROW_STATUS_SUCCESS);
                patch.setOneId(target);
                patch.setMatchState(RESULT_EXACT);
                patch.setMatchScore(BigDecimal.valueOf(100));
                patch.setErrorCount(0);
                clearNullableFields = true;
                message = "已关联 One ID " + target;
            }
            case "EXCLUDE" -> {
                // 排除：本行不纳入主档（Suspected → Invalid / Skipped）
                patch.setResultType(RESULT_INVALID);
                patch.setRowStatus(ROW_STATUS_SKIPPED);
                patch.setHandling(HANDLING_IGNORE);
                patch.setErrorCount(1);
                patch.setErrorSummary("人工排除（不纳入主档）");
                message = "该行已排除，不再纳入主档";
            }
            case "RETURN" -> {
                // 退回修复：回到 Business User 修复后重新上传（Suspected → Invalid / Fix）
                patch.setResultType(RESULT_INVALID);
                patch.setRowStatus(ROW_STATUS_FAILED);
                patch.setHandling(HANDLING_FIX);
                patch.setErrorCount(1);
                patch.setErrorSummary("退回修复（数据疑似重复，需线下核实后再导入）");
                message = "该行已退回修复";
            }
            default -> throw new ServiceException("不支持的治理动作：{}（仅支持 LINK / EXCLUDE / RETURN）", action);
        }
        rowMapper.updateById(patch);
        if (clearNullableFields) {
            rowMapper.update(Wrappers.<CmdImportRow>lambdaUpdate()
                .eq(CmdImportRow::getId, row.getId())
                .set(CmdImportRow::getHandling, null)
                .set(CmdImportRow::getErrorSummary, null));
        }
        recalcJobCounts(job);
        recordGovernanceAudit(job, row, type, message);
        return message;
    }

    /** 查询 active 主档（按 One ID，取最新一条） */
    private CmdCustomer selectActiveCustomer(String oneId) {
        List<CmdCustomer> list = customerMapper.selectList(Wrappers.<CmdCustomer>lambdaQuery()
            .eq(CmdCustomer::getOneId, oneId)
            .eq(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_ACTIVE)
            .orderByDesc(CmdCustomer::getCreateTime)
            .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 批量导入行跨BU命中 → 发起「跨BU客户合并」审批（总设计 MERGE 场景）
     * <p>创建 sceneCode=MERGE 的审批待办（bizSnapshotJson 携带 mode=ROW_LINK / rowId / targetOneId），
     * BU 初审 →（跨BU升级）GC 决策 → 批准后由治理服务 execMergeTask 把行回写为 Exact 并关联目标 One ID。
     *
     * @param job            导入任务
     * @param row            Suspected 行
     * @param targetCustomer 命中的跨BU存量主档
     * @return 结果文案
     */
    private String launchRowMerge(CmdImportJob job, CmdImportRow row, CmdCustomer targetCustomer) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> snapshot = new LinkedHashMap<>(4);
        snapshot.put("mode", "ROW_LINK");
        snapshot.put("rowId", row.getId());
        snapshot.put("targetOneId", targetCustomer.getOneId());
        Map<String, Object> evidence = new LinkedHashMap<>(8);
        evidence.put("合并模式", "批量导入行 → 跨BU存量主档（关联已有 One ID，需 GC 决策）");
        evidence.put("发起原因", "导入行与 " + targetCustomer.getBuScope() + " 主档疑似重复（跨BU），升级合并审批");
        evidence.put("导入行", row.getJobCode() + " 第 " + row.getRowNo() + " 行 · " + row.getLegalName());
        evidence.put("目标记录", targetCustomer.getOneId() + " · " + targetCustomer.getLegalName()
            + " · " + targetCustomer.getBuScope());

        CmdApprovalTask task = new CmdApprovalTask();
        task.setTaskNo(generateTaskNo());
        task.setTaskCategory(CmdConstants.APPR_CAT_APPROVAL);
        task.setBizType(CmdConstants.BIZ_TYPE_MERGE);
        task.setBizId(String.valueOf(row.getId()));
        task.setBizTitle("跨BU合并：" + row.getLegalName() + " → " + targetCustomer.getLegalName());
        task.setSceneCode(SCENE_MERGE);
        task.setApplicantId(LoginHelper.getUserId());
        task.setApplicantName(resolveApplicant().name());
        task.setBuScope(job.getBuScope());
        task.setScope(CmdConstants.SCOPE_BU);
        task.setCurrentNodeCode("BU_REVIEW");
        task.setCurrentNodeName(NODE_NAME_BU_REVIEW);
        task.setAssigneeName(NAME_BU_STEWARD);
        task.setAssigneeRole(ROLE_BU_STEWARD);
        task.setStatus(CmdConstants.APPR_STATUS_PENDING);
        task.setRiskLevel("High");
        task.setDuplicateState(RESULT_SUSPECTED);
        task.setCrossBuFlag(CmdConstants.YES);
        task.setSubmitTime(now);
        task.setSlaDue(now.plusHours(resolveSlaHours()));
        task.setSlaState(CmdConstants.SLA_NORMAL);
        task.setEvidenceJson(JsonUtils.toJsonString(evidence));
        task.setBizSnapshotJson(JsonUtils.toJsonString(snapshot));
        task.setRemark("导入行跨BU疑似重复，BU 发起合并审批");
        approvalTaskMapper.insert(task);

        AuditEvent audit = new AuditEvent();
        audit.setEventType("MERGE");
        audit.setEventName("导入行跨BU疑似重复，发起合并审批：" + row.getLegalName() + " → " + targetCustomer.getLegalName());
        audit.setBizType(SCENE_MERGE);
        audit.setBizId(task.getTaskNo());
        audit.setOneId(targetCustomer.getOneId());
        audit.setOperatorId(LoginHelper.getUserId());
        audit.setOperatorName(resolveApplicant().name());
        audit.setEventTime(now);
        audit.setResult("SUCCESS");
        auditService.record(audit);
        return "跨BU命中（目标 " + targetCustomer.getBuScope() + " 主档 " + targetCustomer.getOneId()
            + "），已发起客户合并审批 " + task.getTaskNo() + "，批准后自动关联";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproval(String jobCode, String actionType, String operator) {
        CmdImportJob job = getJobByCode(jobCode);
        String type = actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
        CmdImportJob patch = new CmdImportJob();
        patch.setId(job.getId());
        patch.setEndTime(LocalDateTime.now());
        if (CmdConstants.ACTION_APPROVE.equals(type)) {
            // 批量处理结果：New 行审批通过后生成 One ID 并激活主档
            int published = publishNewRows(job);
            patch.setJobStatus(STATUS_COMPLETED);
            patch.setRemark(appendRemark(job.getRemark(), "审批通过：" + (published) + " 条 New 行已生成 One ID（审批人：" + operator + "）"));
            jobMapper.updateById(patch);
        } else if (CmdConstants.ACTION_REJECT.equals(type)) {
            patch.setJobStatus(STATUS_FAILED);
            patch.setErrorMessage("批量导入确认被拒绝（审批人：" + operator + "）");
            jobMapper.updateById(patch);
        } else if (CmdConstants.ACTION_RETURN.equals(type)) {
            patch.setJobStatus(STATUS_WAIT_REVIEW);
            patch.setRemark(appendRemark(job.getRemark(), "审批退回补充（审批人：" + operator + "）"));
            jobMapper.updateById(patch);
        } else {
            log.debug("[CMD][IMPORT] 审批动作[{}]不改变导入任务状态：{}", actionType, jobCode);
        }
    }

    // ================================ 上传处理管线 ================================

    /**
     * 按模板编码查询模板定义
     *
     * @param templateCode 模板编码
     * @return 模板定义
     */
    private CmdImportTemplate getTemplate(String templateCode) {
        if (StringUtils.isBlank(templateCode)) {
            throw new ServiceException("模板编码不能为空");
        }
        CmdImportTemplate template = templateMapper.selectOne(
            Wrappers.<CmdImportTemplate>lambdaQuery()
                .eq(CmdImportTemplate::getTemplateCode, templateCode)
                .last("limit 1"));
        if (template == null) {
            throw new ServiceException("导入模板不存在：{}", templateCode);
        }
        return template;
    }

    /**
     * 按列序号升序查询模板字段映射
     *
     * @param templateCode 模板编码
     * @return 字段映射列表
     */
    private List<CmdImportTemplateMapping> selectMappings(String templateCode) {
        return mappingMapper.selectList(
            Wrappers.<CmdImportTemplateMapping>lambdaQuery()
                .eq(CmdImportTemplateMapping::getTemplateCode, templateCode)
                .orderByAsc(CmdImportTemplateMapping::getColumnIndex));
    }

    /**
     * 文件级预检：上传表头必须包含模板定义的全部「必填」列
     * <p>
     * 选填列（is_required=N）缺失不阻断整批：模板下载仍包含全部列，
     * 选填列缺失时按默认值 / 空值放行（与错误策略「必填→Reject / 选填→Warning」同口径），
     * 保证模板新增选填字段后存量文件仍可继续导入。
     *
     * @param collector 行采集器（含规范化表头）
     * @param mappings  模板字段映射
     * @return 缺失列名串（空串表示通过）
     */
    private String preCheckHeader(RowCollector collector, List<CmdImportTemplateMapping> mappings) {
        List<String> missing = new ArrayList<>();
        for (CmdImportTemplateMapping mapping : mappings) {
            if (!CmdConstants.YES.equals(mapping.getIsRequired())) {
                continue;
            }
            String column = StringUtils.blankToDefault(mapping.getColumnName(), mapping.getFieldCode());
            if (!collector.hasColumn(column)) {
                missing.add(column);
            }
        }
        return String.join("、", missing);
    }

    /**
     * 行级处理：DQ 校验 + 批次内去重 + 存量匹配 → 四类分流
     *
     * @param job       已入库的任务
     * @param template  模板定义
     * @param mappings  字段映射
     * @param collector 行采集器
     * @return 分流统计
     */
    private Outcome classifyRows(CmdImportJob job, CmdImportTemplate template,
                                 List<CmdImportTemplateMapping> mappings, RowCollector collector) {
        int dataStartRow = template.getDataStartRow() == null || template.getDataStartRow() < 2
            ? 2 : template.getDataStartRow();
        String nameColumn = columnNameOf(mappings, FIELD_LEGAL_NAME);
        String codeColumn = columnNameOf(mappings, FIELD_CREDIT_CODE);

        // 批次内去重索引：规范化值 → 首次出现的 Excel 行号
        Map<String, Integer> batchCodes = new HashMap<>();
        Map<String, Integer> batchNames = new HashMap<>();

        int exact = 0;
        int suspected = 0;
        int created = 0;
        int invalid = 0;
        int index = 0;
        for (Map<Integer, Object> row : collector.getRows()) {
            int rowNo = dataStartRow + index;
            String legalName = trimToNull(collector.value(row, nameColumn));
            String creditCode = trimToNull(collector.value(row, codeColumn));

            // 1) 行级 DQ：必填列（is_required=Y）缺失 + 信用代码格式
            List<String> errors = new ArrayList<>();
            for (CmdImportTemplateMapping mapping : mappings) {
                if (CmdConstants.YES.equals(mapping.getIsRequired())
                    && StringUtils.isBlank(collector.value(row, mapping.getColumnName()))) {
                    errors.add("必填列缺失：" + StringUtils.blankToDefault(mapping.getColumnName(), mapping.getFieldCode()));
                }
            }
            if (creditCode != null && !creditCode.toUpperCase(Locale.ROOT).matches(CREDIT_CODE_PATTERN)) {
                errors.add("信用代码格式错误（应为 18 位大写字母数字）");
            }

            // 2) 四类分流：Invalid > 批次内重复(Suspected) > 存量 Exact > 名称疑似(Suspected) > New
            String resultType;
            String rowStatus;
            String handling = null;
            String matchState = null;
            String matchOneId = null;
            BigDecimal matchScore = null;
            if (!errors.isEmpty()) {
                resultType = RESULT_INVALID;
                rowStatus = ROW_STATUS_FAILED;
                handling = HANDLING_FIX;
            } else if (creditCode != null && batchCodes.containsKey(norm(creditCode))) {
                resultType = RESULT_SUSPECTED;
                rowStatus = ROW_STATUS_GOVERNANCE;
                handling = HANDLING_GOVERNANCE;
                matchState = RESULT_SUSPECTED;
                errors.add("批次内与第 " + batchCodes.get(norm(creditCode)) + " 行信用代码重复");
            } else if (legalName != null && batchNames.containsKey(norm(legalName))) {
                resultType = RESULT_SUSPECTED;
                rowStatus = ROW_STATUS_GOVERNANCE;
                handling = HANDLING_GOVERNANCE;
                matchState = RESULT_SUSPECTED;
                errors.add("批次内与第 " + batchNames.get(norm(legalName)) + " 行客户名称重复");
            } else {
                CmdCustomer exactHit = creditCode == null ? null : matchByCreditCode(creditCode);
                if (exactHit != null) {
                    resultType = RESULT_EXACT;
                    rowStatus = ROW_STATUS_SUCCESS;
                    matchState = RESULT_EXACT;
                    matchOneId = exactHit.getOneId();
                    matchScore = BigDecimal.valueOf(100);
                } else {
                    CmdCustomer nameHit = legalName == null ? null : matchByLegalName(legalName);
                    if (nameHit != null) {
                        resultType = RESULT_SUSPECTED;
                        rowStatus = ROW_STATUS_GOVERNANCE;
                        handling = HANDLING_GOVERNANCE;
                        matchState = RESULT_SUSPECTED;
                        matchOneId = nameHit.getOneId();
                        matchScore = BigDecimal.valueOf(75);
                        errors.add("与存量主档名称相同（候选 " + nameHit.getOneId() + "），需治理确认");
                    } else {
                        resultType = RESULT_NEW;
                        rowStatus = ROW_STATUS_SUCCESS;
                        matchState = RESULT_NEW;
                    }
                }
            }

            if (creditCode != null) {
                batchCodes.putIfAbsent(norm(creditCode), rowNo);
            }
            if (legalName != null) {
                batchNames.putIfAbsent(norm(legalName), rowNo);
            }

            CmdImportRow entity = new CmdImportRow();
            entity.setJobId(job.getId());
            entity.setJobCode(job.getJobCode());
            entity.setRowNo(rowNo);
            entity.setRowStatus(rowStatus);
            entity.setResultType(resultType);
            entity.setOneId(matchOneId);
            entity.setBuScope(job.getBuScope());
            entity.setLegalName(legalName);
            entity.setCreditCode(creditCode);
            entity.setDqScore(dqScoreOf(errors, resultType));
            entity.setMatchState(matchState);
            entity.setMatchScore(matchScore);
            entity.setRawJson(JsonUtils.toJsonString(toNamedMap(collector, mappings, row, false)));
            entity.setParsedJson(JsonUtils.toJsonString(toNamedMap(collector, mappings, row, true)));
            entity.setErrorCount(errors.size());
            entity.setErrorSummary(errors.isEmpty() ? null : String.join("；", errors));
            entity.setHandling(handling);
            rowMapper.insert(entity);
            index++;

            switch (resultType) {
                case RESULT_EXACT -> exact++;
                case RESULT_SUSPECTED -> suspected++;
                case RESULT_NEW -> created++;
                default -> invalid++;
            }
        }
        return new Outcome(exact, suspected, created, invalid);
    }

    /**
     * 统计与状态回写（对应泳道图「批次任务详情」：Completed / Partial / Failed、数量与原因）
     *
     * @param job     任务
     * @param outcome 分流统计
     */
    private void applyOutcome(CmdImportJob job, Outcome outcome) {
        CmdImportJob patch = new CmdImportJob();
        patch.setId(job.getId());
        patch.setExactCount(outcome.exact);
        patch.setSuspectedCount(outcome.suspected);
        patch.setNewCount(outcome.created);
        patch.setReviewCount(0);
        patch.setInvalidCount(outcome.invalid);
        patch.setSuccessCount(outcome.exact + outcome.created);
        patch.setJobStatus(resolveJobStatus(outcome));
        patch.setProgress(100);
        patch.setEndTime(LocalDateTime.now());
        jobMapper.updateById(patch);
    }

    /**
     * 依据分流统计推导任务状态：全部 Invalid → Failed；部分 Invalid → 部分成功；有疑似/新建 → 待复核；否则完成
     *
     * @param outcome 分流统计
     * @return 任务状态
     */
    private String resolveJobStatus(Outcome outcome) {
        if (outcome.invalid > 0 && outcome.invalid == outcome.total()) {
            return STATUS_FAILED;
        }
        if (outcome.invalid > 0) {
            return STATUS_PARTIAL;
        }
        if (outcome.suspected > 0 || outcome.created > 0) {
            return STATUS_WAIT_REVIEW;
        }
        return STATUS_COMPLETED;
    }

    /**
     * 按行明细重算任务统计与状态（治理动作后调用）
     *
     * @param job 任务
     */
    private void recalcJobCounts(CmdImportJob job) {
        List<CmdImportRow> rows = rowMapper.selectList(
            Wrappers.<CmdImportRow>lambdaQuery().eq(CmdImportRow::getJobId, job.getId()));
        int exact = 0;
        int suspected = 0;
        int created = 0;
        int review = 0;
        int invalid = 0;
        for (CmdImportRow row : rows) {
            switch (StringUtils.defaultString(row.getResultType())) {
                case RESULT_EXACT -> exact++;
                case RESULT_SUSPECTED -> suspected++;
                case RESULT_NEW -> created++;
                case CmdConstants.MATCH_REVIEW -> review++;
                default -> invalid++;
            }
        }
        CmdImportJob patch = new CmdImportJob();
        patch.setId(job.getId());
        patch.setExactCount(exact);
        patch.setSuspectedCount(suspected);
        patch.setNewCount(created);
        patch.setReviewCount(review);
        patch.setInvalidCount(invalid);
        patch.setSuccessCount(exact + created);
        patch.setJobStatus(resolveJobStatus(new Outcome(exact, suspected, created + review, invalid)));
        jobMapper.updateById(patch);
    }

    // ================================ 审批发布联动 ================================

    /**
     * 存在 New 行时创建「批量导入确认」审批待办并启动流程实例
     * <p>
     * 流程实例启动失败不回滚导入（导入本身已成功落库），仅记录日志与任务备注，
     * 之后可在流程中心对 IMPORT_BATCH 场景手工重试。
     *
     * @param job     任务
     * @param outcome 分流统计
     */
    private void submitApproval(CmdImportJob job, Outcome outcome) {
        LocalDateTime now = LocalDateTime.now();
        Applicant applicant = resolveApplicant();

        CmdApprovalTask task = new CmdApprovalTask();
        task.setTaskNo(generateTaskNo());
        task.setTaskCategory(CmdConstants.APPR_CAT_APPROVAL);
        task.setBizType(CmdConstants.BIZ_TYPE_IMPORT);
        task.setBizId(job.getJobCode());
        task.setBizTitle("批量导入确认：" + job.getFileName());
        task.setSceneCode(SCENE_IMPORT_BATCH);
        task.setApplicantId(applicant.id());
        task.setApplicantName(applicant.name());
        task.setBuScope(job.getBuScope());
        task.setScope(CmdConstants.SCOPE_BU);
        task.setCurrentNodeCode("BU_REVIEW");
        task.setCurrentNodeName(NODE_NAME_BU_REVIEW);
        task.setAssigneeName(NAME_BU_STEWARD);
        task.setAssigneeRole(ROLE_BU_STEWARD);
        task.setStatus(CmdConstants.APPR_STATUS_PENDING);
        task.setRiskLevel(outcome.invalid > 0 ? "High" : "Medium");
        task.setDuplicateState(outcome.suspected > 0 ? CmdConstants.MATCH_SUSPECTED : CmdConstants.MATCH_NEW);
        task.setCrossBuFlag(CmdConstants.NO);
        task.setSubmitTime(now);
        task.setSlaDue(now.plusHours(resolveSlaHours()));
        task.setSlaState(CmdConstants.SLA_NORMAL);
        // 批次级 DQ：单个 New 行尚未入主档，页面 DQ 列展示本批次行级均分（总设计「批次任务详情：数量与原因」）
        task.setDqScore(batchAverageDq(job));
        task.setEvidenceJson(approvalEvidenceOf(job, outcome));
        approvalTaskMapper.insert(task);

        // 业务侧轨迹：提交批量导入确认
        CmdApprovalAction action = new CmdApprovalAction();
        action.setTaskId(task.getId());
        action.setTaskNo(task.getTaskNo());
        action.setActionType(CmdConstants.ACTION_SUBMIT);
        action.setActionName("提交批量导入确认");
        action.setFromNodeCode("APPLY");
        action.setOperatorId(applicant.id());
        action.setOperatorRole(ROLE_BU_STEWARD);
        action.setActionTime(now);
        action.setBeforeState(CmdConstants.APPR_STATUS_DRAFT);
        action.setAfterState(CmdConstants.APPR_STATUS_PENDING);
        approvalActionMapper.insert(action);

        // 启动 Warm-Flow 实例（防御式：引擎异常不影响导入结果）
        try {
            flowEngineService.startInstance(task.getTaskNo());
        } catch (Exception e) {
            log.error("[CMD][IMPORT] 批量导入确认流启动失败：taskNo={}", task.getTaskNo(), e);
            CmdImportJob patch = new CmdImportJob();
            patch.setId(job.getId());
            patch.setRemark(appendRemark(job.getRemark(), "流程实例启动失败：" + e.getMessage()));
            jobMapper.updateById(patch);
        }
    }

    /**
     * 审批通过：为全部 New 行生成客户主档（One ID + active）
     *
     * @param job 任务
     * @return 生成条数
     */
    private int publishNewRows(CmdImportJob job) {
        List<CmdImportRow> rows = rowMapper.selectList(Wrappers.<CmdImportRow>lambdaQuery()
            .eq(CmdImportRow::getJobId, job.getId())
            .eq(CmdImportRow::getResultType, RESULT_NEW));
        int count = 0;
        for (CmdImportRow row : rows) {
            CmdCustomer customer = new CmdCustomer();
            customer.setOneId(generateOneId());
            customer.setLegalName(StringUtils.blankToDefault(row.getLegalName(), "未命名导入客户 " + row.getRowNo()));
            customer.setCreditCode(row.getCreditCode());
            customer.setBuScope(row.getBuScope());
            customer.setCountry("中国");
            customer.setStatus(CmdConstants.CUST_STATUS_ACTIVE);
            customer.setSourceSystem("IMPORT");
            customer.setSourceId(job.getJobCode());
            customer.setDqScore(row.getDqScore());
            customer.setMatchState(RESULT_NEW);
            customer.setDuplicateFlag(CmdConstants.NO);
            customer.setRemark("批量导入生成：" + job.getJobCode() + " 第 " + row.getRowNo() + " 行");
            customerMapper.insert(customer);

            CmdImportRow patch = new CmdImportRow();
            patch.setId(row.getId());
            patch.setOneId(customer.getOneId());
            patch.setRemark("审批通过，已生成 One ID " + customer.getOneId());
            rowMapper.updateById(patch);
            count++;
        }
        return count;
    }

    // ================================ 查询 / 匹配辅助 ================================

    /**
     * 按任务编号查询任务
     *
     * @param jobCode 任务编号
     * @return 任务实体
     */
    private CmdImportJob getJobByCode(String jobCode) {
        CmdImportJob job = jobMapper.selectOne(
            Wrappers.<CmdImportJob>lambdaQuery().eq(CmdImportJob::getJobCode, jobCode).last("limit 1"));
        if (job == null) {
            throw new ServiceException("导入任务不存在：{}", jobCode);
        }
        return job;
    }

    /**
     * 存量精确匹配：统一社会信用代码 + active 主档
     *
     * @param creditCode 信用代码
     * @return 命中的主档（可空）
     */
    private CmdCustomer matchByCreditCode(String creditCode) {
        return customerMapper.selectOne(Wrappers.<CmdCustomer>lambdaQuery()
            .eq(CmdCustomer::getCreditCode, creditCode)
            .eq(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_ACTIVE)
            .last("limit 1"));
    }

    /**
     * 存量名称匹配：客户名称相同（不限状态，作为疑似候选证据）
     *
     * @param legalName 客户名称
     * @return 命中的主档（可空）
     */
    private CmdCustomer matchByLegalName(String legalName) {
        return customerMapper.selectOne(Wrappers.<CmdCustomer>lambdaQuery()
            .eq(CmdCustomer::getLegalName, legalName)
            .last("limit 1"));
    }

    /**
     * 按字段编码取对应的 Excel 列名
     *
     * @param mappings  字段映射
     * @param fieldCode 字段编码
     * @return 列名，未配置时返回 null
     */
    private String columnNameOf(List<CmdImportTemplateMapping> mappings, String fieldCode) {
        return mappings.stream()
            .filter(mapping -> fieldCode.equals(mapping.getFieldCode()))
            .map(CmdImportTemplateMapping::getColumnName)
            .findFirst()
            .orElse(null);
    }

    /**
     * 行数据按模板列（或字段编码）整理为有序 Map，供 raw_json / parsed_json 落库
     *
     * @param collector 行采集器
     * @param mappings  字段映射
     * @param row       Excel 原始行
     * @param byField   true 用字段编码作 key，false 用 Excel 列名作 key
     * @return 有序 Map
     */
    private Map<String, Object> toNamedMap(RowCollector collector, List<CmdImportTemplateMapping> mappings,
                                           Map<Integer, Object> row, boolean byField) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (CmdImportTemplateMapping mapping : mappings) {
            String key = byField
                ? mapping.getFieldCode()
                : StringUtils.blankToDefault(mapping.getColumnName(), mapping.getFieldCode());
            map.put(key, collector.value(row, mapping.getColumnName()));
        }
        return map;
    }

    /**
     * 行级质量分：100 起步，每个问题 -20，Invalid 保底 40（与单条申请 DQ 口径区分：导入按行打分）
     *
     * @param errors     问题列表
     * @param resultType 分流结果
     * @return 质量分
     */
    private BigDecimal dqScoreOf(List<String> errors, String resultType) {
        int score = Math.max(0, 100 - errors.size() * 20);
        if (RESULT_INVALID.equals(resultType)) {
            score = Math.max(score, 40);
        }
        return BigDecimal.valueOf(score);
    }

    /**
     * 上传文件落盘（目录由 cmd.poc.import-upload-path 配置）
     *
     * @param file             上传文件
     * @param originalFilename 原始文件名
     * @return 落盘后的文件路径
     */
    private Path saveFile(MultipartFile file, String originalFilename) {
        try {
            Path dir = Paths.get(uploadPath);
            Files.createDirectories(dir);
            Path target = dir.resolve(IdUtil.fastSimpleUUID() + "_" + originalFilename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new ServiceException("上传文件保存失败：" + e.getMessage());
        }
    }

    /**
     * 解析 Excel：读取表头（列名 → 列索引）与全部数据行
     *
     * @param file     已落盘的文件
     * @param template 模板定义（决定表头行数）
     * @return 行采集器
     */
    private RowCollector readRows(Path file, CmdImportTemplate template) {
        RowCollector collector = new RowCollector();
        int headRowNumber = template.getHeaderRow() == null || template.getHeaderRow() < 1 ? 1 : template.getHeaderRow();
        try (InputStream is = Files.newInputStream(file)) {
            FesodSheet.read(is)
                .headRowNumber(headRowNumber)
                .autoCloseStream(true)
                .sheet()
                .registerReadListener(collector)
                .doRead();
        } catch (IOException e) {
            throw new ServiceException("上传文件解析失败：" + e.getMessage());
        }
        return collector;
    }

    /**
     * 构造导入任务（文件级）
     *
     * @return 待入库的任务实体
     */
    private CmdImportJob buildJob(MultipartFile file, CmdImportTemplate template, String originalFilename,
                                  Path saved, int rowCount, String errorStrategy, String duplicateStrategy,
                                  String scene, String buScope, String sourceSystem) {
        CmdImportJob job = new CmdImportJob();
        job.setJobCode("IMP-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        job.setJobName(template.getTemplateName() + " 批量导入");
        // 业务上下文（设计节点「新建导入任务」）：弹窗选择优先，缺省回落模板定义
        job.setScene(StringUtils.blankToDefault(scene, template.getScene()));
        job.setBuScope(StringUtils.blankToDefault(buScope, template.getBuScope()));
        job.setTemplateId(template.getId());
        job.setTemplateCode(template.getTemplateCode());
        job.setTemplateVersion(template.getVersionNo());
        job.setFileName(originalFilename);
        job.setFilePath(saved.toString());
        job.setFileSize(file.getSize());
        job.setTotalCount(rowCount);
        job.setSuccessCount(0);
        job.setJobStatus(STATUS_WAIT_REVIEW);
        job.setProgress(0);
        String remark = "错误策略：" + StringUtils.blankToDefault(errorStrategy, STRATEGY_REJECT)
            + "；重复策略：" + StringUtils.blankToDefault(duplicateStrategy, "GOVERNANCE");
        if (StringUtils.isNotBlank(sourceSystem)) {
            remark += "；来源系统：" + sourceSystem;
        }
        job.setRemark(remark);
        job.setSubmitBy(resolveApplicant().name());
        job.setSubmitTime(LocalDateTime.now());
        return job;
    }

    /**
     * 生成五路分流策略（展示文案与统计数字联动）
     *
     * @param vo 结果对象（已填充统计数字）
     * @return 分流策略列表
     */
    private List<CmdImportResultVo.ImportRouteVo> buildRoutes(CmdImportResultVo vo) {
        List<CmdImportResultVo.ImportRouteVo> routes = new ArrayList<>();
        routes.add(route("Exact", "关联已有One ID", "System", vo.getExact()));
        routes.add(route("Suspected", "进入人工治理", "BU/GC Steward", vo.getSuspected()));
        routes.add(route("Review", "规则或业务复核", "BU Steward", vo.getReview()));
        routes.add(route("New", "审批后生成One ID", "Steward", vo.getCreated()));
        routes.add(route("Invalid", "返回修复", "Business User", vo.getInvalid()));
        return routes;
    }

    private CmdImportResultVo.ImportRouteVo route(String result, String handling, String owner, Integer count) {
        CmdImportResultVo.ImportRouteVo item = new CmdImportResultVo.ImportRouteVo();
        item.setResult(result);
        item.setHandling(handling);
        item.setOwner(owner);
        item.setDetail("查看" + count + "条");
        return item;
    }

    /**
     * 记录上传导入的审计事件（谁、何时、导入了什么文件、分流结果如何）
     *
     * @param job     任务
     * @param outcome 分流统计
     */
    private void recordImportAudit(CmdImportJob job, Outcome outcome) {
        try {
            Applicant applicant = resolveApplicant();
            AuditEvent audit = new AuditEvent();
            audit.setEventType("IMPORT");
            audit.setEventName("批量导入上传：" + job.getFileName());
            audit.setBizType(CmdConstants.BIZ_TYPE_IMPORT);
            audit.setBizId(job.getJobCode());
            audit.setOperatorId(applicant.id());
            audit.setOperatorName(applicant.name());
            audit.setOperatorRole(ROLE_BU_STEWARD);
            audit.setEventTime(LocalDateTime.now());
            audit.setResult("SUCCESS");
            audit.setRiskLevel(outcome.invalid > 0 ? "High" : "Low");
            audit.setAfterJson("{\"total\":" + outcome.total() + ",\"exact\":" + outcome.exact
                + ",\"suspected\":" + outcome.suspected + ",\"new\":" + outcome.created
                + ",\"invalid\":" + outcome.invalid + "}");
            auditService.record(audit);
        } catch (Exception e) {
            // 审计失败不影响导入主流程，但必须留痕便于排查
            log.warn("[CMD][IMPORT] 导入审计记录失败：{}", job.getJobCode(), e);
        }
    }

    /**
     * 记录行级治理动作的审计事件
     *
     * @param job  任务
     * @param row  行明细
     * @param type 动作类型
     * @param note 处理说明
     */
    private void recordGovernanceAudit(CmdImportJob job, CmdImportRow row, String type, String note) {
        try {
            Applicant applicant = resolveApplicant();
            AuditEvent audit = new AuditEvent();
            audit.setEventType("IMPORT");
            audit.setEventName("导入行治理[" + type + "]：" + row.getLegalName() + "（" + note + "）");
            audit.setBizType(CmdConstants.BIZ_TYPE_IMPORT);
            audit.setBizId(job.getJobCode());
            audit.setOneId(row.getOneId());
            audit.setOperatorId(applicant.id());
            audit.setOperatorName(applicant.name());
            audit.setEventTime(LocalDateTime.now());
            audit.setResult("SUCCESS");
            audit.setBeforeJson("{\"resultType\":\"" + StringUtils.defaultString(row.getResultType(), "") + "\"}");
            audit.setAfterJson("{\"action\":\"" + type + "\"}");
            auditService.record(audit);
        } catch (Exception e) {
            log.warn("[CMD][IMPORT] 治理审计记录失败：{} row={}", job.getJobCode(), row.getId(), e);
        }
    }

    /**
     * 审批待办治理证据（审批详情页展示四类分流统计）
     *
     * @param job     任务
     * @param outcome 分流统计
     * @return JSON 字符串
     */
    private String approvalEvidenceOf(CmdImportJob job, Outcome outcome) {
        Map<String, Object> evidence = new LinkedHashMap<>(10);
        evidence.put("批次号", job.getJobCode());
        evidence.put("导入文件", job.getFileName());
        evidence.put("总行数", outcome.total());
        evidence.put("Exact", outcome.exact);
        evidence.put("Suspected", outcome.suspected);
        evidence.put("New", outcome.created);
        evidence.put("Invalid", outcome.invalid);
        evidence.put("处理方式", "Exact 关联已有 One ID；New 审批后生成 One ID；Suspected 进入治理；Invalid 退回修复");
        evidence.put("待办", "BU Scope 初审：核对批次分流结果，批准后为 " + outcome.created + " 条 New 记录生成 One ID");
        return JsonUtils.toJsonString(evidence);
    }

    /**
     * 批次行级 DQ 均分
     * <p>
     * 批量导入是批次级审批：批次自身没有 One ID 也没有单条主档，因此页面 DQ 列
     * 展示本批次全部行的质量分均分（总设计「批次任务详情：Completed / Partial /
     * Failed、数量、原因与待办」），避免审批列表出现整列空值。
     *
     * @param job 导入任务
     * @return 均分（无有效分值时返回 null，页面回退为「-」）
     */
    private BigDecimal batchAverageDq(CmdImportJob job) {
        List<CmdImportRow> rows = rowMapper.selectList(Wrappers.<CmdImportRow>lambdaQuery()
            .eq(CmdImportRow::getJobId, job.getId()));
        BigDecimal sum = BigDecimal.ZERO;
        int counted = 0;
        for (CmdImportRow row : rows) {
            if (row.getDqScore() != null) {
                sum = sum.add(row.getDqScore());
                counted++;
            }
        }
        return counted == 0 ? null : sum.divide(BigDecimal.valueOf(counted), 0, RoundingMode.HALF_UP);
    }

    /**
     * 生成审批待办编号（AP-yyyyMMdd-0001，与客户新建申请同一序列空间）
     *
     * @return 任务编号
     */
    private String generateTaskNo() {
        String prefix = "AP-" + LocalDate.now().format(TASK_NO_DAY) + "-";
        for (int seq = 1; seq <= 9999; seq++) {
            String taskNo = prefix + String.format("%04d", seq);
            Long exists = approvalTaskMapper.lambda().eq(CmdApprovalTask::getTaskNo, taskNo).count();
            if (exists == null || exists == 0) {
                return taskNo;
            }
        }
        throw new ServiceException("申请编号生成失败：当日流水号已用尽");
    }

    /**
     * 解析提交人 / 申请人：优先当前登录人，POC 免登录取不到时使用演示账号
     *
     * @return 申请人（ID + 姓名）
     */
    private Applicant resolveApplicant() {
        try {
            if (LoginHelper.isLogin()) {
                return new Applicant(LoginHelper.getUserId(),
                    StringUtils.blankToDefault(LoginHelper.getUsername(), DEMO_APPLICANT_NAME));
            }
        } catch (Exception e) {
            log.debug("[CMD][IMPORT] 未获取到登录人，使用演示申请人：{}", e.getMessage());
        }
        return new Applicant(DEMO_APPLICANT_ID, DEMO_APPLICANT_NAME);
    }

    /**
     * 读取批量导入确认场景的 SLA 时长（cmd_flow_scene.sla_hours）
     *
     * @return SLA 时长（小时）
     */
    private long resolveSlaHours() {
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(SCENE_IMPORT_BATCH);
        Object hours = scene == null ? null : scene.get("sla_hours");
        return hours instanceof Number number ? number.longValue() : DEFAULT_SLA_HOURS;
    }

    /**
     * 生成 One ID（与客户新建同一规则：GC- 八位随机码，生成后永不变更）
     *
     * @return One ID
     */
    private String generateOneId() {
        return "GC-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    private String appendRemark(String remark, String addition) {
        return StringUtils.isBlank(remark) ? addition : remark + "；" + addition;
    }

    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer nvl(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 行级处理结果统计
     */
    private record Outcome(int exact, int suspected, int created, int invalid) {
        int total() {
            return exact + suspected + created + invalid;
        }
    }

    /**
     * 提交人 / 申请人（登录人 / 演示账号）
     */
    private record Applicant(Long id, String name) {
    }

    /**
     * Excel 行采集器：记录表头（规范化列名 → 列索引）与全部数据行。
     * <p>
     * 之所以按「列名」取值而不是按列序号，是为了兼容不同版本的列索引基准，
     * 同时天然校验上传文件表头与模板是否一致。
     */
    private static final class RowCollector extends AnalysisEventListener<Map<Integer, Object>> {

        /** 数据行（列索引 → 单元格值） */
        private final List<Map<Integer, Object>> rows = new ArrayList<>();

        /** 表头（规范化列名 → 列索引） */
        private final Map<String, Integer> nameToIndex = new LinkedHashMap<>();

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                nameToIndex.put(normalize(entry.getValue()), entry.getKey());
            }
        }

        @Override
        public void invoke(Map<Integer, Object> data, AnalysisContext context) {
            rows.add(data);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 解析完成，无需额外处理
        }

        /**
         * 表头是否包含指定列
         *
         * @param columnName 列名
         * @return true 包含
         */
        private boolean hasColumn(String columnName) {
            return columnName != null && nameToIndex.containsKey(normalize(columnName));
        }

        /**
         * 按 Excel 列名取当前行的单元格值
         *
         * @param row        数据行
         * @param columnName 列名
         * @return 单元格值，未找到返回 null
         */
        private String value(Map<Integer, Object> row, String columnName) {
            Integer index = nameToIndex.get(normalize(columnName));
            if (index == null) {
                return null;
            }
            Object value = row.get(index);
            return value == null ? null : String.valueOf(value);
        }

        /**
         * 列名规范化：去空格 + 转小写，避免大小写与空格差异导致匹配失败
         */
        private static String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private List<Map<Integer, Object>> getRows() {
            return rows;
        }
    }
}
