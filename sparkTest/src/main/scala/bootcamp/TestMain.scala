package bootcamp


object TestMain {

  def main(args: Array[String]) {
    val cp = Seq("this.jar", "that.jar", "one.jar", "two.jar")
    val nCp = cp filter { f => f == "that.jar"  || f == "two.jar" }

    nCp.foreach(println(_))
  }

}
