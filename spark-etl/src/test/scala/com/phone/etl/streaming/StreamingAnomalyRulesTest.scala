package com.phone.etl.streaming

import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

/**
 * 规则集单测：用本地 SparkSession 跑几条样本，验证 5 条规则的触发与级别正确。
 *
 * 运行：
 *   mvn -pl spark-etl test
 *
 * 不依赖 Kafka / Hive，纯 DataFrame，CI 可跑。
 */
class StreamingAnomalyRulesTest extends AnyFunSuite with BeforeAndAfterAll {

  // 必须是 stable identifier（val）才能 `import spark.implicits._`
  // 用 lazy val 避免在类构造期就吃 SparkSession 的几秒开销
  private lazy val spark: SparkSession = SparkSession.builder()
    .master("local[2]")
    .appName("rules-test")
    .config("spark.sql.shuffle.partitions", "2")
    .getOrCreate()

  import spark.implicits._

  override def beforeAll(): Unit = {
    spark.sparkContext.setLogLevel("WARN")
  }

  override def afterAll(): Unit = {
    spark.stop()
  }

  /** 与 RawStreamingJob.parse 输出 schema 一致（snake_case） */
  case class Evt(
    event_id: String,
    brand: String, model: String, promotion: String,
    unit_price: Int, sales_qty: Int,
    production_cost: Double, marketing_cost_total: Double
  )

  test("big_loss 优先级最高 + level=high") {
    val df = Seq(Evt("e1", "Apple", "iP15", "电商", 4000, 5,
      production_cost = 20000, marketing_cost_total = 3000)).toDF()
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(rs.getAs[String]("alert_type")  === "big_loss")
    assert(rs.getAs[String]("alert_level") === "high")
    assert(rs.getAs[String]("alert_title").contains("亏损"))
  }

  test("marketing_burnout：营销费率 >40%") {
    val df = Seq(Evt("e1", "小米", "14U", "直播", 3000, 2,
      production_cost = 3000, marketing_cost_total = 3000)).toDF()
    // revenue=6000, marketing/revenue=0.5 → burnout
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(rs.getAs[String]("alert_type") === "marketing_burnout")
  }

  test("profit_anomaly：毛利率 <5%") {
    val df = Seq(Evt("e1", "vivo", "X100", "线下", 4000, 3,
      production_cost = 11800, marketing_cost_total = 100)).toDF()
    // revenue=12000, profit=100, margin=0.0083 → anomaly
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(rs.getAs[String]("alert_type")  === "profit_anomaly")
    assert(rs.getAs[String]("alert_level") === "mid")
  }

  test("price_outlier：单价 >12000") {
    val df = Seq(Evt("e1", "Apple", "iP15PM", "电商", 15000, 1,
      production_cost = 5000, marketing_cost_total = 500)).toDF()
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(rs.getAs[String]("alert_type")  === "price_outlier")
    assert(rs.getAs[String]("alert_level") === "low")
  }

  test("qty_spike：销量 >= 50（但会被更高优先级规则拦截）") {
    val df = Seq(Evt("e1", "华为", "P70", "线下", 5000, 80,
      production_cost = 200000, marketing_cost_total = 50000)).toDF()
    // 这条同时会满足 marketing_burnout / big_loss，应被高优先级拦截
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(Set("marketing_burnout", "big_loss").contains(rs.getAs[String]("alert_type")))
  }

  test("正常事件：alert_type 为 null") {
    val df = Seq(Evt("e1", "OPPO", "Find X7", "电商", 4500, 3,
      production_cost = 8000, marketing_cost_total = 1500)).toDF()
    // revenue=13500, profit=4000, margin=0.30, marketing/rev=0.11 → 全部合规
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(rs.getAs[String]("alert_type") == null)
  }

  test("派生字段：revenue / gross_profit / gross_margin / marketing_ratio 正确") {
    val df = Seq(Evt("e1", "X", "Y", "Z", 1000, 10,
      production_cost = 5000, marketing_cost_total = 1000)).toDF()
    val rs = StreamingAnomalyRules.tag(df).collect().head
    assert(rs.getAs[Double]("revenue")         === 10000.0)
    assert(rs.getAs[Double]("gross_profit")    === 4000.0)
    assert(rs.getAs[Double]("gross_margin")    === 0.4)
    assert(rs.getAs[Double]("marketing_ratio") === 0.1)
  }
}
