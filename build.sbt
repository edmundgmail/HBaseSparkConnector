name := "HBaseSparkConnector"

version := "0.1"

scalaVersion := "2.11.8"

lazy val versions = Map(
  "confluent" -> "3.3.1",
  "hbase" -> "1.2.0",
  "hadoop" -> "2.7.1",
  "jackson" -> "2.8.4",
  "spark" -> "2.2.1",
  "elasticsearch" -> "6.1.0",
  "kafka"->"0.11.0.2"
)

val hbaseClient = "org.apache.hbase" % "hbase-client" % versions("hbase")
val hbaseCommon = "org.apache.hbase" % "hbase-common" % versions("hbase")
val hbaseCommonTest = "org.apache.hbase" % "hbase-common" % versions("hbase") % "test" classifier "tests"
val hbaseServer = "org.apache.hbase" % "hbase-server" % versions("hbase") % "test"
val hbaseServerTest = "org.apache.hbase" % "hbase-server" % versions("hbase") % "test" classifier "tests"

val sparkCore = "org.apache.spark" % "spark-core_2.11" % versions("spark") % "compile"
val sparkSql = "org.apache.spark" % "spark-sql_2.11" % versions("spark")  % "compile"
val sparkStream = "org.apache.spark" % "spark-streaming_2.11" % versions("spark")  % "compile"
val sparkHive = "org.apache.spark" % "spark-hive_2.11" % versions("spark")  % "compile"



libraryDependencies ++= Seq(
  sparkCore, sparkSql, sparkStream, sparkHive, hbaseClient, hbaseCommon, hbaseCommonTest, hbaseServer, hbaseServerTest,
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.mockito" % "mockito-core" % "1.10.19" % "test"
)

resolvers ++= Seq(
  "Apache Repository" at "https://repository.apache.org/content/repositories/releases/"
)