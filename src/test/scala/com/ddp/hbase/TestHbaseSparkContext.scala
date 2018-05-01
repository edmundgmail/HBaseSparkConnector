package com.ddp.hbase

import java.util.Properties

import org.apache.spark.sql.SparkSession

case class Student(name: String, age: Int)

class TestHbaseSparkContext extends Testing{
  var spark : SparkSession = _
  var fixture : HBaseSparkContext = _

  override def beforeAll(): Unit = {
    spark = SparkSession.builder().appName("localtest").master("local[*]").getOrCreate
    val properties = new Properties()
    fixture = HBaseSparkContext(spark.sparkContext, properties)
  }

  it("Test") {
     val rdd = spark.sparkContext.parallelize(List(
       Student("Edmund", 10),
       Student("Wayne", 20),
       Student("Will", 12)

     ))
     fixture.save("test", "f1", "${name}_${age}", rdd)
  }
}
