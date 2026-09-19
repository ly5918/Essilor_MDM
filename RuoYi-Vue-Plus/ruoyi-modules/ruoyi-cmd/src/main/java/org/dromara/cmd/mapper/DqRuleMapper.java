package org.dromara.cmd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.cmd.domain.DqRule;

/**
 * DQ规则 Mapper
 *
 * @author Essilor CMD POC
 */
@Mapper
public interface DqRuleMapper extends BaseMapper<DqRule> {
}