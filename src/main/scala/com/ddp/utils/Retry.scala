package com.ddp.utils

import com.ddp.logging.Logging

import scala.concurrent._
import scala.concurrent.duration.{Deadline, Duration, DurationLong}

object Retry extends Logging {
  /**
    * exponential back off for retry
    */
  def exponentialBackoff(r: Int): Duration = (scala.math.pow(2, r).round * 100) + 200 milliseconds

  def noIgnore(t: Throwable): Boolean = false

  def retry[T](maxRetry: Int,
               deadline: Option[Deadline] = None,
               backoff: (Int) => Duration = exponentialBackoff,
               retryOnThrowable: Throwable => Boolean = noIgnore,
               blockDescription: String = "")(block: => T)(implicit ctx: ExecutionContext): Future[T] = {

    object TooManyRetriesException extends Exception("too many retries without exception")
    object DeadlineExceededException extends Exception("deadline exceded")

    val p = Promise[T]

    def recursiveRetry(retryCnt: Int, exception: Option[Throwable])(f: () => T): Option[T] = {
      if (maxRetry == retryCnt
        || deadline.isDefined && deadline.get.isOverdue) {
        exception match {
          case Some(t) =>
            p failure t
          case None if deadline.isDefined && deadline.get.isOverdue =>
            p failure DeadlineExceededException
          case None =>
            p failure TooManyRetriesException
        }
        None
      } else {
        val success = try {
          val rez = if (deadline.isDefined) {
            // scalastyle:off
            Await.result(Future(f()), deadline.get.timeLeft)
            // scalastyle:on awaitresult
          } else {
            f()
          }
          Some(rez)
        } catch {
          case t: Throwable if retryOnThrowable(t) =>
            val interval = backoff(retryCnt).toMillis
            logger.warn(s"Failed operation: [$blockDescription]. Retry cnt: $retryCnt. Will be retrying after ${interval}ms.. ")
            blocking {
              Thread.sleep(interval)
            }
            recursiveRetry(retryCnt + 1, Some(t))(f)
          case t: Throwable =>
            logger.warn(s"Failed: [$blockDescription]. Won't be retrying.", t)
            p failure t
            None
        }
        success match {
          case Some(v) =>
            p success v
            Some(v)
          case None => None
        }
      }
    }

    def doBlock() = block

    Future {
      recursiveRetry(0, None)(doBlock)
    }

    p.future
  }
}