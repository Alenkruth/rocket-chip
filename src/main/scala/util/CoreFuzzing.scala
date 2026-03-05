package freechips.rocketchip.util

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._

import org.chipsalliance.cde.config._


trait CoreFuzzingConstants{
    // Bits used to store the TAG
    val iftTagWidth = 1

    // counter for uops
    val uopIDCounterWidthCF = 8

    // tags for each module
    val numModules = 19
    // tainting info for uops
    val moduleCountCF = log2Ceil(numModules) // we will probably have 16 modules tops
   
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

    val taintTypeCf = 2 // we have 4 types of taints

    // Bit widths of reconfiguration control wires
    // we support 4 parameter reconfiguration and each field is 8 bit wide
    val dcacheParamsWidthCF = 8 

    // protected address range
    // moved to a CSR
    // val PROTECTED_START = 0x80002c10L
    // val PROTECTED_END   = 0x80002c40L
    // The L here indicates it is LOOOONG!
    
    // custom ROB entry options
    // 300 is the default CoreFuzzing ROB size
    // the rob cofig register at 0xbc2 is by default set to 0 (300 entries)
    // possible entry configs are
    // 0 : 300
    // 1 : 16
    // 2 : 20
    // 3 : 24
    // 4 : 30
    // 5 : 32
    // 6 : 40
    // 7 : 50
    // 8 : 60
    // 9 : 64
    // 10: 80
    // 11: 90
    // 12: 96
    // 13: 128
    // 14: 130
    // 15: 150
    // 16: 200
    // 17: 250
    // 18: 256 
    def robEntryOptions = Seq(512, 16, 20, 24, 30, 32, 40, 50, 60, 64, 80, 90, 96, 128, 130, 150, 200, 250, 256)
    // itlb sets is fixed to 1. Not messing with it in fear of timing. defined in rocket>ICache.scala
    // fixed superpage entries = 4
    // same with dtlb. Degined in HellaCache.scala
    def itlbWays = Seq(64, 32, 16, 8, 4, 2, 1)
    def dtlbWays = Seq(64, 32, 16, 8, 4, 2, 1)



    val setOptions = Seq(128, 32, 64)
    val wayOptions = Seq(16, 1, 2, 4, 8)
    val sizeOptions = Seq(128, 2, 4, 8, 16, 32, 64)
    val replOptions = Seq(0, 1) // 0=PseudoLRU, 1=Random
    val blockSizeOptions = Seq(16, 8) // 16B (default), 8B

}

class Tagger (implicit val p: Parameters) extends Module
    with HasCoreParameters
    with CoreFuzzingConstants
{
    val io = IO(new Bundle{
        val addr_i = Input(UInt(coreMaxAddrBits.W))
        val tag_o = Output(UInt(iftTagWidth.W))
    })

    // when ((io.addr_i >= PROTECTED_START.U(coreMaxAddrBits.W)) && 
    //       (io.addr_i <= PROTECTED_END.U(coreMaxAddrBits.W))){
    //     io.tag_o := 1.U(iftTagWidth.W)
    // } .otherwise {
    //     io.tag_o := 0.U(iftTagWidth.W)
    // }
    // TODO - fix this for tagging if needed
    io.tag_o := 0.U(iftTagWidth.W) // default no tagging
}