package bootcamp


import java.util.Date

import org.apache.spark.{SparkContext, SparkConf}
import com.datastax.spark.connector._

case class EmailOld(user: String, mailbox: String, msgdate:Date, message_id:String, bcclist: Set[String], body: String, cclist: Set[String], fromlist: Set[String], subject: String, tolist: Set[String])

case class Email(user: String, mailbox: String, bcclist: Set[String], message_id: String, msgdate: Date, body: String, cclist: Set[String], fromlist: Set[String], subject: String, tolist: Set[String])

case class Email3(username: String, mailbox: String, bcclist: Set[String], message_id: String, msgdate: Date, body: String, cclist: Set[String], fromlist: Set[String], subject: String, tolist: Set[String])

case class Attachment_summary(username: String, mailbox: String, msgdate: String, message_id: String, filename: String, content_type: String)

case class Attachment(username: String, mailbox: String, msgdate: String, message_id: String, filename: String, content_type: String, bytes: Array[Byte])

case class MailboxesByUser(user:String, mailbox:String)



/*
CREATE TABLE msgs_per_mailbox_by_user (
user text,
mailbox text,
msgdate timestamp,
message_id text,
bcclist set<text>,
body text,
cclist set<text>,
fromlist set<text>,
is_read boolean,
subject text,
tolist set<text>,
PRIMARY KEY ((user, mailbox), msgdate, message_id)
)
*/

case class MsgsPerMailboxByUser(username: String, mailbox: String, bcclist: Set[String], message_id: String, msgdate: Date, body: String, cclist: Set[String], fromlist: Set[String], subject: String, tolist: Set[String] )

object StupermailETL {
  def main(args: Array[String]) {
    val sparkConf = new SparkConf()
      .setAppName("StupermailETL")
      .set("spark.cassandra.connection.host", "52.10.71.51")
      .setJars(Array("target/scala-2.10/stupermail-assembly-0.2.0.jar"))
      .setMaster("spark://52.10.71.51:7077")

    val sc = new SparkContext(sparkConf)

    //  https://github.com/datastax/spark-cassandra-connector/blob/master/spark-cassandra-connector/src/it/scala/com/datastax/spark/connector/rdd/CassandraRDDSpec.scala

    val emailRDD = sc.cassandraTable[Email]("stupormail", "email")
    //val emailRDD = sc.cassandraTable[Email]("stupormail", "email").where("user in ('meyers-a', 'dana_davis', 'scholtes-d', 'mcconnell-m')")

    //val emailRDD = sc.cassandraTable[Email]("stupormail", "email")

    println(s"rdd size: ${emailRDD.count()}")
    emailRDD.take(50).foreach(email => println(s"email.user: ${email.user}" ))

    val groupedEmail = emailRDD.groupBy(email => (email.user, email.mailbox))
    groupedEmail.count()
    groupedEmail.take(50).foreach {
      case ((user, mailbox), emailList) => {
        println(s"group count: ${user} ${mailbox} ${emailList.size}")
      }
    }


    println(s"rdd size: ${emailRDD.count()}")
    emailRDD.take(20).foreach(email => println(email.user))
    emailRDD.take(20).foreach(email => println(email.mailbox))

    //Query 0: Get mailboxes for a given user
    val mailboxesByUserRDD = emailRDD.map(email => MailboxesByUser(email.user,email.mailbox))
    mailboxesByUserRDD.saveToCassandra("supermail", "mailboxes_by_user", SomeColumns("user", "mailbox"))


    //Query 2: Get 20 most recent in given mailbox
    emailRDD.saveToCassandra("supermail", "msgs_per_mailbox_by_user")

    sc.stop()


  }
}

//select * from msgs_per_mailbox_by_user where user in ('meyers-a', 'dana_davis', 'scholtes-d', 'mcconnell-m')

