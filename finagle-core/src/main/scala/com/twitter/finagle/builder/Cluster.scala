package com.twitter.finagle.builder

import java.net.SocketAddress
import com.twitter.finagle.Service

/**
 * A collection of SocketAddresses. The intention of this interface
 * to express membership in a cluster of servers that provide a
 * specific service.
 *
 * Note that a Cluster can be elastic: members can join or leave at
 * any time.
 */
trait Cluster {
  /**
   * Produce a sequence of brokers that changes as servers join and
   * leave the cluster.
   */
  def mkServices[Req, Rep](f: SocketAddress => Service[Req, Rep]):
    Seq[Service[Req, Rep]]

  def join(address: SocketAddress)
}

class SocketAddressCluster(underlying: Seq[SocketAddress])
  extends Cluster
{
  private[this] var self = underlying

  def mkServices[Req, Rep](f: SocketAddress => Service[Req, Rep]) =
    self map f

  def join(address: SocketAddress) {
    self = underlying ++ Seq(address)
  }
}
