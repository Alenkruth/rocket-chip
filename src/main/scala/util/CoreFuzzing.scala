package freechips.rocketchip.util

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._

import org.chipsalliance.cde.config._


trait CoreFuzzingConstants{
    // Bits used to store the TAG
    val TAG_WIDTH = 1

    // protected address range
    val PROTECTED_START = 0x80002c10L
    val PROTECTED_END   = 0x80002c40L

    // The L here indicates it is LOOOONG!
}

class Tagger (implicit val p: Parameters) extends Module
    with HasCoreParameters
    with CoreFuzzingConstants
{
    val io = IO(new Bundle{
        val addr_i = Input(UInt(coreMaxAddrBits.W))
        val tag_o = Output(UInt(TAG_WIDTH.W))
    })

    when ((io.addr_i >= PROTECTED_START.U(coreMaxAddrBits.W)) && 
          (io.addr_i <= PROTECTED_END.U(coreMaxAddrBits.W))){
        io.tag_o := 1.U(TAG_WIDTH.W)
    } .otherwise {
        io.tag_o := 0.U(TAG_WIDTH.W)
    }
}