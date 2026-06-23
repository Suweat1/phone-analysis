package com.phone.app.controller;

import com.phone.app.common.R;
import com.phone.app.entity.*;
import com.phone.app.service.DashboardExtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 扩展看板（ads_ext_*）REST 接口。
 *
 * 与 {@link DashboardController}（业务 8 张表）保持同样的 {@code R<T>} 包装风格；
 * 单独挂在 {@code /dashboard/ext/*} 下，便于前端按板块归类与降级。
 */
@RestController
@RequestMapping("/dashboard/ext")
@RequiredArgsConstructor
public class DashboardExtController {

    private final DashboardExtService svc;

    /** 品牌大盘：饼图（营收占比）/ 雷达（5 维）/ 漏斗（毛利阶梯）共用此接口 */
    @GetMapping("/brand-summary")
    public R<List<BrandSummary>> brandSummary() { return R.ok(svc.brandSummary()); }

    /** 全局 KPI 仪表盘：4 个 gauge */
    @GetMapping("/kpi-gauge")
    public R<List<KpiGauge>> kpiGauge() { return R.ok(svc.kpiGauge()); }

    /** 品牌→机型 层级：Treemap / Sunburst 共用 */
    @GetMapping("/brand-model-tree")
    public R<List<BrandModelNode>> brandModelTree() { return R.ok(svc.brandModelTree()); }

    /** 日营收日历热力图 */
    @GetMapping("/calendar-heat")
    public R<List<CalendarHeat>> calendarHeat() { return R.ok(svc.calendarHeat()); }

    /** 品牌×月份 毛利率矩形热力图 */
    @GetMapping("/brand-month-heat")
    public R<List<BrandMonthHeat>> brandMonthHeat() { return R.ok(svc.brandMonthHeat()); }

    /** 桑基图：渠道→品牌→年龄段 */
    @GetMapping("/sales-sankey")
    public R<List<SankeyEdge>> salesSankey() { return R.ok(svc.salesSankey()); }

    /** 品牌客单价箱线 */
    @GetMapping("/brand-price-box")
    public R<List<BrandPriceBox>> brandPriceBox() { return R.ok(svc.brandPriceBox()); }
}
