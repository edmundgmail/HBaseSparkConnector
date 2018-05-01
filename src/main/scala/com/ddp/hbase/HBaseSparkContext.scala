package com.ddp.hbase

import java.util.Properties

import org.apache.hadoop.hbase.client.Put
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.concurrent.duration.Duration


trait HBaseSparkContext {
  def save[T <: Product](tableName: String, familyName: String, rowkeyFields: String, rdd: RDD[T]) : Unit
  def readAll[T](tableName: String): RDD[T]
}

object HBaseSparkContext {
  def apply(sc: SparkContext, properties: Properties) = new HBaseSparkContextImpl(sc, properties)
}