package langoustine.lsp

import cats.MonadThrow
import java.io.InputStream
import java.io.OutputStream
import langoustine.JSONRPC
import java.io.BufferedReader
import java.io.InputStreamReader
import langoustine.JSONRPC.RequestMessage

class Exchange(logger: scribe.Logger):
  private enum State:
    case Start
    case ReceivedContentLength(l: Int)

  import State.*

  def bind(
      in: InputStream,
      out: OutputStream,
      build: RequestMessage => Unit
  ) =
    var state: State = Start
    val reader       = new BufferedReader(new InputStreamReader(in))

    try

      while true do

        println(state)

        state match
          case Start =>
            val line = reader.readLine().trim

            state = line match
              case s"Content-Length: $num" =>
                State.ReceivedContentLength(num.toInt)
              case other =>
                logger.error(s"REceived $line")
                Start

          case ReceivedContentLength(l) =>
            val buf = Array.ofDim[Char](l)
            reader.skip(2)
            reader.read(buf)
            var request: Option[JSONRPC.RequestMessage] = None
            val str                                     = new String(buf)
            try
              request = Some(
                upickle.default.read[JSONRPC.RequestMessage](str)
              )
              request.foreach(request => build(request))
            catch
              case exc =>
                logger.error(
                  s"Failed to execute ${request.map(_.method)}",
                  exc
                )
            end try
            state = Start
        end match

      end while
    catch
      case exc =>
        logger.error(s"Server crashed, boo :(", exc)
    end try
  end bind
end Exchange
