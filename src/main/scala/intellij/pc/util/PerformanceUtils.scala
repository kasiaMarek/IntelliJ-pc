package intellij.pc.util

import com.intellij.openapi.diagnostic.Logger

import java.time.{Duration, Instant}

object PerformanceUtils {
  private val logger = Logger.getInstance(getClass.getName)

  def timed[T](text: String)(fn: => T): T = {
    val start = Instant.now()
    val res = fn
    val end = Instant.now()
    val duration = Duration.between(start, end)
    logger.warn(s"$text took $duration to complete.")
    res
  }

}
