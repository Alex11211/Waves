package com.wavesplatform.it

import org.scalatest.{CancelAfterFailure, FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.traverse
import scala.concurrent.duration._

class NetworkSeparationTestSuite extends FreeSpec with Matchers with IntegrationNodesInitializationAndStopping
  with CancelAfterFailure with ReportingTestName {

  override val docker = Docker(getClass)
  override val nodes: Seq[Node] = docker.startNodesSync(NodeConfigs.forTest(3, 1 -> "waves.miner.quorum = 0"))
  private val primaryNode: Node = nodes.last

  "node should grow up to 10 blocks together" in Await.result(primaryNode.waitForHeight(10), 5.minutes)

  "then we disconnect nodes from the network" in nodes.foreach(docker.disconnectFromNetwork)

  "and wait for another 10 blocks on one node" in Await.result(primaryNode.waitForHeight(20), 5.minutes)

  "after that we connect nodes back to the network" in nodes.foreach(docker.connectToNetwork)

  "nodes should sync" in Await.result(
    for {
      height <- traverse(nodes)(_.height).map(_.max)
      _ <- traverse(nodes)(_.waitForHeight(height + 13))
      blocks <- traverse(nodes)(_.blockAt(height + 8))
    } yield {
      val xs = blocks.map(_.signature)
      all(xs) shouldEqual xs.head
    },
    5.minutes
  )

}
