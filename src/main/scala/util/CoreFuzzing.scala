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

    // [RECV3] record v3 unit bitmap (design record-v3-sink-design-20261025.md section 3.2): one bit per real unit the
    // instruction visited, each with a firing writer; no fetch-side constants and no repurposed bits (sftq is its own
    // record field).  Bits 10-14 are reserved 0 (owner N-D1 a).  The v1 names (numModules = 24, icacheTagCF ..
    // csrTagCF) are deleted; every writer is renamed by spec SP-09 (compile-driven).
    val numModules   = 15
    val UNIT_INT_IQ  = 0   // dispatched to the integer issue queue (iq_type bit 0); stamped on the ROB enqueue copy
    val UNIT_MEM_IQ  = 1   // dispatched to the memory issue queue (iq_type bit 1; IQT_MFP included)
    val UNIT_LDQ     = 2   // occupied an LDQ entry (LDQ enqueue)
    val UNIT_STQ     = 3   // occupied an STQ entry (STQ enqueue)
    val UNIT_STQ_FWD = 4   // load whose data came from the store-to-load forwarding path
    val UNIT_DC      = 5   // load answered by the D$ (any path); store: its STQ-commit access reaches the D$ (pre-mark)
    val UNIT_MSHR    = 6   // load answered through the MSHR file or an IOMSHR (record dc_kind 2 or 3)
    val UNIT_ALU     = 7   // executed in the ALU / pipelined multiplier (FU_ALU or FU_MUL; conditional branches are FU_ALU)
    val UNIT_DIV     = 8   // executed in the iterative divider (FU_DIV)
    val UNIT_JMP     = 9   // executed in the jump unit (FU_JMP: jal, jalr)
    require(UNIT_JMP < numModules, "[RECV3] unit bit outside the 15-bit unit field")

    // Number of influencer slots per uop (Phase 2).
    // All slots are dynamic (addInfluencer, addInfluencerBatch, wb_resps merge).
    // Pipeline-flush info is tracked as separate fl/floc fields (like spec_atk/spec_oc),
    // not as an influencer slot, so no slot is reserved.
    //
    // Raised 3 -> 4 alongside the entry narrowing (28b -> 16b, see inflOpCountWidthCF),
    // so 4 slots now cost less than 3 did.  3 was measurably binding, and the bias it
    // introduced was not uniform: candidates fill slots SEQUENTIALLY in the order listed
    // at core.scala's addInfluencerBatch call, where INFL_MEM_HOL is last of seven, and
    // fetch (BTB/BPD/RAS_STATE) has already consumed slots before dispatch runs.  So the
    // influence census systematically under-counts whatever is added late in the pipeline.
    // Measured on run_00016: cf_infl_overflow set on 15.4% of all records and 40.3% of
    // secret-carrying ones, with MEM_HOL still reaching 323,790 entries -- a floor, not a
    // measurement, and top_undefended is ranked on those counts.
    val numInfluencerSlotsCF = 4

    // [RECV3] influencer slot key width (design 2.4, owner D12 "agreed"): 12 bits.  OC-keyed codes store the producer's
    // op_count[11:0] (host: producer = own - ((own - key) & 0xFFF), exact while the distance < 4,096); PC-keyed codes
    // store PC[13:2] whole (one code per type, nothing folded into the type code); NONE-keyed codes store 0.
    // Replaces inflOpCountWidthCF (10, deleted: every user is renamed by spec SP-06).
    val inflKeyWidthCF = 12

    // Width of cf_spec_branch_op_id, likewise storing only the LOW bits of the
    // speculating branch's op_count_id.  Bounded by construction -- the distance cannot
    // exceed the speculation window -- and measured max across five workloads is 76
    // (fr 65, retbleed 65, ret2spec 76, spectre_v1 54, spectre_v3 13), so 8 bits carries
    // a 3.4x margin.  Unlike the influencer field there is no unbounded mechanism here.
    val specBranchOpIdWidthCF = 8

    // [RECV3] record v3 influence-type code map (design 2.5, owner D13 "yes"): 6 bits, 0 = EMPTY, no code reuse.
    // Families by the high bits: 0x0x dataflow, 0x1x queue/structural stalls, 0x2x port/arbiter contention,
    // 0x3x predictor and cache state.  Every v1 numeric value is replaced.  The v1-only names (INFL_PIPELINE_FLUSH,
    // INFL_DTLB_STATE, INFL_ITLB_STATE, INFL_BTB_TARGET_INJECT, INFL_CACHE_EVICTION_1..3, INFL_MEM_DATAFLOW_1..3,
    // INFL_BTB_STATE_0..3, inflPcTypeCodes, inflPcCode) are deleted: the 12-bit key carries PC[13:2] whole, so a
    // PC-keyed type has ONE code.  0x26 (DC_WRPORT in design revision 3) is retired and never reused.
    // Key kinds (comment column): OC = producer op_count[11:0], PC = PC[13:2], NONE = 0.
    val inflTypeWidthCF        = 6
    val INFL_EMPTY             = 0x00  // -     empty slot (the v3 record has no slot valid bit)
    val INFL_REG_DATAFLOW      = 0x01  // OC    value dataflow through a register (the sprob / store-operand marks carry key 0)
    val INFL_STL_FORWARD       = 0x02  // OC    store-to-load forwarding (key = the store)
    val INFL_STL_PARTIAL       = 0x03  // OC    load replayed behind a partially overlapping tagged store / older load
    val INFL_MEM_DATAFLOW      = 0x04  // PC    load read a doubleword last stored by a tagged store (key = the storer's PC)
    val INFL_MEM_ORDER         = 0x05  // OC    load squashed by a memory-ordering failure (key = the store / load)
    val INFL_LRSC              = 0x06  // OC    SC used a reservation set by a tagged LR (key = the LR)
    val INFL_CONTROL_FLOW      = 0x07  // NONE  implicit flow under the secret window (key 0; the parser names the arming branch)
    val INFL_ROB_FULL          = 0x10  // OC    dispatch stalled by a full ROB (key = the ROB head)
    val INFL_LDQ_FULL          = 0x11  // OC    dispatch stalled by a full LDQ (key = the LDQ head)
    val INFL_STQ_FULL          = 0x12  // OC    dispatch stalled by a full STQ (key = the STQ head)
    val INFL_IQ_FULL           = 0x13  // OC    dispatch stalled by a full issue queue (key = its stalling occupant)
    val INFL_FTQ_FULL          = 0x14  // OC    fetch starved by a full FTQ (the patch-59 edge, own code; key = the captured blocker)
    val INFL_REG_PRESSURE      = 0x15  // OC    rename stalled on free registers (key = the stuck head)
    val INFL_MEM_HOL           = 0x16  // OC    LDQ/STQ head-of-line blocking (key = the head)
    val INFL_DRAIN_WAIT        = 0x17  // OC    serializing uop (is_unique: CSR / fence / fence.i / AMO / LR / SC / xRET / wfi;
                                       //       every uop under disableOOO) held at dispatch by the ROB / STQ / MSHR drain
                                       //       (key = the last flagged blocker: a ROB head, the STQ head or an MSHR's primary)
                                       //       [owner 2026-11-04, RULES C "Drain edges U6 + U6b": own code; 0x10 / 0x16 unchanged]
    val INFL_ISSUE_CONTENTION  = 0x20  // OC    issue port denied to a ready uop (key = the winner)
    val INFL_FU_BUSY           = 0x21  // OC    non-pipelined unit busy (key = the occupant)
    val INFL_DC_BANKCONF       = 0x22  // OC    D$ data-bank conflict nack (key = the older lane's access)
    val INFL_DC_NACK           = 0x23  // PC    D$ nack while a tagged MSHR/WB access was in flight (key = that access's PC)
    val INFL_LSU_LANE          = 0x24  // OC    LSU lane lost to a higher-priority candidate (key = the winner; spec SP-20)
    val INFL_DC_PORT_BLOCK     = 0x25  // OC    D$ request port refused (key = the winning MSHR's primary op_count, or 0: prober / writeback / hold-off; spec SP-20)
    //                           0x26     retired (design revision 4, audit G6): never reuse
    val INFL_LOAD_PAUSE        = 0x27  // NONE  mem issue paused by LDQ saturation (key 0; spec SP-20)
    val INFL_BPD_STATE         = 0x30  // OC    prediction from a TAGE/BIM row trained by the other domain / by a secret
    val INFL_BTB_STATE         = 0x31  // NONE  BTB/uBTB entry trained by the other domain (fetch flag 5, domain mismatch; key 0)
    val INFL_RAS_STATE         = 0x32  // OC    RAS entry pushed by the other domain, or a stale pop
    val INFL_ICACHE_STATE      = 0x33  // OC    I$ line brought in by the other domain / a secret fetch
    val INFL_CACHE_EVICTION    = 0x34  // PC    D$ eviction / residency (key = the filler / marker PC)
    val INFL_REPL_STATE        = 0x35  // PC    D$ eviction attributed through the set's replacement state (key = the advancer PC); replaces v1 record bit [254]
    val INFL_BTB_DISPLACE      = 0x36  // PC    hit on a secret-trained BTB/uBTB entry, or fetch of a tag a secret / attacker allocation
                                       //       displaced (fetch flags 7 / 8; sec = flag 7, atk = flag 8; key = the allocator / trainer PC)
                                       //       [controller decision 2026-10-25, stream B gap B-2 option (a): own code, was BTB_STATE sec=1]
    // Key kind per code (design 2.5 column "key").  Hardware use: only OC-keyed candidates take part in the
    // oc_aliased range check (util.scala, spec SP-06).  Host use: the decoder's key printing (spec SP-11).
    val KEY_OC       = 0
    val KEY_PC       = 1
    val KEY_NONE     = 2
    val inflKeyKindMap: Map[Int, Int] = Map(
      INFL_EMPTY -> KEY_NONE,
      INFL_REG_DATAFLOW -> KEY_OC, INFL_STL_FORWARD -> KEY_OC, INFL_STL_PARTIAL -> KEY_OC, INFL_MEM_DATAFLOW -> KEY_PC,
      INFL_MEM_ORDER -> KEY_OC, INFL_LRSC -> KEY_OC, INFL_CONTROL_FLOW -> KEY_NONE,
      INFL_ROB_FULL -> KEY_OC, INFL_LDQ_FULL -> KEY_OC, INFL_STQ_FULL -> KEY_OC, INFL_IQ_FULL -> KEY_OC,
      INFL_FTQ_FULL -> KEY_OC, INFL_REG_PRESSURE -> KEY_OC, INFL_MEM_HOL -> KEY_OC, INFL_DRAIN_WAIT -> KEY_OC,
      INFL_ISSUE_CONTENTION -> KEY_OC, INFL_FU_BUSY -> KEY_OC, INFL_DC_BANKCONF -> KEY_OC, INFL_DC_NACK -> KEY_PC,
      INFL_LSU_LANE -> KEY_OC, INFL_DC_PORT_BLOCK -> KEY_OC, INFL_LOAD_PAUSE -> KEY_NONE,
      INFL_BPD_STATE -> KEY_OC, INFL_BTB_STATE -> KEY_NONE, INFL_RAS_STATE -> KEY_OC, INFL_ICACHE_STATE -> KEY_OC,
      INFL_CACHE_EVICTION -> KEY_PC, INFL_REPL_STATE -> KEY_PC, INFL_BTB_DISPLACE -> KEY_PC)
    def inflKeyKind(code: Int): Int = {
      require(inflKeyKindMap.contains(code), s"[RECV3] influence code 0x${code.toHexString} is not in the v3 code map")
      inflKeyKindMap(code)
    }
    require(inflKeyKindMap.size == 30, "[RECV3] the v3 code map has 30 codes (EMPTY included: design 2.5 + 0x36 BTB_DISPLACE + 0x17 DRAIN_WAIT)")
    require(inflKeyKindMap.keys.forall(c => c >= 0 && c < (1 << inflTypeWidthCF)) && !inflKeyKindMap.contains(0x26),
      "[RECV3] code outside 6 bits, or the retired 0x26 reused")

    // width of the PC slice a D$ line / BTB record remembers about its filler / last storer / trainer
    val inflPcBitsCF          = 12  // PC[13:2]
    require(inflKeyWidthCF == inflPcBitsCF, "[RECV3] a PC-keyed slot stores PC[13:2] whole in the 12-bit key")

    // [RECV3] record classes (design 2.2): bits [3:0] of every 256-bit record; class[3] = 1 means state record.
    // 4-7 and 9-15 are reserved (the decoder rejects them).
    val REC_NOP    = 0
    val REC_COMMIT = 1
    val REC_SQUASH = 2
    val REC_KILL   = 3
    val REC_STATE  = 8
    // SQUASH record kind, bits [7:4] (design 2.6)
    val SQ_KIND_BRKILL   = 0   // branch kill (v1 [FLUSH] SRC=3)
    val SQ_KIND_ROLLBACK = 1   // exception rollback (v1 [FLUSH] SRC=4)
    // STATE record header group-valid bit positions (design 5.1); [8] is dbg, [15:12] reserved 0
    val STATE_GV_R     = 4
    val STATE_GV_M     = 5
    val STATE_GV_L     = 6
    val STATE_GV_IC    = 7
    val STATE_GV_RAS   = 9
    val STATE_GV_PRED  = 10
    val STATE_GV_EPOCH = 11

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
    // NOTE: the TLBs are NOT runtime-reconfigurable.  itlb sets are fixed at 1 with 4
    // superpage entries (rocket/ICache.scala), and the dtlb likewise (HellaCache.scala).
    // Way-count vectors for both used to be declared here, but nothing in the RTL ever
    // consumed them -- they advertised a reconfiguration dimension that does not exist,
    // and one of them listed a range the fuzzer's own itlbNWays list disagreed with.
    // Removed 2026-08-29.  dtlbNWays/itlbNWays remain in csr_map.REBUILD_REQUIRED, which
    // is the accurate statement: changing them needs a resynthesis, not a CSR write.
    def coreWidthOptions = Seq(4, 2, 1) // index 0=4 (default), 1=2, 2=1; index 3 is reserved
    // INT and FP PRF reconfiguration share ONE index (cf_preg_idx, CSR 0xbc7), so
    // the two lists MUST stay the same length and a rung cannot be removed from one
    // without removing it from the other.
    //
    // The 5th rung keeps its INT size (48) but its FP size is CLAMPED to the floor
    // (40, same as rung 3) -- 2026-08-28. fp=36 deadlocks the core, and because one
    // index drives both files, dropping the rung would have cost the int=48 sweep
    // point for a fault that has nothing to do with the INT file. Clamping keeps
    // every INT configuration sweepable and makes 40 the lowest FP size reachable.
    // Measured on an otherwise fully default machine (coreWidth 4, ROB 512):
    //
    //     36 FP pregs - 32 architectural (f0..f31 all live)
    //                 -  4 held in RenameFreeList pre-selection r_sel (plWidth = coreWidth)
    //                 =  0 available -> the next FP write never renames -> no commits
    //                    -> core.scala:2083 "Pipeline has hung." after 8192 idle cycles
    //
    // Confirmed both directions, one variable, all else default:
    //     0xbc7=4 (fp 36) -> HANG  (spectre_v1 @1,421,525 cyc; mm.riscv @1,741,815)
    //     0xbc7=3 (fp 40) -> PASS, tohost=3, full run in 1652 s
    // Reproducer without attack code: riscv-tests benchmarks/mm.riscv (1214 FP-dest
    // instrs over 31 distinct FP dest regs). Integer-only benchmarks pass at fp=36,
    // so the INT half was never the problem -- int=48 only went with it because the
    // two share an index.
    //
    // The floor is numLregs + 2*plWidth = 40 at coreWidth 4, NOT 32 + coreWidth:
    // `require(numFpPhysRegs >= 32 + coreWidth)` budgets coreWidth spare pregs, but the
    // freelist pre-selection pipeline permanently holds coreWidth of them. RenameFreeList
    // now enforces this per-option so the rung cannot come back by accident.
    def pregFileSizeOptions   = Seq(192, 128, 96, 64, 48) // INT hardware max = 192
    // NOTE the repeated 40: indices 3 and 4 give the SAME FP size on purpose. 40 is the
    // floor (32 arch + 2*coreWidth); rung 4 exists to sweep int=48, not to shrink FP
    // further. Never let any entry fall below 40 -- RenameFreeList requires it.
    def fpPregFileSizeOptions = Seq( 96,  64, 48, 40, 40) // FP hardware max = 96; floor 40

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
    // Top rung 48 -> 40 (2026-08-29).  Hardware is built at each list's MAXIMUM, so the
    // top rung sets the silicon cost even when a small config is selected.  48 put this
    // core out of family: MegaBoom (width 4) and GigaBoom (width 5) both use 32, and even
    // TeraBoom only reaches 64 at width 8 -- while this is a width-4 core.  The LSU is
    // 213,428 LUTs at a 6.2 LUT:FF ratio, and its age matrices and store-to-load CAM scale
    // roughly with queue depth SQUARED, so the top rung is disproportionately expensive.
    //
    // 40 rather than 32 keeps a distinct top rung above MegaBoom's 32.  Recovers ~30% of
    // the depth-squared logic instead of ~55%.
    //
    // NOTE ON ORDERING -- this list is DESCENDING, but the fuzzer's OPTION_LISTS in
    // base-attacks/fuzzer/evaluator.py is ASCENDING ([8, 16, 24, 32, 48]).  A config's
    // stored index therefore refers to the ASCENDING list: index 0 is the SMALLEST size,
    // not the largest.  Reading a campaign's index vector against this file's ordering
    // inverts every conclusion -- it briefly produced the claim that the 48 rung was the
    // most-used and the one where INFL_ROB_FULL dominated, when in fact index 0 = 8
    // entries and no campaign run ever selected 48.  Cross-check against OPTION_LISTS
    // before drawing conclusions about which rungs earn their area.
    def ldQueueEntryOptions = Seq(40, 32, 24, 16, 8)
    def stQueueEntryOptions = Seq(40, 32, 24, 16, 8)
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