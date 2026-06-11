package com.phone.app.controller;

import com.phone.app.common.R;
import com.phone.app.entity.ColumnDict;
import com.phone.app.service.ColumnDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final ColumnDictService svc;

    /** 给前端一次拉走「英文 → 中文 + layer + category」全量映射 */
    @GetMapping("/columns")
    public R<Map<String, ColumnDict>> all() {
        return R.ok(svc.all());
    }

    /** 单查 */
    @GetMapping("/cn")
    public R<String> cn(@RequestParam String en) {
        return R.ok(svc.cn(en));
    }

    /** 强制刷新（如 ETL 刚跑完） */
    @PostMapping("/refresh")
    public R<Integer> refresh() {
        svc.refresh();
        return R.ok(svc.all().size());
    }
}
