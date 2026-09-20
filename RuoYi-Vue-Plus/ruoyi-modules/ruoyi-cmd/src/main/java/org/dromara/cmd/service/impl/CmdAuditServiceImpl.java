package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.bo.AuditEventBo;
import org.dromara.cmd.domain.bo.AuditExportBo;
import org.dromara.cmd.domain.vo.AuditEventVo;
import org.dromara.cmd.mapper.AuditEventMapper;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计中心 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>审计事件只追加不修改，事件编号服务端生成</li>
 *   <li>导出行为同样登记为一条审计事件，保证"谁在什么时候导了什么"可追溯</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdAuditServiceImpl implements ICmdAuditService {

    private final AuditEventMapper auditMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AuditEventVo> selectPage(AuditEventBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AuditEvent> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(bo.getEventType())) {
            lqw.eq(AuditEvent::getEventType, bo.getEventType());
        }
        if (StringUtils.isNotBlank(bo.getBizType())) {
            lqw.eq(AuditEvent::getBizType, bo.getBizType());
        }
        if (StringUtils.isNotBlank(bo.getOperatorRole())) {
            lqw.eq(AuditEvent::getOperatorRole, bo.getOperatorRole());
        }
        if (StringUtils.isNotBlank(bo.getOneId())) {
            lqw.eq(AuditEvent::getOneId, bo.getOneId());
        }
        if (StringUtils.isNotBlank(bo.getResult())) {
            lqw.eq(AuditEvent::getResult, bo.getResult());
        }
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String kw = bo.getKeyword().trim();
            // 贯通查询：事件编号 / 事件名称 / 操作人 / One ID / 关联业务单号
            lqw.and(w -> w.like(AuditEvent::getEventId, kw)
                .or().like(AuditEvent::getEventName, kw)
                .or().like(AuditEvent::getOperatorName, kw)
                .or().like(AuditEvent::getOneId, kw)
                .or().like(AuditEvent::getBizId, kw));
        }
        lqw.orderByDesc(AuditEvent::getEventTime);
        Page<AuditEventVo> page = auditMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String record(AuditEvent event) {
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now());
        }
        if (StringUtils.isBlank(event.getEventId())) {
            event.setEventId(generateEventId());
        }
        // audit_event 的 before_json / after_json 是 JSON 列：空串不是合法 JSON，必须落 NULL
        if (StringUtils.isBlank(event.getBeforeJson())) {
            event.setBeforeJson(null);
        }
        if (StringUtils.isBlank(event.getAfterJson())) {
            event.setAfterJson(null);
        }
        auditMapper.insert(event);
        return event.getEventId();
    }

    /**
     * 生成审计事件编号 AE-yyyyMMdd-####（当日流水，天然防重可读）
     *
     * @return 事件编号
     */
    private String generateEventId() {
        String day = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "AE-" + day + "-";
        LambdaQueryWrapper<AuditEvent> lqw = Wrappers.lambdaQuery();
        lqw.likeRight(AuditEvent::getEventId, prefix).orderByDesc(AuditEvent::getEventId).last("limit 1");
        AuditEvent last = auditMapper.selectOne(lqw);
        int seq = 1;
        if (last != null && StringUtils.isNotBlank(last.getEventId())) {
            String tail = last.getEventId().substring(prefix.length());
            try {
                seq = Integer.parseInt(tail) + 1;
            } catch (NumberFormatException ignore) {
                seq = 1;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String exportLog(AuditExportBo bo) {
        String exportCode = "EXP-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
        AuditEvent entity = new AuditEvent();
        entity.setEventId(exportCode);
        entity.setEventType("EXPORT");
        entity.setEventName("导出审计日志：范围=" + StringUtils.blankToDefault(bo.getRange(), "全部")
            + "，类型=" + StringUtils.blankToDefault(bo.getEventType(), "全部")
            + "，格式=" + StringUtils.blankToDefault(bo.getFormat(), "Excel"));
        entity.setBizType("AUDIT");
        entity.setOperatorName("Auditor");
        entity.setOperatorRole("AUDITOR");
        entity.setEventTime(LocalDateTime.now());
        entity.setResult("SUCCESS");
        entity.setRemark(StringUtils.blankToDefault(bo.getMasking(), "脱敏策略：默认"));
        auditMapper.insert(entity);
        return exportCode;
    }
}
