package com.ddp.hbase

import java.security.PrivilegedExceptionAction

import com.ddp.logging.Logging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hbase.{HBaseConfiguration, HConstants}
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory}
import org.apache.hadoop.security.{SecurityUtil, UserGroupInformation}
import org.apache.spark.{SparkEnv, SparkFiles}

import scala.util.{Failure, Properties, Success, Try}

object HBaseConnection extends Logging{

  private var connection: Connection = _

  private def createConfig(quorum: String, clientPort: String, master: String) = {
    val hbaseConfig = HBaseConfiguration.create()
    hbaseConfig.set("hbase.zookeeper.quorum", quorum)
    hbaseConfig.set("hbase.zookeeper.property.clientPort", clientPort)
    hbaseConfig.set("hbase.master", master)
    hbaseConfig
  }

  private def createSecuredHBaseConnection(coreSiteXml: String, hdfsSiteXml: String, hbaseSiteXml: String,
                                           krb5Conf: String, principal: String, keytab: String, scannerTimeoutMs: Int) = {
    try {
      val conf = new Configuration()

      conf.addResource(new Path(coreSiteXml))
      conf.addResource(new Path(hdfsSiteXml))
      conf.addResource(new Path(hbaseSiteXml))
      conf.setInt(HConstants.HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD, scannerTimeoutMs)

      val hbaseConfig = HBaseConfiguration.create(conf)

      Properties.setProp("java.security.krb5.conf", krb5Conf)
      UserGroupInformation.setConfiguration(hbaseConfig)
      UserGroupInformation.loginUserFromKeytab(principal, getKeyTabFile(keytab))

      connection = SecurityUtil.doAsLoginUser(new PrivilegedExceptionAction[Connection] {
        override def run(): Connection = {
          ConnectionFactory.createConnection(hbaseConfig)
        }
      })

      Success(connection)

    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e)
        Failure(e)
    }
  }

  private def getKeyTabFile(keytabPath: String): String = {
    val path = new Path(keytabPath)

    if (SparkEnv.get != null && SparkEnv.get.executorId != "driver")
      SparkFiles.get(path.getName)
    else
      path.toUri.getPath
  }

  private def createHBaseConnection(hbaseConfig: Configuration) =
    try {
      connection = ConnectionFactory.createConnection(hbaseConfig)
      Success(connection)
    }
    catch {
      case e: Exception =>
        logger.error(
          s"Cannot connect to hbase with Quorum: ${hbaseConfig.get("hbase.zookeeper.quorum")}, " +
            s"ClientPort: ${hbaseConfig.get("hbase.zookeeper.property.clientPort")}, " +
            s"Master: ${hbaseConfig.get("hbase.master")}: ${e.getMessage}", e)
        Failure(e)
    }

  def setConnection(conn: Connection): Unit = connection = conn

  def getSecuredConnection(coreSiteXml: String, hdfsSiteXml: String, hbaseSiteXml: String, krb5Conf: String,
                           principal: String, keytab: String, scannerTimeoutMs: Int): Try[Connection] =
    if (!connection.isInstanceOf[Connection] || connection.isClosed || connection.isAborted)
      synchronized {
        if (!connection.isInstanceOf[Connection] || connection.isClosed || connection.isAborted)
          createSecuredHBaseConnection(coreSiteXml, hdfsSiteXml, hbaseSiteXml, krb5Conf, principal, keytab, scannerTimeoutMs)
        else
          Success(connection)
      }
    else
      Success(connection)

  def getConnection(quorum: String, clientPort: String, master: String): Try[Connection] =
    if (!connection.isInstanceOf[Connection] || connection.isClosed || connection.isAborted)
      synchronized {
        if (!connection.isInstanceOf[Connection] || connection.isClosed || connection.isAborted)
          createHBaseConnection(createConfig(quorum, clientPort, master))
        else
          Success(connection)
      }
    else
      Success(connection)
}
