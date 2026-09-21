package org.dromara.cmd.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 侧边导航统计 数据层（跨表聚合查询）
 * <p>
 * 用途：左侧菜单每一项右上角的「数据统计」角标。角标必须与点进去后页面里的
 * 数字对得上，因此这里的口径与各业务页面严格一致，且**全部由业务表实时聚合**，
 * 不维护冗余统计表（避免双写不一致）。
 * <p>
 * 为什么单独建一个 Mapper：这些统计是「跨表 JOIN」的口径（如待归位 = 客户主档 × 层级节点），
 * 放在任一业务 Mapper 里都会造成职责错位，故统一收敛到导航统计。
 *
 * @author Essilor CMD POC
 */
public interface CmdNavMapper {

    /**
     * 统计「待归位主数据」数量
     * <p>
     * 口径与「客户层级 → 待归位主数据」列表一致（见 ICmdHierarchyService#selectUnassignedList）：
     * 已审批通过成为 Golden Record（status=active），但层级节点表中
     * 「没有节点」或「节点还没挂父节点 / 仍是 UNASSIGNED」的主数据。
     *
     * @return 待归位数
     */
    @Select("SELECT COUNT(*) FROM cmd_customer c "
        + "LEFT JOIN cmd_hierarchy_node n ON n.one_id = c.one_id AND n.del_flag = '0' "
        + "WHERE c.del_flag = '0' AND c.status = 'active' "
        + "AND (n.id IS NULL OR n.parent_one_id IS NULL OR n.parent_one_id = '' OR n.hierarchy_type = 'UNASSIGNED')")
    Long countUnassignedCustomer();
}
