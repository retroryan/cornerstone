package bootcamp

import java.util.Date

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext

import com.datastax.spark.connector._

case class Receipts(cashier_first_name: Int, cashier_id: String, cashier_last_name: String)

object FirstTest {
  def main(args: Array[String]) {
    val sparkConf = new SparkConf()
      .setAppName("techsupply")
      .set("spark.cassandra.connection.host", "172.28.128.3")
      .setMaster("spark://172.28.128.3:7077")

    //      .setJars(Array("target/scala-2.10/stupermail-assembly-0.2.0.jar"))

    val sc = new SparkContext(sparkConf)

    val genericRDD = sc.cassandraTable("retail", "receipts")

    genericRDD take 20 foreach println

    sc.stop
  }
}


/**
CREATE TABLE receipts (
receipt_id timeuuid,
cashier_first_name text,
cashier_id int,
cashier_last_initial text,
cashier_last_name text,
close_drawer timestamp,
payment map<text, text>,
register_id int,
savings decimal,
store_id int,
subtotal decimal,
tax_rate decimal,
total decimal,
PRIMARY KEY ((receipt_id))
)

**/