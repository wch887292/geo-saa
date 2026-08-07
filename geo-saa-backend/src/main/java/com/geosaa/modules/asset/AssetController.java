package com.geosaa.modules.asset;

import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 资产存证接口。
 *
 * <p>聚合 content / knowledge / distribute / diagnose 四类数据为统一资产视图，
 * 供前端「资产存证」页面使用。接口需登录态（与 statistics 一致，由安全配置统一拦截）。
 */
@RestController
@RequestMapping("/api/v1/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(assetService.getOverview());
    }

    @GetMapping("/list")
    public PageResult<Map<String, Object>> list(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return assetService.listAssets(assetType, year, month, pageNum, pageSize);
    }
}
