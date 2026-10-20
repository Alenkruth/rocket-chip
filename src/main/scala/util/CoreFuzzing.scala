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

    // Width of InfluencerEntry.op_count.  It stores only the LOW bits of the
    // influencing uop's op_count_id; software reconstructs the full value from the
    // consuming record's own 16-bit op_count:
    //     producer = own - ((own - stored) & ((1 << inflOpCountWidthCF) - 1))
    // Correct whenever the true distance is < 2^inflOpCountWidthCF.  Truncation is free
    // in hardware -- storing a delta instead would need a subtract, which this avoids.
    //
    // WIDTH RATIONALE.  Sized from the TAIL across ten attack workloads, not from one:
    //
    //   workload  p99.99  max     longest-edge type
    //   fr           426  458     INFL_REG_DATAFLOW
    //   ff           382  433     INFL_REG_DATAFLOW
    //   pp           382  402     INFL_CACHE_EVICTION
    //   ridl         420  423     INFL_CACHE_EVICTION
    //   ssb          444  446     INFL_REG_DATAFLOW
    //   spectre_v3   462  498     INFL_CACHE_EVICTION
    //
    // An earlier revision used 8 bits, sized from spectre_v1 alone (99.58% within 256).
    // That was a tuning artifact: on flush+reload and meltdown it aliases 4.5% of edges.
    // 9 bits (512) covers every observed edge but leaves only 2.8% headroom over the
    // 498 maximum, which is not enough for a fuzzer meant to run on unprofiled programs.
    //
    // 10 bits gives ~2x margin.  The binding constraint is INFL_CACHE_EVICTION, which
    // references whatever instruction filled the evicted line -- unbounded by
    // construction once a program has a real working set, and already the longest edge
    // in three of six workloads above.  Aliasing does not drop an edge, it FABRICATES a
    // link to the wrong producer, so margin is worth more here than area: a false edge
    // becomes a false novel flow that someone has to chase.
    val inflOpCountWidthCF = 10

    // Width of cf_spec_branch_op_id, likewise storing only the LOW bits of the
    // speculating branch's op_count_id.  Bounded by construction -- the distance cannot
    // exceed the speculation window -- and measured max across five workloads is 76
    // (fr 65, retbleed 65, ret2spec 76, spectre_v1 54, spectre_v3 13), so 8 bits carries
    // a 3.4x margin.  Unlike the influencer field there is no unbounded mechanism here.
    val specBranchOpIdWidthCF = 8

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
    val INFL_FU_BUSY          = 18  // non-pipelined FU (div/fdivsqrt) occupied by other-domain or secret-dependent op blocks issue
    val INFL_GHIST_STATE      = 19  // global history register still holds a secret-influenced branch outcome (64-branch window)
    val INFL_CONTROL_FLOW     = 20  // uop is control-dependent on a secret branch (implicit flow: which path ran, not which bits moved)

    // [PCPROV 2026-09-15] PC-keyed provenance for the two D$-sourced types.
    //
    // A cache line's age is unbounded, so an op_count (16 b in the line metadata, 10 b
    // in the slot, wrapping every 65,536 uops) is the wrong key for "which instruction
    // filled / last stored this line": any fill older than 2^inflOpCountWidthCF uops was
    // being REFUSED as aliased and the link lost.  The producer's PC never goes stale
    // and maps directly onto the static attack chain.  Attack binaries are <= 13 KB of
    // 4-byte-aligned code (no RVC), so PC[13:2] identifies every instruction; measured
    // on spectre_v1: PC[11:2] alone collides for 302 of 1,045 memory-op PCs, PC[13:2]
    // for none.
    //
    // The slot layout is frozen by the 256-bit IFT bridge record (IFTBridgeIO.scala), so
    // the 12 PC bits are carried WITHOUT widening anything:
    //     slot.op_count[9:0]  = PC[11:2]
    //     slot.infl_type      = base code + PC[13:12]       (2 bits ride in the type)
    // i.e. each D$ type occupies FOUR consecutive codes; the base code (12 / 17) is the
    // pre-existing value and still means PC[13:12] == 0.  Decoding:
    //     base(ty)  = 12 if ty in {12,21,22,23};  17 if ty in {17,24,25,26}
    //     pc[13:2]  = (pcHi(ty) << 10) | slot.op_count,  pcHi = 0 for the base code,
    //                 1,2,3 for the three continuation codes
    // No other type is affected; for every other type slot.op_count is still the
    // producer's low op_count bits and cf_infl_oc_aliased still range-checks it.  The
    // PC-typed slots are exempt from that range check (util.scala InfluencerCandidate.is_pc).
    // [BTBDISP 2026-10-13, series-r step 16] the SECRET BTB edge (FetchIftInfo bit 7: a hit on
    // a secret-trained BTB/uBTB entry or a fetch of a tag a secret allocation displaced) is
    // PC-coded (PC of the secret allocator / trainer) on its OWN four codes, none shared with
    // an ordinary edge: 31 (PC[13:12]=0, owner ruling 114), 14 (=1), 15 (=2), 13 (=3)
    // (ruling 113: retired codes; 13 PIPELINE_FLUSH was never emitted as a slot, 14/15
    // DTLB/ITLB lost their emitters in step 10, ruling 94).  Decoding: ty in {31,14,15,13} ->
    // BTB secret edge, pcHi = {31:0, 14:1, 15:2, 13:3}, PC[13:2] = (pcHi << 10) | op_count.
    // Code 10 (BTB_STATE) is left to the domain-mismatch edge {is_secret=0, op_count 0}.
    // The INFL_PIPELINE_FLUSH / INFL_DTLB_STATE / INFL_ITLB_STATE names stay for old logs only.
    // Code map after step 16: 28 IQ_FULL; 29, 30 free; 19 retired.
    val INFL_CACHE_EVICTION_1 = 21  // CACHE_EVICTION, PC[13:12] = 1
    val INFL_CACHE_EVICTION_2 = 22  // CACHE_EVICTION, PC[13:12] = 2
    val INFL_CACHE_EVICTION_3 = 23  // CACHE_EVICTION, PC[13:12] = 3
    val INFL_MEM_DATAFLOW_1   = 24  // MEM_DATAFLOW,   PC[13:12] = 1
    val INFL_MEM_DATAFLOW_2   = 25  // MEM_DATAFLOW,   PC[13:12] = 2
    val INFL_MEM_DATAFLOW_3   = 26  // MEM_DATAFLOW,   PC[13:12] = 3
    val INFL_BTB_TARGET_INJECT = 27 // BTB-predicted JALR target is cross-domain (branch-target-injection, doc 67 Part A)
    // [IQFULL 2026-10-13, series-r step 16, owner ruling 110] dispatch stalled because an issue
    // queue had no room while it held a secret-tagged (s_acc||s_prop) or, for a victim uop,
    // attacker-domain entry.  op_count = 0 (no single culprit).  The ruling named code 18,
    // but 18 is INFL_FU_BUSY (live: issue-unit-age-ordered.scala); 28 is the first free code.
    val INFL_IQ_FULL          = 28
    val INFL_BTB_STATE_0      = 31  // BTB secret edge, PC[13:12] = 0  (ruling 114: not 10, which ordinary edges use)
    val INFL_BTB_STATE_1      = 14  // BTB_STATE (is_secret=1), PC[13:12] = 1  (retired DTLB_STATE code, ruling 113)
    val INFL_BTB_STATE_2      = 15  // BTB_STATE (is_secret=1), PC[13:12] = 2  (retired ITLB_STATE code)
    val INFL_BTB_STATE_3      = 13  // BTB_STATE (is_secret=1), PC[13:12] = 3  (PIPELINE_FLUSH, never a slot)
    // Scala-time helpers so RTL never hand-writes the arithmetic.
    val inflPcTypeCodes: Seq[Int] = Seq(INFL_CACHE_EVICTION, 21, 22, 23, INFL_MEM_DATAFLOW, 24, 25, 26,
                                        INFL_BTB_STATE_0, INFL_BTB_STATE_1, INFL_BTB_STATE_2, INFL_BTB_STATE_3)
    def inflPcCode(base: Int, pcHi: Int): Int = {
      require(base == INFL_CACHE_EVICTION || base == INFL_MEM_DATAFLOW || base == INFL_BTB_STATE)
      require(pcHi >= 0 && pcHi <= 3)
      if (base == INFL_BTB_STATE) Seq(INFL_BTB_STATE_0, INFL_BTB_STATE_1, INFL_BTB_STATE_2, INFL_BTB_STATE_3)(pcHi)
      else if (pcHi == 0) base
      else if (base == INFL_CACHE_EVICTION) INFL_CACHE_EVICTION_1 + (pcHi - 1)
      else INFL_MEM_DATAFLOW_1 + (pcHi - 1)
    }
    // width of the PC slice a D$ line remembers about its filler / last storer
    val inflPcBitsCF          = 12  // PC[13:2]
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