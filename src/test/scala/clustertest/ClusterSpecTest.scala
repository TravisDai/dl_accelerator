package dla.test.clustertest

import chisel3._
import chisel3.tester._
import dla.cluster._
import dla.pe.MCRENFConfig
import org.scalatest._
import scala.util.Random
import scala.math.pow

class ClusterSpecTest extends FlatSpec with ChiselScalatestTester with Matchers with ClusterSRAMConfig with MCRENFConfig with GNMFCS2Config {
  val oneSPadPSum: Int = M0*E*N0*F0 // when read counts this, then stop
  def InActDataGen(n: Int, dataWidth: Int): List[Int] = {
    require(n <= pSumSRAMSize/oneSPadPSum, "maybe you should try a smaller 'n'")
    var resultList: List[Int] = Nil
    for (i <- 0 until n) {
      var temResultList: List[Int] = Nil
      val randomLen: Int = (new Random).nextInt(6) + 3 // the length of one adr SPad range(3, 8)
      while (temResultList.length < randomLen) {
        val randomNum = (new Random).nextInt(pow(2, dataWidth).toInt)
        temResultList = temResultList:::List(randomNum)
      }
      temResultList = temResultList:::List(0) // one zero, that's the end of one SPad data
      resultList = resultList:::temResultList
    }
    resultList = resultList:::List(0) // two zeros, that's the end of this stream data
    resultList
  }
  def PSumDataGen(n:Int, dataWidth: Int): List[Int] = {
    var resultList: List[Int] = Nil
    while ( resultList.length < n ){
      val randomNum = (new Random).nextInt(pow(2, dataWidth).toInt)
      resultList = resultList:::List(randomNum)
      /*
      if( !resultList.exists( s=>s==randomNum )){
        resultList = resultList:::List(randomNum)
      }
      */
    }
    resultList
  }
  behavior of "test the spec of cluster group"
  it should "work well on GLB Cluster" in {
    test(new PSumSRAMBank(pSumSRAMSize, psDataWidth, true)) { thePSumBank =>
      val theTopIO = thePSumBank.io
      val theClock = thePSumBank.clock
      val theData =  PSumDataGen(pSumSRAMSize, psDataWidth)
      val startIndex = (new Random).nextInt(pSumSRAMSize/oneSPadPSum - 1)*oneSPadPSum
      println("----------------- test begin -----------------")
      println("----------- Partial Sum SRAM Bank ------------")
      println("----------- test basic functions -------------")
      println(s"-------- startIndex = $startIndex")
      thePSumBank.reset.poke(true.B)
      theClock.step(1)
      thePSumBank.reset.poke(false.B)
      println("--------------- begin to write ---------------")
      theTopIO.ctrlPath.writeOrRead.poke(true.B)
      theTopIO.ctrlPath.doEn.poke(true.B)
      theTopIO.ctrlPath.startIdx.poke(startIndex.U)
      for (i <- 0 until oneSPadPSum) {
        println(s"--------------- $i-th write cycle -----------")
        theTopIO.dataPath.inIOs.bits.poke(theData(i).U)
        theTopIO.dataPath.inIOs.valid.poke(true.B)
        theTopIO.debugIO.idx.expect((startIndex + i).U, s"startIdx = $startIndex")
        theTopIO.debugIO.idxInc.expect(true.B, s"$i, index should increase")
        /*
        println(s"--------       data = ${theData(i)} \n" +
          s"--------      index = ${theTopIO.debugIO.idx.peek()} \n" +
          s"-------- whetherInc = ${theTopIO.debugIO.idxInc.peek()}")*/
        theTopIO.dataPath.inIOs.ready.expect(true.B, s"$i, it should be ready now")
        theTopIO.ctrlPath.done.expect((i == oneSPadPSum - 1).B, s"i = $i, write should finish?")
        theClock.step(1)
        println("-------- PASS")
      }
      println("---------------- write finish ----------------")
      theTopIO.ctrlPath.doEn.poke(false.B)
      theClock.step(1)
      println("--------------- begin to read ----------------")
      theTopIO.ctrlPath.writeOrRead.poke(false.B)
      theTopIO.ctrlPath.doEn.poke(true.B)
      theTopIO.ctrlPath.startIdx.poke(startIndex.U)
      for (i <- 0 until oneSPadPSum) {
        println(s"--------------- $i-th read cycle -----------")
        /*
        println(s"--------       data = ${theTopIO.dataPath.outIOs.bits.peek()} \n" +
          s"--------      index = ${theTopIO.debugIO.idx.peek()} \n" +
          s"-------- whetherInc = ${theTopIO.debugIO.idxInc.peek()}")*/
        theTopIO.dataPath.outIOs.ready.poke(true.B)
        theTopIO.debugIO.idxInc.expect(false.B, s"$i, index should not increase now")
        theClock.step(1)
        /*
        println(s"--------       data = ${theTopIO.dataPath.outIOs.bits.peek()} \n" +
          s"--------      index = ${theTopIO.debugIO.idx.peek()} \n" +
          s"-------- whetherInc = ${theTopIO.debugIO.idxInc.peek()}")*/
        theTopIO.debugIO.idxInc.expect(true.B, s"index should increase")
        theTopIO.dataPath.outIOs.bits.expect(theData(i).U, s"$i, theData should be ${theData(i)}")
        theTopIO.dataPath.outIOs.valid.expect(true.B, "it should valid now")
        theTopIO.ctrlPath.done.expect((i == oneSPadPSum - 1).B)
        theClock.step(1)
        println("-------- PASS")
      }
      println("---------------- read finish -----------------")
      println("---------------- test finish -----------------")
    }
    test(new InActSRAMCommon(inActAdrSRAMSize, inActAdrWidth, true)) { theAdrSRAM =>
      val theTopIO = theAdrSRAM.io
      val theClock = theAdrSRAM.clock
      val theData =  InActDataGen(5, inActAdrWidth)
      println("----------------- test begin -----------------")
      println("----------- InputActAdr SRAM Bank ------------")
      println("----------- test basic functions -------------")
      theAdrSRAM.reset.poke(true.B)
      theClock.step(1)
      theAdrSRAM.reset.poke(false.B)
      println("--------------- begin to write ---------------")
      theTopIO.ctrlPath.writeOrRead.poke(true.B)
      theTopIO.ctrlPath.doEn.poke(true.B)
      for (i <- theData.indices) {
        println(s"--------------- $i-th write cycle -----------")
        theTopIO.dataPath.inIOs.data.bits.poke(theData(i).U)
        theTopIO.dataPath.inIOs.data.valid.poke(true.B)
        theTopIO.dataPath.inIOs.data.ready.expect(true.B, s"$i, it should be ready now")
        //theTopIO.ctrlPath.done.expect(((i > 2) && (theData(i-1) == 0) && (theData(i) == 0)).B, s"Data(i-1) = \${theData(i-1)}\n" +
          s"Data(i) = ${theData(i)}")
      }
      println("--------------- write finish -----------------")
      println("--------------- begin to read ----------------")
      println("---------------- read finish -----------------")
      println("---------------- test finish -----------------")
    }
  }
  //it should "work well on Router Cluster" in {}
  //it should "work well on Processing Element Cluster" in {}
  //it should "work well on Cluster Group" in {}
}