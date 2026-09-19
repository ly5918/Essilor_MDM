package org.dromara.cmd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.cmd.domain.MatchRule;

/**
 * 匹配规则 Mapper
 *
 * @author Essilor CMD POC
 */
@Mapper
public interface MatchRuleMapper extends BaseMapper<MatchRule> {
}