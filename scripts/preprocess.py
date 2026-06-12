#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
phone-analysis 本机一次性预处理：

    data/source-phone.xlsx
        ↓ 清洗 + 列名英化 + 单位字段拆分
    data/processed/phone.csv      (UTF-8-SIG，供人/Excel 直接查看)
    data/processed/phone.parquet  (snappy 压缩，供 Spark 高效读取)

设计纪律
--------
1. 列名全部转 snake_case 英文；中英映射写在 column_mapping.py，供 SpringBoot / Vue
   在展示时反查中文。
2. 五个「规格 + 单位」字段在本脚本内全部拆为数值（业务统一口径）：
       摄像头像素 "5400万"   -> camera_pixel_wan        (int, 万)
       电池续航   "4500mAh"  -> battery_capacity_mah   (int, mAh)
       屏幕尺寸   "6.36英寸" -> screen_size_inch       (float, 英寸)
       存储容量   "256GB"    -> storage_gb             (int, GB)
       屏幕刷新率 "120Hz"    -> refresh_rate_hz        (int, Hz)
3. 不在本脚本做任何派生计算（营收/毛利/毛利率）；ods 层贴原始，
   派生交由 Spark dwd/dws/ads 层负责。
4. CSV 与 Parquet 列名 / 顺序完全一致，便于 Spark schema 与本机查看对齐。

运行
----
    uv venv .venv --python 3.11
    uv pip install --python .venv/bin/python -r scripts/requirements.txt
    .venv/bin/python scripts/preprocess.py
"""
from __future__ import annotations

import logging
import re
import sys
from pathlib import Path

import pandas as pd

# 让脚本既能 `python scripts/preprocess.py` 也能模块化导入
sys.path.insert(0, str(Path(__file__).resolve().parent))
from column_mapping import COLUMN_CN_TO_EN, ORDERED_EN_COLUMNS  # noqa: E402

# ---------------------------------------------------------------------------
# 路径常量（本脚本只在本机跑，硬路径无伤大雅；如需配置化可改读 env）
# ---------------------------------------------------------------------------
ROOT = Path(__file__).resolve().parent.parent
SRC_XLSX = ROOT / "data" / "source-phone.xlsx"
OUT_DIR = ROOT / "data" / "processed"
OUT_CSV = OUT_DIR / "phone.csv"
OUT_PARQUET = OUT_DIR / "phone.parquet"
OUT_SCHEMA = OUT_DIR / "phone.schema.txt"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
log = logging.getLogger("preprocess")


# ---------------------------------------------------------------------------
# 单位字段拆分
# ---------------------------------------------------------------------------
_NUMERIC_RE = re.compile(r"-?\d+(?:\.\d+)?")


def _extract_number(value, as_int: bool):
    """从形如 '5400万' / '4500mAh' / '6.36英寸' 的字符串里抽出第一个数字。"""
    if value is None:
        return None
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return int(value) if as_int else float(value)
    text = str(value).strip()
    if not text or text.lower() == "nan":
        return None
    m = _NUMERIC_RE.search(text.replace(",", ""))
    if not m:
        return None
    try:
        return int(float(m.group())) if as_int else float(m.group())
    except ValueError:
        return None


def split_spec_units(df: pd.DataFrame) -> pd.DataFrame:
    """把五个「规格+单位」中文字段一次性替换为英文数值列。"""
    # 原始中文字段 → (英文新列, 是否 int)
    rules = [
        ("摄像头像素",   "camera_pixel_wan",     True),
        ("电池续航",     "battery_capacity_mah", True),
        ("屏幕尺寸",     "screen_size_inch",     False),
        ("存储容量",     "storage_gb",           True),
        ("屏幕刷新率",   "refresh_rate_hz",      True),
    ]
    for cn, en, as_int in rules:
        if cn not in df.columns:
            log.warning("源数据缺列：%s，跳过", cn)
            continue
        df[en] = df[cn].apply(lambda v: _extract_number(v, as_int))
        # int 列若有 NaN 在 pandas 里只能用 nullable Int；保持简单：fillna(0) 由数据决定
        # 这里选择保留 NaN，由 Spark schema 兜底为 nullable
        del df[cn]
    return df


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main() -> None:
    if not SRC_XLSX.exists():
        sys.exit(f"源文件不存在: {SRC_XLSX}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    log.info("读 Excel: %s", SRC_XLSX)
    df = pd.read_excel(SRC_XLSX, sheet_name=0, dtype=object)  # 全 object 避免 pandas 自动推断把字符串截断
    log.info("原始 shape = %s", df.shape)

    # 1) 去重 + 去全空行
    df = df.drop_duplicates().dropna(how="all").reset_index(drop=True)
    log.info("去重去空后 shape = %s", df.shape)

    # 2) 单位字段拆分（必须在重命名前做，rules 用中文键）
    df = split_spec_units(df)
    # split_spec_units 用 .apply 写入，pyarrow 会把混合 int/None 的 object 列
    # 推断为 INT64；Hive DDL 声明的是 INT（INT32），Spark 3 严格不允许
    # INT64→INT32 narrow，启动期会抛 SchemaColumnConvertNotSupportedException。
    # 这里统一用 pandas nullable Int32（保留 NaN 语义，pyarrow 落盘为 INT32）。
    spec_int_cols = [
        "camera_pixel_wan", "battery_capacity_mah",
        "storage_gb", "refresh_rate_hz",
    ]
    for c in spec_int_cols:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce").round().astype("Int32")

    # 3) 其余中文列名 → 英文
    rename_map = {cn: en for cn, en in COLUMN_CN_TO_EN.items() if cn in df.columns}
    df = df.rename(columns=rename_map)

    # 4) 类型规整
    #    日期
    if "sale_date" in df.columns:
        df["sale_date"] = pd.to_datetime(df["sale_date"], errors="coerce").dt.date
    #    数值列：销售/成本/配件/延保 一律 float64；
    #    用户评价/销量/客单价/用户年龄/总延保服务额 → Int32
    #    （历史上写过 Int64，但 Hive DDL 全部声明 INT；Int32 与 INT 一一对应，
    #     避免 Spark 3 启动期 INT64→INT 的转换报错。值域均 << 2^31，无溢出风险。）
    int_cols = [
        "user_rating", "unit_price", "sales_qty",
        "warranty_total_sales", "user_age",
    ]
    for c in int_cols:
        if c in df.columns:
            # 先转 float，再 round，最后 Int32（容忍源数据里偶发的 .0 / NaN）
            df[c] = pd.to_numeric(df[c], errors="coerce").round().astype("Int32")

    float_cols = [
        "production_cost", "marketing_cost_total", "logistics_cost",
        "platform_commission_total", "after_sales_cost",
        "case_sales", "earphone_sales", "charger_sales",
        "cable_sales", "screen_protector_sales", "accessory_sales_total",
        "warranty_1y_sales", "warranty_2y_sales",
        "accident_insurance_sales", "screen_insurance_sales",
    ]
    for c in float_cols:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce").astype("float64")

    str_cols = [
        "brand", "model", "processor", "os", "promotion",
        "user_city", "user_gender", "user_member_level",
    ]
    for c in str_cols:
        if c in df.columns:
            df[c] = df[c].astype("string").str.strip()

    # 5) 按事先定好的「最终列顺序」重排；多余列丢弃但打 warning
    missing = [c for c in ORDERED_EN_COLUMNS if c not in df.columns]
    if missing:
        log.warning("最终列顺序中缺失: %s（将忽略）", missing)
    extra = [c for c in df.columns if c not in ORDERED_EN_COLUMNS]
    if extra:
        log.warning("源数据有未在映射中的列: %s（将丢弃）", extra)
    final_cols = [c for c in ORDERED_EN_COLUMNS if c in df.columns]
    df = df[final_cols]

    # 6) 落盘
    log.info("写 CSV: %s", OUT_CSV)
    df.to_csv(
        OUT_CSV,
        index=False,
        encoding="utf-8-sig",     # BOM：Excel 直接打开不乱码
        date_format="%Y-%m-%d",
    )
    log.info("写 Parquet: %s", OUT_PARQUET)
    df.to_parquet(OUT_PARQUET, engine="pyarrow", compression="snappy", index=False)

    # 7) 输出 schema 备查（给 Hive DDL 与 Spark schema 当参照）
    lines = [f"shape: {df.shape}", "columns:"]
    for c in df.columns:
        lines.append(f"  {c}\t{df[c].dtype}")
    OUT_SCHEMA.write_text("\n".join(lines), encoding="utf-8")
    log.info("写 schema: %s", OUT_SCHEMA)

    log.info("完成。最终 shape = %s", df.shape)


if __name__ == "__main__":
    main()
