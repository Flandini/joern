package io.joern.rubysrc2cpg.dataflow

import io.joern.dataflowengineoss.language.*
import io.joern.rubysrc2cpg.testfixtures.RubyCode2CpgFixture
import io.shiftleft.semanticcpg.language.*

class SingleAssignmentTests extends RubyCode2CpgFixture(withPostProcessing = true, withDataFlow = true) {

  "flow through two inline assignments `z = x = y = 1`" in {
    val cpg = code("""
        |z = x = y = 1
        |puts y
        |puts x
        |puts z
        |""".stripMargin)
    val source = cpg.literal.l
    val sink   = cpg.method.name("puts").callIn.argument.l
    val flows  = sink.reachableByFlows(source).map(flowToResultPairs).distinct.l
    flows.size shouldBe 5
    flows should contain(List(("y = 1", 2), ("puts y", 3)))
    flows should contain(List(("y = 1", 2), ("x = y = 1", 2), ("puts x", 4)))
    flows should contain(List(("y = 1", 2), ("puts y", 3), ("puts x", 4)))
    flows should contain(List(("y = 1", 2), ("x = y = 1", 2), ("z = x = y = 1", 2), ("puts z", 5)))
    flows should contain(List(("y = 1", 2), ("x = y = 1", 2), ("puts x", 4), ("puts z", 5)))
  }

  "flow through expressions" in {
    val cpg = code("""
                     |a = 1
                     |b = a+3
                     |c = 2 + b%6
                     |d = c + b & !c + -b
                     |e = c/d + b || d
                     |f = c - d & ~e
                     |g = f-c%d - +d
                     |h = g**5 << b*g
                     |i = b && c || e > g
                     |j = b>c ? (e+-6) : (f +5)
                     |k = i..h
                     |l = j...g
                     |
                     |puts l
                     |""".stripMargin)

    val src  = cpg.identifier.name("a").l
    val sink = cpg.call.name("puts").l
    sink.reachableByFlows(src).l.size shouldBe 2
  }

  "flow through **=" in {
    val cpg = code("""
        |x = 5
        |call1(x**=2)
        |call2(x) 
        |""".stripMargin)

    val source = cpg.literal("2").l
    val call1  = cpg.call("call1")
    val call2  = cpg.call("call2")

    call1.reachableBy(source).l shouldBe source
    call2.reachableBy(source).l shouldBe source
  }

  "Data flow through grouping expression" in {
    val cpg = code("""
                     |x = 0
                     |y = (x==0)
                     |puts y
                     |""".stripMargin)

    val source = cpg.identifier.name("x").l
    val sink   = cpg.call.name("puts").l
    sink.reachableByFlows(source).l.size shouldBe 2
  }

  "Data flow through variable assigned a scoped constant" in {
    val cpg = code("""
                     |MyConst = 10
                     |x = ::MyConst
                     |puts x
                     |""".stripMargin)

    val source = cpg.identifier.name("x").l
    val sink   = cpg.call.name("puts").l
    sink.reachableByFlows(source).l.size shouldBe 2
  }

  "Data flow for __LINE__ variable identifier" in {
    val cpg = code("""
        |x=1
        |a=x+__LINE__
        |puts a
        |""".stripMargin)

    val source = cpg.identifier.name("x").l
    val sink   = cpg.call.name("puts").l
    sink.reachableByFlows(source).size shouldBe 2
  }
}
