package scala.meta.tests
package semanticdb

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.meta.testkit.DiffAssertions
import scala.meta.internal.io.FileIO
import scala.meta._
import scala.meta.internal.metacp.{Main => Metacp, Settings => MetacpSettings}
import scala.meta.internal.metap.{Main => Metap}
import scala.meta.tests.cli._
import scala.compat.Platform.EOL
import org.langmeta.internal.io.PathIO
import org.langmeta.internal.semanticdb._
import org.langmeta.io.AbsolutePath
import org.scalatest.FunSuite

class ExpectSuite extends FunSuite with DiffAssertions {
  BuildInfo.scalaVersion.split("\\.").take(2).toList match {
    // both the compiler and stdlib are different between Scala versions.
    // For the sake of simplicity, we only run the expect test against the
    // output of 2.12. It's possible to add another expect file for 2.12
    // later down the road if that turns out to be useful.
    case "2" :: "12" :: Nil =>
      test("metacp.expect") {
        import MetacpExpect._
        assertNoDiff(loadObtained, loadExpected)
      }
      test("metacp.owners") {
        import MetacpOwnersExpect._
        assertNoDiff(loadObtained, loadExpected)
      }
      test("lowlevel.expect") {
        import LowlevelExpect._
        assertNoDiff(loadObtained, loadExpected)
      }
      test("highlevel.expect") {
        import HighlevelExpect._
        assertNoDiff(loadObtained, loadExpected)
        val sourceroot = PathIO.workingDirectory
        val roundtrip = loadDatabase.toSchema(sourceroot).toDb(Some(Sourcepath(sourceroot)))
        assertNoDiff(roundtrip.toString, loadExpected, "Roundtrip")
      }
      test("metac.owners") {
        import MetacOwnersExpect._
        assertNoDiff(loadObtained, loadExpected)
      }
    case _ =>
      ()
  }
}

trait ExpectHelpers {
  def filename: String
  def path: Path =
    Paths.get("tests", "jvm", "src", "test", "resources", filename)
  def loadExpected: String =
    new String(Files.readAllBytes(path), UTF_8)
  def saveExpected(value: String): Unit =
    Files.write(path, value.getBytes(UTF_8))

  protected def highlevelDatabase(path: Path): Database = {
    val database = Database.load(Classpath(path.toString))
    val sorted = Database(database.documents.sortBy(_.input.syntax))
    sorted
  }

  protected def lowlevelSyntax(path: Path): String = {
    val paths = Files.walk(path).iterator.asScala
    val semanticdbs = paths.map(_.toString).filter(_.endsWith(".semanticdb")).toArray.sorted
    val (exitcode, stdout) = CliSuite.communicate(Metap.process(semanticdbs))
    assert(exitcode == 0)
    stdout
  }

  protected def decompiledPath(path: Path): Path = {
    val target = Files.createTempDirectory("target_")
    val metacp_settings = MetacpSettings.parse(List("-d", target.toString, path.toString)).get
    val (metacp_exitcode, _) = CliSuite.communicate(Metacp.process(metacp_settings))
    assert(metacp_exitcode == 0)
    target
  }

  protected def ownerSyntax(path: Path): String = {
    val paths = Files.walk(path).iterator.asScala
    val semanticdbs = paths.map(_.toString).filter(_.endsWith(".semanticdb")).toArray.sorted
    val owners = mutable.Map[String, String]()
    semanticdbs.foreach { semanticdb =>
      FileIO.readAllDocuments(AbsolutePath(semanticdb)).foreach { document =>
        document.symbols.foreach { info =>
          if (!info.symbol.startsWith(info.owner)) {
            sys.error(s"invalid owner ${info.owner} for ${info.symbol}")
          }
          if (info.symbol.startsWith("local") && info.owner != "") {
            sys.error(s"invalid owner ${info.owner} for ${info.symbol}")
          }
          val key = {
            if (info.symbol.startsWith("local")) info.symbol + "_" + document.uri
            else info.symbol
          }
          val value = {
            if (info.owner.nonEmpty) info.owner
            else "<none>"
          }
          owners.get(key) match {
            case Some(existingValue) =>
              if (value != existingValue) {
                sys.error(s"conflicting owners for $key: $existingValue, $value")
              }
            case None =>
              owners(key) = value
          }
        }
      }
    }
    // NOTE: This logic arranges the symbols into a neat tree,
    // but unfortunately we can't enable it right now, because we don't
    // save SymbolInformation for owners.
    //
    // val children = owners.toList.groupBy(_._2).mapValues(_.map(_._1))
    // val visited = mutable.Set[String]()
    // val buf = new StringBuilder
    // def loop(sym: String, level: Int): Unit = {
    //   visited += sym
    //   if (sym != "<none>") buf.append((" " * level) + sym + EOL)
    //   children.getOrElse(sym, Nil).sorted.foreach(loop(_, level + 2))
    // }
    // loop("<none>", -2)
    // if (owners.size != visited.size) {
    //   sys.error(s"dangling owners: ${(owners.keySet -- visited).mkString(", ")}")
    // }
    // buf.append(EOL)
    // buf.toString
    owners.toList.sortBy(_._1).map(kv => s"${kv._1} => ${kv._2}").mkString(EOL)
  }
}

object MetacpExpect extends ExpectHelpers {
  def filename: String = "metacp.expect"
  def loadObtained: String = lowlevelSyntax(decompiledPath(Paths.get(BuildInfo.databaseClasspath)))
}

object MetacpOwnersExpect extends ExpectHelpers {
  def filename: String = "metacp.owners"
  def loadObtained: String = ownerSyntax(decompiledPath(Paths.get(BuildInfo.databaseClasspath)))
}

object LowlevelExpect extends ExpectHelpers {
  def filename: String = "lowlevel.expect"
  def loadObtained: String = lowlevelSyntax(Paths.get(BuildInfo.databaseClasspath))
}

object HighlevelExpect extends ExpectHelpers {
  def filename: String = "highlevel.expect"
  def loadObtained: String = loadDatabase.syntax
  def loadDatabase: Database = highlevelDatabase(Paths.get(BuildInfo.databaseClasspath))
}

object MetacOwnersExpect extends ExpectHelpers {
  def filename: String = "metac.owners"
  def loadObtained: String = ownerSyntax(Paths.get(BuildInfo.databaseClasspath))
}

// To save the current behavior, run `sbt save-expect`.
object SaveExpectTest {
  def main(args: Array[String]): Unit = {
    MetacpExpect.saveExpected(MetacpExpect.loadObtained)
    MetacpOwnersExpect.saveExpected(MetacpOwnersExpect.loadObtained)
    LowlevelExpect.saveExpected(LowlevelExpect.loadObtained)
    HighlevelExpect.saveExpected(HighlevelExpect.loadObtained)
    MetacOwnersExpect.saveExpected(MetacOwnersExpect.loadObtained)
  }
}