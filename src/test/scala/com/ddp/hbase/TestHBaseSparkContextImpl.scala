package com.ddp.hbase

import java.util.Properties

import org.apache.spark.sql.SparkSession

class TestHBaseSparkContextImpl extends Testing {
  var fixture : HBaseSparkContextImpl = _

  override def beforeAll(): Unit = {
    val spark = SparkSession.builder().appName("localtest").master("local[*]").getOrCreate
    val properties = new Properties()
    fixture = HBaseSparkContext(spark.sparkContext, properties)
  }


  it("test regex") {
    val pattern = "\\{([a-zA-Z_$][a-zA-Z0-9_$]*)\\}".r
    val rowkeyFormat = "{$name}_{_age}"
    val fields = pattern.findAllMatchIn(rowkeyFormat).toList.map(_.group(1))
    fields shouldEqual List("$name","_age")
  }

  it("test getrowkey") {
    val rowkeyFormat = "${name}%%{age}$"
    val map = Map("name"->"edmund", "age"->"1")

    new String(fixture.getRowkey(rowkeyFormat, map)) shouldEqual  "$edmund%%1$"
  }

}
