import scodec.bits.ByteVector
import scalaz.concurrent._
import scalaz.stream._
import atto._, Atto._
import atto.stream._
import atto.syntax.stream.all._
/*
 * input  ->  |Rubbish|.....|.......|..|......|Rubbish|
 * output ->          |__|__|__|__|__|__|__|__|
 *
 *
 * Example of buffered input and streamed output.
 * The expected output is a line of text, the input will be a
 * buffer of externally defined length.  Meaning the two boundaries will not always align.
 *
 *
 */
object Example extends App {

  import scala.{ List, Nil }
  import scala.language._
  import scala.Predef.{ augmentString }
  import scalaz.{ Monad, Catchable, \/ }
  import scalaz.syntax.monad._
  import scalaz.stream._

  import Atto._
  import ParseResult._
  import Process.{ await1, emit, emitAll, halt, suspend, await, eval, receive1Or }
  import process1.{ id, scan }

  def parseM[A](input:String,p: Parser[A]): Process1[String, A] = {
    def exhaust(r: ParseResult[A], acc: List[A]): (ParseResult[A], List[A]) =
      r match {
        case Done(in, a) => exhaust(p.parse(in), a :: acc)
        case _           => (r, acc)
      }
    def go(r: ParseResult[A]): Process1[String, A] =
      receive1Or[String, A](emitAll(exhaust(r.done, Nil)._2)) { s =>
        val (r0, acc) = r match {
          case Done(in, a)    => (p.parse(in + s), List(a))
          case Fail(in, _, _) => (r, Nil)
          case Partial(_)     => (r.feed(s), Nil)
        }
        val (r1, as) = exhaust(r0, acc)
        emitAll(as.reverse) ++ go(r1)
      }
    go(p.parse(input))
  }

  def stream[A,B](a: Parser[A],b: Parser[B]):Process1[String,B]  = {
    def go(r: ParseResult[A]): Process1[String,B] =
      r match {
        case Partial(_)         => receive1Or[String, B](halt){s => go(r feed s)}
        case Done(input,result) => parseM(input,b)
        case f @ Fail(_,_,msg)  => Process.Halt(Cause.Error(new Exception(msg)))
      }
    go(a.parse(""))
  }

  // l33t complex grammar
  lazy val cr: Parser[Char]                         = char(0x0D)
  lazy val lf: Parser[Char]                         = char(0x0A)
  lazy val eoln                                     = opt(cr) ~ lf
  lazy val boundary                                 = char('☃')
  lazy val delimiter                                = boundary ~ eoln
  lazy val thing                                    = manyUntil(letter,delimiter).map(_.mkString)
  lazy val preamble                                 = opt(manyUntil(anyChar,delimiter))
  //Non streaming version
  //lazy val things                                 = many(thing)
  //lazy val epilogue                               = opt(skipMany(anyChar))
  //lazy val complete                               = preamble ~> things <~ epilogue
  val bufferLength                                  = 6

  val input = """this is all rubbish
               |this is all rubbish☃
               |A☃
               |AB☃
               |ABC☃
               |ABCD☃
               |ABCDE☃
               |ABCDEF☃
               |ABCDEFG☃
               |ABCDEFGH☃
               |this is all rubbish""".stripMargin

  lazy val ip: Process[Task, String] = Process.emitAll(input.grouped(bufferLength).toSeq)

  val toEither: String => Either[Int,String] = s => s.length() % 2 match {
    case 1 => Right(s)
    case _ => Right(s)//Left(s.length()) //
  }

  lazy val fromEither: Either[Int,String] => Process[Task,String] = e =>
    e.fold(i => Process.Halt(Cause.End), s => Process.emit(s))

  lazy val output = ip.
  map(toEither).
  flatMap(fromEither).
  pipe(stream(preamble,thing)).
  runLog.
  run.
  mkString("\n")

  println(s"output\n$output")



}