# data/processed/

存放本机 `scripts/preprocess.py` 的产物（parquet / csv）。

- 这些文件 **进 git**（数据量不大时）或走 git lfs；运行机 `git pull` 后由 `hdfs dfs -put` 上传到 HDFS `/phone-analysis/raw/`。
- 临时调试文件放 `_tmp/` 或以 `.tmp.csv` 结尾，已在 `.gitignore` 中过滤。
