package com.geosaa.modules.content.geo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单项 GEO 战术评分。
 *
 * <p>对应 Princeton KDD 2024（arXiv:2311.09735）九大战术中的某一项。
 * score 为 0-100，detail 为可读的判定说明。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacticScore {

    /** 战术编码，与 {@link GeoContentValidator#TACTIC_CODES} 保持一致 */
    private String code;

    /** 战术中文名 */
    private String name;

    /** 0-100 分 */
    private int score;

    /** 判定说明（命中次数 / 密度等） */
    private String detail;
}
