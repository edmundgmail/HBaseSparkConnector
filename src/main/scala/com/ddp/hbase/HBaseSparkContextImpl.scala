package com.ddp.hbase

import java.util.Properties

import org.apache.hadoop.hbase.client.Put
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

class HBaseSparkContextImpl (sc: SparkContext, properties: Properties) extends HBaseSparkContext with Serializable {
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
      val clientPort = properties.getProperty("clientPort", "2181")
      val quorum = properties.getProperty("quorum", "localhost")
      val hBaseMaster = properties.getProperty("hBaseMaster", "/hbase")
      hBaseDAO = new HBaseDAO(quorum = quorum, clientPort =clientPort, hBaseMaster = hBaseMaster)
      hBaseDAO
    }

  override def save[T <: Product](tableName: String, familyName: String, rowkeyFields: String, rdd: RDD[T]) : Unit = {
    rdd.foreachPartition(p=> {
      val puts = p.toList.map(r=> toPut(familyName, rowkeyFields, r) )
      getHBaseDAO().writeToTable(tableName, puts)
    }
    )
  }

  override def readAll[T](tableName: String, limit: Int = 10000): RDD[T] = {
      getHBaseDAO().g
      })
  }

  def getCCParams(cc: Product) : Map[String, String] = {
    val values = cc.productIterator
    cc.getClass.getDeclaredFields.map( _.getName -> values.next.toString ).toMap
  }

  private [hbase] def getRowkey(rowkeyFormat: String, map: Map[String, String]) : Array[Byte] = {
    val pattern = "\\{([a-zA-Z_$][a-zA-Z0-9_$]*)\\}".r
    val fields = pattern.findAllMatchIn(rowkeyFormat).toList.map(_.group(1))
    fields.foldLeft(rowkeyFormat)((format, field) => format.replace(s"{${field}}", map.get(field).getOrElse(""))).getBytes
  }

  private [hbase] def toPut[T <: Product](familyName: String, rowkeyFields: String, record: T): Put = {
    val family = familyName.getBytes
    val cc = getCCParams(record)

    cc.keySet.foldLeft(new Put(getRowkey(rowkeyFields, cc)))((ret: Put, column: String)=> ret.addColumn(family, column.toString.getBytes, cc.get(column).getOrElse("").toString.getBytes))
  }

}
