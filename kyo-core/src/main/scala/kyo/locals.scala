package kyo

import core._
import frames._
import ios._

object locals {

  trait Local[T] {

    import Locals._

    def default: T

    /*inline(3)*/
    def get(using /*inline(3)*/ fr: Frame["Local.get"]): T > IOs =
      new KyoIO[T, Nothing] {
        def frame = fr
        def apply(v: Unit, s: Safepoint[IOs], l: Locals.State) =
          l.getOrElse(Local.this, default).asInstanceOf[T]
      }

    /*inline(3)*/
    def let[U, S](v: T)(f: U > S)(using /*inline(3)*/ fr: Frame["let"]): U > (S | IOs) = {
      type M2[_]
      type E2 <: Effect[M2]
      def loop(f: U > S): U > S =
        f match {
          case kyo: Kyo[M2, E2, Any, U, S] @unchecked =>
            new KyoCont[M2, E2, Any, U, S](kyo) {
              def frame = fr
              def apply(v2: Any, s: Safepoint[E2], l: Locals.State) =
                loop(kyo(v2, s, l.updated(Local.this, v)))
            }
          case _ =>
            f
        }
      IOs(loop(f))
    }
  }

  object Locals {

    type State = Map[Local[_], Any]

    object State {
      def empty: State = Map.empty
    }
    /*inline(3)*/
    def make[T](defaultValue: T)(using /*inline(3)*/ fr: Frame["Locals.make"]): Local[T] =
      new Local[T] {
        def default           = defaultValue
        override def toString = s"Local($fr)"
      }

    /*inline(3)*/
    def save(using /*inline(3)*/ fr: Frame["Locals.save"]): State > IOs =
      new KyoIO[State, Nothing] {
        def frame = fr
        def apply(v: Unit, s: Safepoint[IOs], l: Locals.State) =
          l
      }

    /*inline(3)*/
    def restore[T, S](st: State)(f: T > S)(
        using /*inline(3)*/ fr: Frame["Locals.restore"]
    ): T > (S | IOs) =
      type M2[_]
      type E2 <: Effect[M2]
      def loop(f: T > S): T > S =
        f match {
          case kyo: Kyo[M2, E2, Any, T, S] @unchecked =>
            new KyoCont[M2, E2, Any, T, S](kyo) {
              def frame = fr
              def apply(v2: Any, s: Safepoint[E2], l: Locals.State) =
                loop(kyo(v2, s, l ++ st))
            }
          case _ =>
            f
        }
      IOs(loop(f))
  }
}