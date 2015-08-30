package org.json4s

import org.json4s.ExtractionBugs.SomeOtherContent.SomeOtherContentClass
import org.specs2.mutable.Specification
import reflect.{ ClassDescriptor, Reflector }
import text.Document
import java.util

object NativeExtractionBugs extends ExtractionBugs[Document]("Native") with native.JsonMethods
object JacksonExtractionBugs extends ExtractionBugs[JValue]("Jackson") with jackson.JsonMethods

trait SharedModule {
  case class SharedObj(name: String, visible: Boolean = false)
}

object PingPongGame extends SharedModule

object ExtractionBugs {
  case class Response(data: List[Map[String, Int]])

  case class OptionOfInt(opt: Option[Int])

  case class PMap(m: Map[String, List[String]])

  case class ManyConstructors(id: Long, name: String, lastName: String, email: String) {
    def this() = this(0, "John", "Doe", "")
    def this(name: String) = this(0, name, "Doe", "")
    def this(name: String, email: String) = this(0, name, "Doe", email)
  }

  case class ExtractWithAnyRef()

  case class UnicodeFieldNames(`foo.bar,baz`: String)

  object HasCompanion {
    def hello = "hello"
  }
  case class HasCompanion(nums: List[Int])

  case class ABigDecimal(num: BigDecimal)

  trait Content {
    def id: String
    def name: String
  }

  object Content {

    class ContentClass(val id: String, val name: String) extends Content

    def apply(id: String, name: String): Content = {
      new ContentClass(id, name)
    }
  }

  trait SomeOtherContent {
    def path: String
    def isFoo: Boolean
    def content: Content
  }

  object SomeOtherContent {

    class SomeOtherContentClass(val path: String, val isFoo: Boolean, val content: Content) extends SomeOtherContent

    def apply(path: String, isFoo: Boolean, content: Content): SomeOtherContent = {
      new SomeOtherContentClass(path, isFoo, content)
    }
  }

}
abstract class ExtractionBugs[T](mod: String) extends Specification with JsonMethods[T] {

  import ExtractionBugs._
  implicit val formats: Formats = DefaultFormats.withCompanions(classOf[PingPongGame.SharedObj] -> PingPongGame)

  (mod + " Extraction bugs Specification") should {
    "ClassCastException (BigInt) regression 2 must pass" in {
      val opt = OptionOfInt(Some(39))
      Extraction.decompose(opt).extract[OptionOfInt].opt.get must_== 39
    }

    "Extraction should not fail when Maps values are Lists" in {
      val m = PMap(Map("a" -> List("b"), "c" -> List("d")))
      Extraction.decompose(m).extract[PMap] must_== m
    }

    "Extraction should not fail when class is defined in a trait" in {
      val inst = PingPongGame.SharedObj("jeff", visible = true)
      val extr = Extraction.decompose(inst)
      extr must_== JObject("name" -> JString("jeff"), "visible" -> JBool(true))
      extr.extract[PingPongGame.SharedObj] must_== inst
    }

    "Extraction should always choose constructor with the most arguments if more than one constructor exists" in {
      val args = Reflector.describe[ManyConstructors].asInstanceOf[ClassDescriptor].mostComprehensive
      args.size must_== 4
    }

    "Extraction should handle AnyRef" in {
      implicit val formats = DefaultFormats.withHints(FullTypeHints(classOf[ExtractWithAnyRef] :: Nil))
      val json = JObject(JField("jsonClass", JString(classOf[ExtractWithAnyRef].getName)) :: Nil)
      val extracted = Extraction.extract[AnyRef](json)
      extracted must_== ExtractWithAnyRef()
    }

    "Extraction should work with unicode encoded field names (issue 1075)" in {
      parse("""{"foo.bar,baz":"x"}""").extract[UnicodeFieldNames] must_== UnicodeFieldNames("x")
    }

    "Extraction should not fail if case class has a companion object" in {
      parse("""{"nums":[10]}""").extract[HasCompanion] must_== HasCompanion(List(10))
    }

    "Extraction should not fail if using companion objects apply method" in {
      val content: Content = parse("""{"id":"some-id", "name":"some-name"}""").extract[Content]
      content must haveClass[Content.ContentClass]
      content.id must_== "some-id"
      content.name must_== "some-name"
    }

    "Extraction should not fail if using companion objects apply method to extract nested json field" in {
      val json = """{"path":"some-path", "isFoo":false, "content": {"id":"some-id", "name":"some-name"}}"""
      val someOtherContent: SomeOtherContent = parse(json).extract[SomeOtherContent]
      someOtherContent must haveClass[SomeOtherContent.SomeOtherContentClass]
      val content = someOtherContent.content
      someOtherContent.isFoo must_== false
      someOtherContent.path must_== "some-path"
      content.id must_== "some-id"
      content.name must_== "some-name"
    }

    "Issue 1169" in {
      val json = parse("""{"data":[{"one":1, "two":2}]}""")
      json.extract[Response] must_== Response(List(Map("one" -> 1, "two" -> 2)))
    }

    "Extraction should extract a java.util.ArrayList as array" in {
      Extraction.decompose(new util.ArrayList[String]()) must_== JArray(Nil)
    }

    "Extraction should extract a java.util.ArrayList as array" in {
      val json = parse("""["one", "two"]""")
      val lst = new util.ArrayList[String]()
      lst.add("one")
      lst.add("two")
      json.extract[util.ArrayList[String]] must_== lst
    }

    "Parse 0 as BigDecimal" in {
      val bd = ABigDecimal(BigDecimal("0"))
      parse("""{"num": 0}""", useBigDecimalForDouble = true).extract[ABigDecimal] must_== bd
    }

    "Extract a bigdecimal from a decimal value" in {
      val bd = ABigDecimal(BigDecimal("12.305"))
      parse("""{"num": 12.305}""", useBigDecimalForDouble = true).extract[ABigDecimal] must_== bd
    }

  }
}

