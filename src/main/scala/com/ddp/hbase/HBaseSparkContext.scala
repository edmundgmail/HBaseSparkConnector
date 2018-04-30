package com.ddp.hbase

import java.util.Properties

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.concurrent.duration.Duration

class HBaseSparkContext(sc: SparkContext, properties: Properties) {
  @transient var hBaseDAO:HBaseDAO = null

  def getHBaseDAO(secured : Boolean = false) =
    if(hBaseDAO!= null) hBaseDAO
    else if (secured) {
      val coreSiteXml = properties.getProperty("coreSiteXml", "")
      val hdfsSiteXml = properties.getProperty("hdfsSiteXml", "")
      val hbaseSiteXml = properties.getProperty("hbaseSiteXml", "")
      val principal = properties.getProperty("principal", "")
      val krb5conf = properties.getProperty("krb5conf", "")
      val keytab = properties.getProperty("keytab", "")
      hBaseDAO = new HBaseDAO(
        coreSiteXml= coreSiteXml,
        hdfsSiteXml = hdfsSiteXml,
        hbaseSiteXml = hbaseSiteXml,
        krb5conf = krb5conf,
        principal = principal,
        keytab = keytab)
      hBaseDAO
  } else{
      val clientPort = properties.getProperty("clientPort", "")
      val quorum = properties.getProperty("quorum", "")
      val hBaseMaster = properties.getProperty("hBaseMaster", "")
      hBaseDAO = new HBaseDAO(quorum = quorum, clientPort =clientPort, hBaseMaster = hBaseMaster)
      hBaseDAO
    }


  def save[T](tableName: String, rdd: RDD[T]) : Unit = ???
  def readAll[T](tableName: String): RDD[T] = ???
}

object HBaseSparkContext {
  def apply(sc: SparkContext, properties: Properties) = new HBaseSparkContext(sc, properties)
}