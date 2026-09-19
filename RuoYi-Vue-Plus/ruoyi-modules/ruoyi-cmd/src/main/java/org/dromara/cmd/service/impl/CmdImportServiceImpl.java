package org.dromara.cmd.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.dromara.cmd.domain.CmdImportJob;
import org.dromara.cmd.domain.CmdImportRow;
import org.dromara.cmd.domain.CmdImportTemplate;
import org.dromara.cmd.domain.CmdImportTemplateMapping;
import org.dromara.cmd.domain.bo.CmdImportJobBo;
import org.dromara.cmd.domain.vo.CmdImportJobVo;
import org.dromara.cmd.domain.vo.CmdImportResultVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateMappingVo;
import org.dromara.cmd.domain.vo.CmdImportTemplateVo;
import org.dromara.cmd.mapper.CmdImportJobMapper;
import org.dromara.cmd.mapper.CmdImportRowMapper;
import org.dromara.cmd.mapper.CmdImportTemplateMapper;
import org.dromara.cmd.mapper.CmdImportTemplateMappingMapper;
import org.dromara.cmd.service.ICmdImportService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 批量导入 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>任务编号由服务端生成：IMP- 前缀 + 八位随机码，避免前端伪造</li>
 *   <li>新建任务默认进入待复核（WAIT_REVIEW），统计数字由导入过程回写</li>
 *   <li>分流策略文案为平台级展示规则，POC 阶段在 buildRoutes 中按常量生成</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdImportServiceImpl implements ICmdImportService {

    /** 新建任务的初始状态：待复核 */
    private static final String STATUS_WAIT_REVIEW = "WAIT_REVIEW";

    /** 模板状态：已发布 */
    private static final String STATUS_PUBLISHED = "Published";

    /** 模板状态：草稿 */
    private static final String STATUS_DRAFT = "Draft";

    /** 错误策略：整行拒绝 */
    private static final String STRATEGY_REJECT = "Reject Row";

    /** 错误策略：告警放行 */
    private static final String STRATEGY_WARNING = "Warning Row";

    /** 上传后行的初始状态：待处理（DQ 与匹配在后续环节执行） */
    private static final String ROW_STATUS_PENDING = "PENDING";

    /** 上传后行的默认分流：新建（由 DQ / 匹配环节改写） */
    private static final String RESULT_TYPE_NEW = "NEW";

    /** 模板中「客户名称」对应的字段编码 */
    private static final String FIELD_LEGAL_NAME = "legal_name";

    /** 模板中「统一社会信用代码」对应的字段编码 */
    private static final String FIELD_CREDIT_CODE = "credit_code";

    private final CmdImportJobMapper jobMapper;
    private final CmdImportTemplateMapper templateMapper;
    private final CmdImportTemplateMappingMapper mappingMapper;
    private final CmdImportRowMapper rowMapper;

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
            throw new ServiceException("导入任务不存在：%s", jobCode);
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
    public void downloadTemplate(String templateCode, HttpServletResponse response) {
        CmdImportTemplate template = getTemplate(templateCode);
        List<CmdImportTemplateMapping> mappings = selectMappings(template.getTemplateCode());
        if (CollUtil.isEmpty(mappings)) {
            throw new ServiceException("模板[%s]未配置字段映射，无法生成模板文件", template.getTemplateName());
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
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet(sheetName)
                .doWrite(Collections.<List<String>>emptyList());
        } catch (IOException e) {
            throw new ServiceException("模板文件生成失败：" + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadJob(MultipartFile file, String templateCode, String errorStrategy, String duplicateStrategy) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的文件");
        }
        CmdImportTemplate template = getTemplate(templateCode);
        List<CmdImportTemplateMapping> mappings = selectMappings(template.getTemplateCode());

        String originalFilename = StringUtils.blankToDefault(file.getOriginalFilename(), "upload.xlsx");
        Path saved = saveFile(file, originalFilename);
        RowCollector collector = readRows(saved, template);

        CmdImportJob job = buildJob(file, template, originalFilename, saved,
            collector.getRows().size(), errorStrategy, duplicateStrategy);
        jobMapper.insert(job);
        saveRows(job, template, mappings, collector);
        return job.getJobCode();
    }

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
            throw new ServiceException("导入模板不存在：%s", templateCode);
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
                                  Path saved, int rowCount, String errorStrategy, String duplicateStrategy) {
        CmdImportJob job = new CmdImportJob();
        job.setJobCode("IMP-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        job.setJobName(template.getTemplateName() + " 批量导入");
        job.setScene(StringUtils.blankToDefault(template.getScene(), "DOOR"));
        job.setTemplateId(template.getId());
        job.setTemplateCode(template.getTemplateCode());
        job.setTemplateVersion(template.getVersionNo());
        job.setBuScope(template.getBuScope());
        job.setFileName(originalFilename);
        job.setFilePath(saved.toString());
        job.setFileSize(file.getSize());
        job.setTotalCount(rowCount);
        job.setSuccessCount(0);
        job.setJobStatus(STATUS_WAIT_REVIEW);
        job.setProgress(100);
        job.setSubmitBy("Business User");
        job.setSubmitTime(LocalDateTime.now());
        job.setStartTime(LocalDateTime.now());
        job.setEndTime(LocalDateTime.now());
        job.setRemark("错误策略：" + StringUtils.blankToDefault(errorStrategy, STRATEGY_REJECT)
            + "；重复策略：" + StringUtils.blankToDefault(duplicateStrategy, "GOVERNANCE"));
        return job;
    }

    /**
     * 逐行写入导入明细（行级）
     *
     * @param job       已入库的任务
     * @param template  模板定义
     * @param mappings  字段映射
     * @param collector 行采集器
     */
    private void saveRows(CmdImportJob job, CmdImportTemplate template,
                          List<CmdImportTemplateMapping> mappings, RowCollector collector) {
        int dataStartRow = template.getDataStartRow() == null || template.getDataStartRow() < 2
            ? 2 : template.getDataStartRow();
        String nameColumn = columnNameOf(mappings, FIELD_LEGAL_NAME);
        String codeColumn = columnNameOf(mappings, FIELD_CREDIT_CODE);
        int index = 0;
        for (Map<Integer, Object> row : collector.getRows()) {
            CmdImportRow entity = new CmdImportRow();
            entity.setJobId(job.getId());
            entity.setJobCode(job.getJobCode());
            entity.setRowNo(dataStartRow + index);
            entity.setRowStatus(ROW_STATUS_PENDING);
            entity.setResultType(RESULT_TYPE_NEW);
            entity.setBuScope(template.getBuScope());
            entity.setLegalName(collector.value(row, nameColumn));
            entity.setCreditCode(collector.value(row, codeColumn));
            entity.setRawJson(JsonUtils.toJsonString(toNamedMap(collector, mappings, row, false)));
            entity.setParsedJson(JsonUtils.toJsonString(toNamedMap(collector, mappings, row, true)));
            entity.setErrorCount(0);
            rowMapper.insert(entity);
            index++;
        }
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

    private Integer nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
