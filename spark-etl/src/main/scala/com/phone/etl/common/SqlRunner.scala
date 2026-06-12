package com.phone.etl.common

import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import scala.io.{Codec, Source}

/**
 * 从 classpath 读取 .sql 文件（支持多条 SQL，以 `;` 分隔）并依次执行。
 *
 * 约定：
 * - SQL 文件用 `-- ` 行注释（与各 DDL 风格一致），SqlRunner 会逐行剔除；
 * - 不支持 `/* */` 块注释，避免误处理含 `/` 的字符串字面量；
 * - 语句间 `;` 必须独占行尾或紧跟非空字符；
 * - `MSCK REPAIR TABLE` 等 DDL 类语句通过 spark.sql 直接执行即可。
 */
object SqlRunner {

  private val log = LoggerFactory.getLogger(getClass)

  /** 从 classpath 加载并执行。例：runFromResource(spark, "ddl/01-ods.sql") */
  def runFromResource(spark: SparkSession, resourcePath: String): Unit = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
      .getOrElse(throw new IllegalArgumentException(s"resource not found: $resourcePath"))

    // 手动 try/finally 管理资源（scala.util.Using 是 Scala 2.13+，本项目锁定 2.12）
    val src = Source.fromInputStream(stream)(Codec.UTF8)
    val raw = try src.mkString finally src.close()
    runScript(spark, raw, resourcePath)
  }

  /** 直接执行多语句脚本字符串 */
  def runScript(spark: SparkSession, script: String, label: String = "<inline>"): Unit = {
    val statements = splitStatements(script)
    log.info(s"[SqlRunner] $label 共 ${statements.size} 条语句")
    statements.zipWithIndex.foreach { case (sql, idx) =>
      log.info(s"[SqlRunner] $label [${idx + 1}/${statements.size}] ${oneLine(sql, max = 120)}")
      spark.sql(sql)
    }
  }

  /** 拆分：去注释 → 按 `;` 切 → trim 后过滤空串 */
  private[common] def splitStatements(script: String): Seq[String] = {
    val cleaned = script
      .split("\n")
      .map(stripLineComment)
      .mkString("\n")
    cleaned.split(";")
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSeq
  }

  /** 去掉 `--` 行注释，但保留字符串中的 `--` */
  private def stripLineComment(line: String): String = {
    var inStr = false
    var quote = ' '
    var i = 0
    while (i < line.length) {
      val c = line.charAt(i)
      if (inStr) {
        if (c == quote) inStr = false
      } else {
        if (c == '\'' || c == '"') {
          inStr = true; quote = c
        } else if (c == '-' && i + 1 < line.length && line.charAt(i + 1) == '-') {
          return line.substring(0, i)
        }
      }
      i += 1
    }
    line
  }

  private def oneLine(sql: String, max: Int): String = {
    val s = sql.replaceAll("\\s+", " ").trim
    if (s.length <= max) s else s.substring(0, max) + "..."
  }
}
