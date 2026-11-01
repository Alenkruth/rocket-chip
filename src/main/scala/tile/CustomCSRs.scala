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

  // CSR to quiesce the pipeline to intiate a reconfiguration
  protected def chillCSRIdCF = 0x7c5
  protected def chillCSRCF: Option[CustomCSR] = None

  // CSR for attacker address start
  protected def attackerAddrStartCSRIdCF = 0x7c6
  protected def attackerAddrStartCSRCF: Option[CustomCSR] = None

  // CSR for attacher address end
  protected def attackerAddrEndCSRIdCF = 0x7c7
  protected def attackerAddrEndCSRCF: Option[CustomCSR] = None

  // CSR for secret address start
  protected def secretAddrStartCSRIdCF = 0x7c8
  protected def secretAddrStartCSRCF: Option[CustomCSR] = None

  // CSR for secret address end
  protected def secretAddrEndCSRIdCF = 0x7c9
  protected def secretAddrEndCSRCF: Option[CustomCSR] = None

  // CSR for passing attack stage to the core
  protected def attackStageCSRIdCF = 0x7ca
  protected def attackStageCSRCF: Option[CustomCSR] = None

  // CSR for L1 D$ cache control (RESTORE-ONLY: discards dirty data)
  //   bit 1: invalidate-all walk — drops every L1 D$ line with NO writeback.
  //   Used only by the checkpoint-restore flow (DTM) and r30; using it during
  //   normal execution loses committed stores.
  protected def cachectlCSRIdCF = 0xbc4
  protected def cachectlCSRCF: Option[CustomCSR] = None

  // Read-only D$ status CSR (hardware-published every cycle; sw writes ignored):
  //   [10:0] dirty_count (tied 0 until dirty accounting lands)
  //   [11]   reconf_blocked (tied 0 until guard lands)
  //   [15:12] applied set/way idx, [16] cfg_pending (tied 0 for now)
  //   [17]   l1 wipe busy (invalidate walker active)
  protected def dcacheStatusCSRIdCF = 0xbcd
  protected def dcacheStatusCSRCF: Option[CustomCSR] = None

  // Read-only IFT statistics CSR (hardware-published every cycle; sw writes are overwritten
  // the next cycle).  Saturating 16-bit counters since reset:
  //   [15:0]  squash records lost (squash FIFO full or ROB row reused before its drain) (D6)
  //   [31:16] third same-type update drops at the commit/squash readouts (D19)
  //   [47:32] the row-reuse share of [15:0]
  //   [48]    ift_squash_ovf: sticky, a squash record has been lost since reset
  protected def iftStatsCSRIdCF = 0xbce
  protected def iftStatsCSRCF: Option[CustomCSR] = None

  // Hardware-defense CSRs (defense-clean-plans *-plan-v5.md). None unless the BOOM core overrides them. Ids fixed (owner ALL-1, SA-V4).
  protected def sttCtlCSRIdCF           = 0xbd0   // STT_CTL: [0] en [1] fut [2] sta_notx [4] exp_only [5] delay_exec [6] dbg (mask 0x77)   // [v6]
  protected def sttCtlCSRCF: Option[CustomCSR] = None
  protected def sttStatCSRIdCF          = 0xbd1   // STT_STAT (RO, hw-written): [31:0] held-slot cycles [63:32] STL-held search cycles
  protected def sttStatCSRCF: Option[CustomCSR] = None
  protected def doppelgangerCtlCSRIdCF  = 0xbd2   // DOPPELGANGER_CTL: [0] en (mask 0x1)
  protected def doppelgangerCtlCSRCF: Option[CustomCSR] = None
  protected def doppelgangerStatCSRIdCF = 0xbd3   // DOPPELGANGER_STAT (RO): [11:0] issued [23:12] released [35:24] dead [47:36] mispred [59:48] train drops
  protected def doppelgangerStatCSRCF: Option[CustomCSR] = None
  protected def specasanCtlCSRIdCF      = 0xbd4   // SPECASAN_CTL: [0] EN (tag checks; implies TBI) [1] TBI (top-byte ignore only) (mask 0x3)
  protected def specasanCtlCSRCF: Option[CustomCSR] = None
  protected def specasanPaintCSRIdCF    = 0xbd5   // SPECASAN_PAINT: write {color[63:60], granule paddr} = the tag store (STG)
  protected def specasanPaintCSRCF: Option[CustomCSR] = None
  protected def cevicheCtlCSRIdCF       = 0xbd6   // CEVICHE_CTL: [0] en [4:1] pol0 RWIS [8:5] pol1 [19:9] hard_d [30:20] soft_d [40:31] hard_i [50:41] soft_i
  protected def cevicheCtlCSRCF: Option[CustomCSR] = None
  protected def specasanStatCSRIdCF     = 0xbd7   // SPECASAN_STAT (RO, hw-written): specasan-plan-v5.md section 6
  protected def specasanStatCSRCF: Option[CustomCSR] = None

  // CSR for FTQ size reconfiguration (2-bit index into ftQueueEntryOptions)
  protected def ftqSizeCSRIdCF = 0xbc5
  protected def ftqSizeCSRCF: Option[CustomCSR] = None

  // CSR for RAS entry count reconfiguration (2-bit index into rasEntryCountOptions)
  protected def rasCountCSRIdCF = 0xbc6
  protected def rasCountCSRCF: Option[CustomCSR] = None

  // CSR for physical register file size reconfiguration (3-bit index into pregFileSizeOptions)
  protected def pregSizeCSRIdCF = 0xbc7
  protected def pregSizeCSRCF: Option[CustomCSR] = None

  // CSR for issue queue size reconfiguration (2-bit index into issueQueueEntryOptions)
  protected def issueQueueCSRIdCF = 0xbc8
  protected def issueQueueCSRCF: Option[CustomCSR] = None

  // CSR for BTB set/way reconfiguration (bits[1:0]=sets index, bit[2]=ways index)
  protected def btbConfigCSRIdCF = 0xbc9
  protected def btbConfigCSRCF: Option[CustomCSR] = None

  // CSR for TAGE active table count reconfiguration (3-bit index into tagetableCountOptions)
  protected def tageCountCSRIdCF = 0xbca
  protected def tageCountCSRCF: Option[CustomCSR] = None

  // CSR for ICache set/way reconfiguration (bits[1:0]=set index, bits[3:2]=way index)
  //   bits [1:0]: index into icacheSetOptions = Seq(64, 32, 16, 8)
  //   bits [3:2]: index into cacheWayOptions  = Seq(8, 4, 2, 1)
  protected def icacheCSRIdCF = 0xbcb
  protected def icacheCSRCF: Option[CustomCSR] = None

  // CSR for runtime core-width reconfiguration (2-bit index into {4, 2, 1} width options)
  //   index 0 = N=4 (full width, default), index 1 = N=2, index 2 = N=1
  protected def coreWidthCSRIdCF = 0xbcc
  protected def coreWidthCSRCF: Option[CustomCSR] = None

  // If you override this, you'll want to concatenate super.decls
  def decls: Seq[CustomCSR] = bpmCSR.toSeq ++ chickenCSR ++ bpdCSRCF ++
                              dcacheCSRCF ++ debugCSRCF ++ robSizeCSRCF ++
                              fetchBufferCSRCF ++ ldqStqCSRCF ++
                              chillCSRCF ++ attackStageCSRCF ++ attackerAddrStartCSRCF ++
                              attackerAddrEndCSRCF ++ secretAddrStartCSRCF ++ secretAddrEndCSRCF ++
                              ftqSizeCSRCF ++ rasCountCSRCF ++ pregSizeCSRCF ++
                              issueQueueCSRCF ++ btbConfigCSRCF ++ tageCountCSRCF ++
                              icacheCSRCF ++ coreWidthCSRCF ++ cachectlCSRCF ++ dcacheStatusCSRCF ++
                              iftStatsCSRCF ++ sttCtlCSRCF ++ sttStatCSRCF ++ doppelgangerCtlCSRCF ++ doppelgangerStatCSRCF ++
                              specasanCtlCSRCF ++ specasanPaintCSRCF ++ cevicheCtlCSRCF ++ specasanStatCSRCF

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
