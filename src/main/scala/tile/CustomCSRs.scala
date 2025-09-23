// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import chisel3._

import org.chipsalliance.cde.config.Parameters

case class CustomCSR(id: Int, mask: BigInt, init: Option[BigInt])

object CustomCSR {
  def constant(id: Int, value: BigInt): CustomCSR = CustomCSR(id, BigInt(0), Some(value))
}

class CustomCSRIO(implicit p: Parameters) extends CoreBundle {
  val ren = Output(Bool())          // set by CSRFile, indicates an instruction is reading the CSR
  val wen = Output(Bool())          // set by CSRFile, indicates an instruction is writing the CSR
  val wdata = Output(UInt(xLen.W))  // wdata provided by instruction writing CSR
  val value = Output(UInt(xLen.W))  // current value of CSR in CSRFile

  val stall = Input(Bool())         // reads and writes to this CSR should stall (must be bounded)

  val set = Input(Bool())           // set/sdata enables external agents to set the value of this CSR
  val sdata = Input(UInt(xLen.W))
}

class CustomCSRs(implicit p: Parameters) extends CoreBundle {
  // Not all cores have these CSRs, but those that do should follow the same
  // numbering conventions.  So we list them here but default them to None.
  protected def bpmCSRId = 0x7c0
  protected def bpmCSR: Option[CustomCSR] = None

  protected def chickenCSRId = 0x7c1
  protected def chickenCSR: Option[CustomCSR] = None

  // CSRs for Core Fuzzing
  protected def bpdCSRIdCF = 0x7c2
  protected def bpdCSRCF: Option[CustomCSR] = None

  // CSR 7c2 needs to be moved within 0xbc0 and 0xbff
  // 0xbc0 - 0xbff has 64 CSRs and they should be sufficient for configuration CSRs

  // cf_dcache_csr
  // id : 0xbc0
  // 0-7 : Sets  (Default - bit 0 - 128; bit 1 - 32; bit 2 - 64)
  // 8-15 : Ways (Default - bit 0 - 16; bit 1 - 1; bit 2 - 2; bit 3 - 4; bit 4 - 8)
  // 16-23 : size (Default - bit 0 - 128; bit 1 - 2; bit 2 - 4; bit 3 - 8; bit 4 - 16; bit 5 - 32; bit 6 - 64)
  // would need to add rowBit reconfiguration - currently fixed to 16B per access, support 8B
  // 24-31 : replacement policy (Default - bit 0 - PseudoLRU, bit 1 - Random)
  protected def dcacheCSRIdCF = 0xbc1
  protected def dcacheCSRCF: Option[CustomCSR] = None

  // CSR cf_debug_log 
  // id : 0xbc1
  // 0 - Core  Fuzzing debug enable
  // 1 - Dcache logs
  // 2 - LSU logs
  // 3 - Core logs
  // 4 - ROB logs
  // 5 - BPD logs
  // 6 - frontend logs
  protected def debugCSRIdCF = 0xbc0
  protected def debugCSRCF: Option[CustomCSR] = None 

  // Add a CSR for ROB size reconfiguration
  // CSR for ROB size reconfiguration (one-hot encoding)
  protected def robSizeCSRIdCF = 0xbc2
  protected def robSizeCSRCF: Option[CustomCSR] = None

  // CSR for cache block size reconfiguration (unsigned integer)
  protected def cacheBlockSizeCSRIdCF = 0xbc3
  protected def cacheBlockSizeCSRCF: Option[CustomCSR] = None

  // CSR for core width reconfiguration (one-hot encoding)
  // protected def coreWidthCSRId = 0xbc3
  // protected def coreWidthCSR: Option[CustomCSR] = {
  //  val mask = boom.common.coreWidthOptionsMask
  //  val init = BigInt(1 << 4) // Default: width 5
  //  Some(CustomCSR(coreWidthCSRId, mask, Some(init)))
  // }

  // Alex-Corefuzzing
  // CSR for changing the size of the fetch-buffer - alex
  protected def fetchBufferCSRIdCF = 0x7c3
  protected def fetchBufferCSRCF: Option[CustomCSR] = None

  // CSR for changing the sizes of load/store queues - alex
  protected def ldqStqCSRIdCF = 0x7c4
  protected def ldqStqCSRCF: Option[CustomCSR] = None

  // If you override this, you'll want to concatenate super.decls
  def decls: Seq[CustomCSR] = bpmCSR.toSeq ++ chickenCSR ++ bpdCSRCF ++
                              dcacheCSRCF ++ debugCSRCF ++ robSizeCSRCF ++
                              cacheBlockSizeCSRCF ++ fetchBufferCSRCF ++ ldqStqCSRCF // ++ coreWidthCSR

  val csrs = Vec(decls.size, new CustomCSRIO)

  def flushBTB = getOrElse(bpmCSR, _.wen, false.B)
  def bpmStatic = getOrElse(bpmCSR, _.value(0), false.B)
  def disableDCacheClockGate = getOrElse(chickenCSR, _.value(0), false.B)
  def disableICacheClockGate = getOrElse(chickenCSR, _.value(1), false.B)
  def disableCoreClockGate = getOrElse(chickenCSR, _.value(2), false.B)
  def disableSpeculativeICacheRefill = getOrElse(chickenCSR, _.value(3), false.B)
  def suppressCorruptOnGrantData = getOrElse(chickenCSR, _.value(9), false.B)

  protected def getByIdOrElse[T](id: Int, f: CustomCSRIO => T, alt: T): T = {
    val idx = decls.indexWhere(_.id == id)
    if (idx < 0) alt else f(csrs(idx))
  }

  protected def getOrElse[T](csr: Option[CustomCSR], f: CustomCSRIO => T, alt: T): T =
    csr.map(c => getByIdOrElse(c.id, f, alt)).getOrElse(alt)
}
