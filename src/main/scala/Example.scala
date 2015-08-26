import scodec.bits.ByteVector
import scalaz.concurrent._
import scalaz.stream._
import atto._
import Atto._
import atto.stream._
import atto.syntax.stream.all._
import scala.util.Random._
import ParseResult._
import Process.{ await1, emit, emitAll, halt, suspend, await, eval, receive1Or }
import process1.{ id, scan }
import scala.languageFeature.postfixOps
import scala.language.postfixOps
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

  def parseM[A](input: String, p: Parser[A]): Process1[String, A] = {
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

  def stream[A, B](a: Parser[A], b: Parser[B]): Process1[String, B] = {
    def go(r: ParseResult[A]): Process1[String, B] =
      r match {
        case Partial(_)          => receive1Or[String, B](halt) { s => go(r feed s) }
        case Done(input, result) => parseM(input, b)
        case f @ Fail(_, _, msg) => Process.Halt(Cause.Error(new Exception(msg)))
      }
    go(a.parse(""))
  }

  // l33t complex grammar
  lazy val cr: Parser[Char] = char(0x0D)
  lazy val lf: Parser[Char] = char(0x0A)
  lazy val eoln             = opt(cr) ~ lf
  lazy val boundary         = char('☃')
  lazy val delimiter        = boundary ~ eoln
  lazy val thing            = manyUntil(anyChar, delimiter).map(_.mkString)
  lazy val preamble         = opt(manyUntil(anyChar, delimiter))
  lazy val bufferLength     = 6

  def buildInput: Process[Task, String] = {
    
    val del = """☃
"""

    def rubbish: String = if (nextBoolean()) { nextString(nextInt(45)) } else ""
    def body: String = {
      (1 to nextInt(5000)).map { _ =>
        List.fill(nextInt(1000))(nextPrintableChar()).mkString 
      }.mkString(del)
    }

    emit(rubbish + del + body + rubbish) ++ buildInput
  }

  val toEither: String => Either[Int, String] = s => s.length() % 2 match {
    case 1 => Right(s)
    case _ => Right(s) //Left(s.length()) //
  }

  lazy val fromEither: Either[Int, String] => Process[Task, String] = e =>
    e.fold(i => Process.Halt(Cause.End), s => Process.emit(s))

  val logInput: Sink[Task,String] = Process.constant {
    case input ⇒ Task.delay(println(s"INPUT $input"))
  }            

  val logOutput: Sink[Task,String] = Process.constant {
    case output ⇒ Task.delay(println(s"OUTPUT $output"))
  }    
  
  lazy val IVEva = buildInput.
  //observe(logInput).  
  flatMap( i => Process.emitAll(i.grouped(bufferLength).toSeq)).
  map(toEither).
  flatMap(fromEither).
  pipe(stream(preamble, thing)).
 // observe(logOutput).
  run.run
  
  IVEva
}