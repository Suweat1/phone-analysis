package com.phone.app.service;

import com.phone.app.common.ColumnMapping;
import com.phone.app.entity.ColumnDict;
import com.phone.app.mapper.ColumnDictMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段中英映射查询：
 *   1) 优先从 MySQL `ads_column_dict` 取（Spark ColumnDictJob 写入）；
 *   2) 找不到则回退到 Java 内置 {@link ColumnMapping}（兜底，避免前端空白）。
 *
 * 结果在内存里短暂缓存（应用进程内 Map），首次查询后基本不再走数据库。
 * 看板字段集合稳定，无需复杂失效策略；如需热更新，可在 /api/dict/refresh 调用 {@link #refresh}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnDictService {

    private final ColumnDictMapper mapper;

    private volatile Map<String, ColumnDict> cache = null;

    public Map<String, ColumnDict> all() {
        if (cache == null) refresh();
        return cache;
    }

    public String cn(String en) {
        Map<String, ColumnDict> m = all();
        ColumnDict d = m.get(en);
        if (d != null && d.getColumnCn() != null) return d.getColumnCn();
        return ColumnMapping.EN_TO_CN.getOrDefault(en, en);
    }

    public synchronized void refresh() {
        Map<String, ColumnDict> next = new HashMap<>();
        try {
            List<ColumnDict> rows = mapper.selectList(null);
            rows.forEach(r -> next.put(r.getColumnEn(), r));
            log.info("ColumnDict refreshed from MySQL, size={}", next.size());
        } catch (Exception e) {
            log.warn("ColumnDict load from MySQL failed, fall back to in-memory mapping: {}",
                    e.getMessage());
        }
        // 兜底：把内置映射也合并进去（不覆盖 DB 的值）
        ColumnMapping.EN_TO_CN.forEach((en, cn) -> next.computeIfAbsent(en, k -> {
            ColumnDict d = new ColumnDict();
            d.setColumnEn(en);
            d.setColumnCn(cn);
            d.setLayer(ColumnMapping.EN_TO_LAYER.get(en));
            d.setCategory(ColumnMapping.EN_TO_CATEGORY.get(en));
            return d;
        }));
        this.cache = next;
    }
}
