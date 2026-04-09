package freechips.rocketchip.util

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._

import org.chipsalliance.cde.config._


trait CoreFuzzingConstants{
    // Bits used to store the TAG
    val iftTagWidth = 1

    // counter for uops — 20 bits gives 1 048 576 unique values (~5x the longest
    // current workload, spectre_v1 at ~203 K instruction events), providing
    // comfortable headroom against wrapping.  16-bit (65 536) wrapped ~3x in spectre_v1.
    val uopIDCounterWidthCF = 16

    // tags for each module
    val numModules = 24
    // tainting info for uops
    val icacheTagCF = 0
    val itlbTagCF = 1
    val l0BTBTagCF = 2
    val l1BTBTagCF = 3
    val btbTagCF = 4
    val bpdTagCF = 5
    val rasTagCF = 6
    val ftqTagCF = 7
    val fbTagCF = 8
    val irfTagCF = 9
    val frfTagCF = 10
    val intissqTagCF = 11
    val fpissqTagCF = 12
    val memissqTagCF = 13
    val robTagCF = 14
    val ldqTagCF = 15
    val stqTagCF = 16
    val dtlbTagCF = 17
    val dcacheTagCF = 18
    // Functional unit types (added for cf_fu_bitmap tracking)
    val aluTagCF    = 19
    val mulTagCF    = 20
    val divTagCF    = 21
    val fpuTagCF    = 22
    val csrTagCF    = 23

    // Number of influencer slots per uop (Phase 2).
    // All slots are dynamic (addInfluencer, addInfluencerBatch, wb_resps merge).
    // Pipeline-flush info is tracked as separate fl/floc fields (like spec_atk/spec_oc),
    // not as an influencer slot, so no slot is reserved.
    val numInfluencerSlotsCF = 3

    // Influence type encoding (5-bit; stored in InfluencerEntry.infl_type)
    val INFL_REG_DATAFLOW     = 0   // victim reads physical reg written by attacker uop
    val INFL_STL_FORWARD      = 1   // store-to-load forwarding across domains
    val INFL_ISSUE_CONTENTION = 2   // all issue ports taken by other-domain uops
    val INFL_MEM_HOL          = 3   // LDQ/STQ commit-head from other domain blocks younger entry
    val INFL_REG_PRESSURE     = 4   // rename stall: other domain holds free regs
    val INFL_ROB_FULL         = 5   // ROB full of other-domain entries stalls dispatch
    val INFL_LDQ_FULL         = 6   // LDQ full stalls dispatch
    val INFL_STQ_FULL         = 7   // STQ full stalls dispatch
    val INFL_MEM_ORDER        = 8   // load re-executed due to conflicting store (order_fail)
    val INFL_BPD_STATE        = 9   // TAGE prediction used row last updated by other domain
    val INFL_BTB_STATE        = 10  // BTB entry written by other domain
    val INFL_RAS_STATE        = 11  // RAS entry pushed by other domain
    val INFL_CACHE_EVICTION   = 12  // dcache line evicted by other-domain miss
    val INFL_PIPELINE_FLUSH   = 13  // squash/flush caused by other-domain branch/exception
    val INFL_DTLB_STATE       = 14  // DTLB entry brought in by other-domain miss
    val INFL_ITLB_STATE       = 15  // ITLB entry brought in by other-domain fetch
    val INFL_ICACHE_STATE     = 16  // ICache line evicted by other-domain fetch
    val INFL_MEM_DATAFLOW     = 17  // attacker-committed store data read by victim load (cache line ownership)
    val inflTypeWidthCF       = 5   // bits to hold up to 31 influence types

    // Bit widths of reconfiguration control wires
    // we support 4 parameter reconfiguration and each field is 8 bit wide
    val dcacheParamsWidthCF = 8 

    // custom ROB entry options — index 0 is the largest (hardware-built size).
    // numRobEntries in WithFuzzingBoom must equal robEntryOptions(0) = 256.
    // possible entry configs are:
    // 0 : 256  (default; maximum — hardware is built at this size)
    // 1 : 192
    // 2 : 128
    // 3 : 96
    // 4 : 64
    // 5 : 32
    def robEntryOptions = Seq(256, 192, 128, 96, 64, 32)
    // itlb sets is fixed to 1. Not messing with it in fear of timing. defined in rocket>ICache.scala
    // fixed superpage entries = 4
    // same with dtlb. Degined in HellaCache.scala
    def itlbWayOptions = Seq(32, 16, 8, 4, 2, 1)
    def dtlbWays = Seq(32, 16, 8, 4, 2, 1)
    def coreWidthOptions = Seq(4, 2, 1) // index 0=4 (default), 1=2, 2=1; index 3 is reserved
    def pregFileSizeOptions = Seq(192, 128, 96, 64, 48) // 256 is default

    // branch predictor options
    // Table 3 is reserved as the GShare bank (active only when cf_bpd_tage_to_gshare=1).
    // In normal TAGE mode the active set is {0,1,2,4,5,6} — table 3 is always excluded.
    // The option values are therefore shifted by 1 for indices that span past table 3:
    //   idx=0 → cf_tage_active=7 → tables 0,1,2,4,5,6 active = 6 real TAGE tables
    //   idx=1 → cf_tage_active=6 → tables 0,1,2,4,5   active = 5 real TAGE tables
    //   idx=2 → cf_tage_active=5 → tables 0,1,2,4     active = 4 real TAGE tables
    //   idx=3 → cf_tage_active=3 → tables 0,1,2        active = 3 real TAGE tables
    //   idx=4 → cf_tage_active=2 → tables 0,1          active = 2 real TAGE tables
    //   idx=5 → cf_tage_active=1 → table  0            active = 1 real TAGE table
    // (Value 4 is skipped because i<4 and i≠3 gives the same set as i<3.)
    // In GShare mode (cf_bpd_tage_to_gshare=1), only table 3 is used; all indices
    // 0-2 (cf_tage_active ≥ 5) enable it; indices 3-5 disable it (base predictor only).
    def tagetableCountOptions = Seq(7, 6, 5, 3, 2, 1)
    def rasEntryCountOptions = Seq(32, 16, 8, 4)
    def btbSetOptions = Seq(128, 64, 32)
    def btbWayOptions = Seq(2, 1)


    def fetchBufferEntryOptions = Seq(64, 48, 32, 24, 16, 8)
    def issueQueueEntryOptions = Seq(32, 24, 16, 8)
    def ldQueueEntryOptions = Seq(48, 32, 24, 16, 8)
    def stQueueEntryOptions = Seq(48, 32, 24, 16, 8)
    def ftQueueEntryOptions = Seq(32, 24, 16, 8)
    // cache options — index 0 is always the hardware-built (maximum) size.
    // DCache: nSets=128, nWays=8 in WithFuzzingBoom
    // ICache: nSets=64,  nWays=8 in WithFuzzingBoom
    // Block size (cacheBlockBytes) is NOT reconfigurable per user requirement.
    def dcacheSetOptions = Seq(128, 64, 32, 16)  // index 0 = 128 = nSets for DCache
    def icacheSetOptions = Seq(64, 32, 16, 8)    // index 0 = 64  = nSets for ICache
    def cacheWayOptions  = Seq(8, 4, 2, 1)         // shared by both; index 0 = 8 = nWays
    // Legacy aliases (keep for backward compat with existing code)
    def setOptions = dcacheSetOptions
    def wayOptions = cacheWayOptions

}

// Tagger class removed — was dead code (never instantiated, hardcoded to 0).
// Address-range tagging is now handled by the cf_attacker/cf_secret CSRs.