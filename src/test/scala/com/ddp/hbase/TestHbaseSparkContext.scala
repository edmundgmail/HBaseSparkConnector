package com.ddp.hbase

import java.util.Properties

import org.apache.spark.sql.SparkSession

case class Student(name: String, age: Int)

class TestHbaseSparkContext extends Testing{
  var spark : SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession.builder().appName("localtest").master("local[*]").getOrCreate
  }

  it("test getrowkey") {
    val rowkeyFormat = "${name}%%{age}$"
    val map = Map("name"->"edmund", "age"->"1")

    assert(new String(HBaseSparkContext.getRowkey(rowkeyFormat, map)) == "$edmund%%1$")
  }

  it("test regex") {
    val pattern = "\\{([a-zA-Z_$][a-zA-Z0-9_$]*)\\}".r
    val rowkeyFormat = "{$name}_{_age}"
    val fields = pattern.findAllMatchIn(rowkeyFormat).toList.map(_.group(1))
    println(fields.mkString(","))
  }

  it("Test") {
     val properties = new Properties()
     val hBaseSparkContext = HBaseSparkContext(spark.sparkContext, properties)
     val rdd = spark.sparkContext.parallelize(List(
       Student("Edmund", 10),
       Student("Wayne", 20),
       Student("Will", 12)

     ))
     hBaseSparkContext.save("test", "f1", "${name}_${age}", rdd)
  }
}
