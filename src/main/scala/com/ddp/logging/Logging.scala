package com.ddp.logging

import java.lang.management.ManagementFactory

import org.slf4j.LoggerFactory

/**
  * Created by vagrant on 8/29/17.
  */
trait Logging extends Serializable {

  System.setProperty("PID", ManagementFactory.getRuntimeMXBean.getName.split('@')(0))

  @transient lazy val logger = LoggerFactory.getLogger(getClass.getName)

  /**
    * Print exception stack into log
    */
  def logError(e: Throwable) = logger.error(e.getMessage, e)
}
