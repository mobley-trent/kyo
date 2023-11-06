package kyo.concurrent

import kyo._
import kyo.ios._
import org.jctools.queues.MpmcUnboundedXaddArrayQueue

import java.util.concurrent.Executors
import scala.annotation.tailrec

import queues._
import fibers._

object channels {

  abstract class Channel[T] { self =>

    def size: Int > IOs

    def offer[S](v: T > S): Boolean > (IOs with S)

    def offerUnit[S](v: T > S): Unit > (IOs with S)

    def poll: Option[T] > IOs

    def isEmpty: Boolean > IOs

    def isFull: Boolean > IOs

    def putFiber[S](v: T > S): Fiber[Unit] > (IOs with S)

    def takeFiber: Fiber[T] > IOs

    def put[S](v: T > S): Unit > (S with IOs with Fibers) =
      putFiber(v).map(_.get)

    def take: T > (IOs with Fibers) =
      takeFiber.map(_.get)
  }

  object Channels {

    def init[T](capacity: Int, access: Access = Access.Mpmc): Channel[T] > IOs =
      Queues.init[T](capacity, access).map { queue =>
        IOs {
          new Channel[T] {

            val u     = queue.unsafe
            val takes = new MpmcUnboundedXaddArrayQueue[Promise[T]](8)
            val puts  = new MpmcUnboundedXaddArrayQueue[(T, Promise[Unit])](8)

            def size    = queue.size
            def isEmpty = queue.isEmpty
            def isFull  = queue.isFull

            def offer[S](v: T > S) =
              v.map { v =>
                IOs[Boolean, S] {
                  try u.offer(v)
                  finally flush()
                }
              }
            def offerUnit[S](v: T > S) =
              v.map { v =>
                IOs[Unit, S] {
                  try {
                    u.offer(v)
                    ()
                  } finally flush()
                }
              }
            val poll =
              IOs {
                try u.poll()
                finally flush()
              }

            def putFiber[S](v: T > S) =
              v.map { v =>
                IOs[Fiber[Unit], S] {
                  try {
                    if (u.offer(v)) {
                      Fibers.value(())
                    } else {
                      val p = Fibers.unsafeInitPromise[Unit]
                      puts.add((v, p))
                      p
                    }
                  } finally {
                    flush()
                  }
                }
              }

            val takeFiber =
              IOs {
                try {
                  u.poll() match {
                    case Some(v) =>
                      Fibers.value(v)
                    case None =>
                      val p = Fibers.unsafeInitPromise[T]
                      takes.add(p)
                      p
                  }
                } finally {
                  flush()
                }
              }

            @tailrec private def flush(): Unit = {
              // never discards a value even if fibers are interrupted
              // interrupted fibers are automatically discarded
              val queueSize  = u.size()
              val takesEmpty = takes.isEmpty()
              val putsEmpty  = puts.isEmpty()
              if (queueSize > 0 && !takesEmpty) {
                val p = takes.poll()
                if (p != null.asInstanceOf[Promise[T]]) {
                  u.poll() match {
                    case None =>
                      takes.add(p)
                    case Some(v) =>
                      if (!p.unsafeComplete(v) && !u.offer(v)) {
                        val p2 = Fibers.unsafeInitPromise[Unit]
                        puts.add((v, p2))
                      }
                  }
                }
                flush()
              } else if (queueSize < capacity && !putsEmpty) {
                val t = puts.poll()
                if (t != null) {
                  val (v, p) = t
                  if (u.offer(v)) {
                    p.unsafeComplete(())
                  } else {
                    puts.add(t)
                  }
                }
                flush()
              } else if (queueSize == 0 && !putsEmpty && !takesEmpty) {
                val t = puts.poll()
                if (t != null) {
                  val (v, p) = t
                  val p2     = takes.poll()
                  if (p2 != null && p2.unsafeComplete(v)) {
                    p.unsafeComplete(())
                  } else {
                    puts.add(t)
                  }
                }
                flush()
              }
            }
          }
        }
      }
  }
}
