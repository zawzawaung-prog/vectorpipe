package vectorpipe.osm

import java.io.InputStream
import org.scalatest._

import java.io.FileInputStream
import scala.util.{ Failure, Success, Try }

// --- //

class XMLSpec extends FunSpec with Matchers {

  def parseOSM(file: String) = {
    val xml: InputStream = new FileInputStream(file)
    val res: Try[(List[Node], List[Way], List[Relation])] = Element.elements.parse(xml)

    res match {
      case Success(_) => ()
      case Failure(e) => println(e); fail()
    }

    assert(res.isSuccess)
  }

  describe("XML Parsing") {
    it("8-shaped Multipolygon") {
      parseOSM("data/8shapedmultipolygon.osm")
    }

    it("Quarry Rock") {
      parseOSM("data/quarry-rock.osm")
    }

    it("Stanley Park") {
      parseOSM("data/stanley-park.osm")
    }

    /*
    it("North Van") {
      parseOSM("data/north-van.osm")
    }

    it("Baarle Nassau") {
      parseOSM("data/baarle-nassau.osm")
    }
     */
  }
}
