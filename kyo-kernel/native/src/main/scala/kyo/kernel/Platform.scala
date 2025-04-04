package kyo.kernel

import scala.concurrent.ExecutionContext

object Platform:

    val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
    val isJVM: Boolean                     = false
    val isJS: Boolean                      = false
    val isNative: Boolean                  = true
    val isDebugEnabled: Boolean            = false
    def exit(code: Int): Unit              = java.lang.System.exit(code)
end Platform
