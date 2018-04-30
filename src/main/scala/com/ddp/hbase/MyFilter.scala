package com.ddp.hbase

import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
import org.apache.hadoop.hbase.filter.{Filter, SingleColumnValueFilter}
import org.apache.hadoop.hbase.util.Bytes

object CompareOperator extends Enumeration {
  val LESS_THAN = Value("<")
  val LESS_EQUAL = Value("<=")
  val EQUAL = Value("=")
  val GREATER_THAN = Value(">")
  val GREATER_EQUAL = Value(">=")
}

trait MyFilter {
  def toHBaseFilter : Filter
}

case class MyColumnValueFilter(cf: String, c: String, operator: String, value: String) extends MyFilter{
  override def toHBaseFilter = {
    val op = CompareOperator.withName(operator) match  {
      case CompareOperator.EQUAL => CompareOp.EQUAL
      case CompareOperator.GREATER_EQUAL => CompareOp.GREATER_OR_EQUAL
      case CompareOperator.GREATER_THAN => CompareOp.GREATER
      case CompareOperator.LESS_EQUAL => CompareOp.LESS_OR_EQUAL
      case CompareOperator.LESS_THAN => CompareOp.LESS
    }
    new SingleColumnValueFilter(Bytes.toBytes(cf), Bytes.toBytes(c), op, Bytes.toBytes(value))
  }
}

object MyFilter {

}

