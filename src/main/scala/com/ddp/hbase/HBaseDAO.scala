package com.ddp.hbase

import java.io.IOException

import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter._
import org.apache.hadoop.hbase.quotas.ThrottlingException
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{DoNotRetryIOException, HConstants, TableName}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success, Try}
import HBaseDAO._
import com.ddp.logging.Logging
import com.ddp.utils.Retry
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp



case class HBaseDAO(coreSiteXml: String,
                    hdfsSiteXml: String,
                    hbaseSiteXml: String,
                    krb5conf: String,
                    principal: String,
                    keytab: String,
                    quorum: String,
                    clientPort: String,
                    hBaseMaster: String,
                    retryCount: Int,
                    retryDeadLine: Int,
                    backoffDurations: Seq[Duration],
                    scannerTimeoutMs: Int)
  extends Serializable with Logging {

  def this(quorum: String,
           clientPort: String,
           hBaseMaster: String,
           retryCount: Int,
           retryDeadLine: Int,
           backoffDurations: Seq[Duration],
           scannerTimeoutMs: Int) = {
    this(null, null, null, null, null, null, quorum, clientPort, hBaseMaster, retryCount, retryDeadLine,
      backoffDurations, scannerTimeoutMs)
  }

  def this(quorum: String,
           clientPort: String,
           hBaseMaster: String) = this(quorum, clientPort, hBaseMaster, 10, 900, defaultBackoffDurations, HConstants.DEFAULT_HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD)

  def this(coreSiteXml: String,
           hdfsSiteXml: String,
           hbaseSiteXml: String,
           krb5conf: String,
           principal: String,
           keytab: String,
           retryCount: Int = 10,
           retryDeadLine: Int = 900,
           backoffDurations: Seq[Duration] = defaultBackoffDurations,
           scannerTimeoutMs: Int = HConstants.DEFAULT_HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD) = {
    this(coreSiteXml, hdfsSiteXml, hbaseSiteXml, krb5conf, principal, keytab, null, null, null, retryCount, retryDeadLine,
      backoffDurations, scannerTimeoutMs)
  }

  def useSecuredConnection: Boolean = false

  private[hbase] def retry[T](blockDescription: String = "")(block: => T): Try[T] = {
    val deadline = retryDeadLine.seconds.fromNow
    val f = Retry.retry(retryCount, Some(deadline), backoffFunc(backoffDurations.toArray), retryRules,
      blockDescription)(block)
    Try(Await.result(f, deadline.timeLeft))
      .transform(t => Success(t), e => {logger.error(e.getMessage); Failure(e)})
  }

  private[hbase] def connection: Try[Connection] = {
    if (useSecuredConnection)
      HBaseConnection.getSecuredConnection(coreSiteXml, hdfsSiteXml, hbaseSiteXml, krb5conf, principal, keytab, scannerTimeoutMs)
    else
      HBaseConnection.getConnection("localhost","2181", "/hbase")
  }

  @throws[IOException]
  def getTable(tableName: String): Option[Table] = connection match {
    case Success(connection) =>
      Try(connection.getTable(TableName.valueOf(tableName))) match {
        case Success(table) => Some(table)
        case Failure(e) =>
          throw new IOException(s"Failed to find HBase table $tableName", e)
      }
    case Failure(e) =>
      throw new IOException(s"Failed to connect to HBase", e)
  }

  def writeToTable(tableName: String, puts: Seq[Put]): Boolean = retry(s"writeToTable: $tableName") {
    getTable(tableName).exists{ table =>
      try{
        table.put(puts.asJava)
        true
      } finally {
        table.close()
      }
    }
  }.getOrElse(false)

  def getRowsFromRowKeys(tableName: String, keys : Seq[Array[Byte]]): Seq[Result] = retry("getRowsFromRowKeys") {
    getTable(tableName).map{table =>
      try {
        val gets = keys.map(key => new Get(key))
        val results = table.get(gets.asJava)

        val rowkeysNotFound = gets.zip(results)
          .filter(_._2.isEmpty)
          .map(_._1.getRow)
          .map(Bytes.toString)

        if (rowkeysNotFound.nonEmpty)
          logger.warn(s"Cannot find records from $tableName for rowkeys: ${rowkeysNotFound.take(100)}" + {if (rowkeysNotFound.size > 100) " and more..." else ""})

        results
          .filter { result => !result.isEmpty }
          .toList

      } finally {
        table.close()
      }
    }.getOrElse(Nil)
  }.getOrElse(Nil)

  def findRowkeys(tableName: String, rowKeys: Seq[String]): Seq[String] = retry("findRowkeys") {
    getTable(tableName).map{table =>
      try {
        val gets = rowKeys.map(key => new Get(key.getBytes))
        val results = table.get(gets.asJava)
        val rowkeysFound = gets.zip(results)
          .filterNot(_._2.isEmpty)
          .map(_._1.getRow)
          .map(Bytes.toString)

        rowkeysFound
      } finally {
        table.close()
      }
    }.getOrElse(Nil)
  }.getOrElse(Nil)

  def getRowkeysWithFilter(tableName: String, filters: List[MyFilter], limit: Int = 10000) : Seq[Array[Byte]] = retry("getRowKeysFromTable") {
    val list = new FilterList(FilterList.Operator.MUST_PASS_ALL, filters.map(_.toHBaseFilter).asJava)

    getTable(tableName).map{ table =>
      val scan = new Scan().setFilter(list)
      val resultScanner = table.getScanner(scan)
      try{
        resultScanner.next(limit).map(_.getRow).toList
      } finally {
        resultScanner.close()
        table.close()
      }
    }.getOrElse(Nil)
  }.getOrElse(Nil)

  def getRowKeys(tableName: String, limit: Int): Seq[Array[Byte]] = retry("getRowKeysFromTable") {
    getTable(tableName).map{ table =>
      val scan = new Scan().setFilter(new FirstKeyOnlyFilter())
      val resultScanner = table.getScanner(scan)
      try{
        resultScanner.next(limit).map(_.getRow).toList
      } finally {
        resultScanner.close()
        table.close()
      }
    }.getOrElse(Nil)
  }.getOrElse(Nil)

  def getAllRowsScanner(tableName: String): Option[ResultScanner] = retry("getAllRowsScanner") {
    getTable(tableName).map{ table =>
      val scan = new Scan()
      table.getScanner(scan)
    }
  }.getOrElse(None)

  def getAllRowKeysScanner(tableName: String): Option[ResultScanner] = retry("getAllRowkeysScanner") {
    getTable(tableName).map{ table =>
      table.getScanner(new Scan().setFilter(new FirstKeyOnlyFilter()))
    }
  }.getOrElse(None)

  def getNextRows(resultScanner: ResultScanner, nRows: Int): Seq[Result] = retry("getNextRows") {
    resultScanner.next(nRows).toSeq
  }.getOrElse(Nil)

  def getNextRowKeys(resultScanner: ResultScanner, nRows: Int): Seq[String] = retry("getNextRowKeys") {
    resultScanner.next(nRows).map(result => Bytes.toString(result.getRow)).toSeq
  }.getOrElse(Nil)

  def deleteRowsFromTable(tableName: String, rowkeys: Seq[Array[Byte]]): Boolean = retry("deleteRowsFromTable") {
    getTable(tableName).exists{ table =>
      try {
        //Note: needs to be mutable otherwise table.delete throw exception
        val deleteList = rowkeys.map(new Delete(_))
        table.delete(deleteList.to[ListBuffer].asJava)
        true
      } finally {
        table.close()
      }
    }
  }.getOrElse(false)

  def getFirstRowFromRowKeyPrefix(tableName: String, rowKeyPrefix: String, columnFamily: String,
                                  columnName: String): Option[String] = {

    val result = getFirstRowWithPrefix(tableName, rowKeyPrefix)

    result match {
      case Some(r) =>
        Some(Bytes.toString(r.getValue(columnFamily.getBytes, columnName.getBytes)))
      case None =>
        logger.warn(s"Cannot find record with prefix $rowKeyPrefix")
        None
    }
  }

  def getRowsWithPrefix(tableName: String, rowKeyPrefix: String): Seq[Result] = retry("getRowsWithPrefix") {
    getTable(tableName).map{ table =>
      val scan = new Scan().setRowPrefixFilter(rowKeyPrefix.getBytes)
      val resultScanner = table.getScanner(scan)
      table.close()
      resultScanner.asScala.toList
    }.getOrElse(EMPTY_LIST)
  }.getOrElse(EMPTY_LIST)

  def getFirstRowWithPrefix(tableName: String, rowKeyPrefix: String): Option[Result] = retry("getFirstRowWithPrefix") {
    getTable(tableName).map{ table =>
      val scan = new Scan().setRowPrefixFilter(rowKeyPrefix.getBytes)
      val resultScanner = table.getScanner(scan)
      table.close()
      Option(resultScanner.next)
    }.getOrElse(None)
  }.getOrElse(None)

}

object HBaseDAO {
  val EMPTY_LIST = List[Result]()

  val defaultBackoffDurations: Seq[Duration] = Seq(10, 20, 60, 120).map(_.seconds)

  def backoffFunc(durations: Array[Duration]) = (r: Int) => durations(Math.min(r, durations.length - 1))

  private def retryRules(t: Throwable): Boolean = {
    t match {
      case e: RuntimeException if e.getCause.isInstanceOf[ThrottlingException] => true
      case e: ThrottlingException => true
      case e: RetriesExhaustedWithDetailsException =>
        val causes = e.getCauses
        causes.size() > 0 && causes.get(0).isInstanceOf[ThrottlingException]
      case e: DoNotRetryIOException => false // f.ex. table not found
      case e: IOException => true // connection exception
      case _ => false
    }
  }
}
