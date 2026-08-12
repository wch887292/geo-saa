package com.geosaa.modules.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资产存证记录实体（O7：asset 独立数据模型）。
 *
 * <p>对应表 {@code asset_record}，为「资产存证」视图提供独立数据源，
 * 不再依赖 content/knowledge/distribute/diagnose 四表的内存聚合兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_record")
public class AssetRecord extends BaseEntity {

    /** 资产唯一键 */
    private String assetKey;

    /** 资产名称 */
    private String assetName;

    /** 资产类型 */
    private String assetType;

    /** 所属品牌 */
    private String brandName;

    /** 状态: 0=失效, 1=有效 */
    private Integer status;

    /** 统计值 */
    private Long statValue;

    /** 来源 */
    private String source;

    /** 备注 */
    private String remark;
}
