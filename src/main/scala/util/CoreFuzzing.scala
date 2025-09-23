package freechips.rocketchip.util

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._

import org.chipsalliance.cde.config._


trait CoreFuzzingConstants{
    // Bits used to store the TAG
    val iftTagWidth = 1
    
    // Bit widths of reconfiguration control wires
    // we support 4 parameter reconfiguration and each field is 8 bit wide
    val dcacheParamsWidthCF = 8 

    // protected address range
    val PROTECTED_START = 0x80002c10L
    val PROTECTED_END   = 0x80002c40L

    // The L here indicates it is LOOOONG!
    
    // custom ROB entry options
    // 130 is the default ROB size
    val robEntryOptions = Seq(130, 16, 20, 30, 32, 40, 50, 64, 80, 90, 96, 128, 150, 256)
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

    when ((io.addr_i >= PROTECTED_START.U(coreMaxAddrBits.W)) && 
          (io.addr_i <= PROTECTED_END.U(coreMaxAddrBits.W))){
        io.tag_o := 1.U(iftTagWidth.W)
    } .otherwise {
        io.tag_o := 0.U(iftTagWidth.W)
    }
}