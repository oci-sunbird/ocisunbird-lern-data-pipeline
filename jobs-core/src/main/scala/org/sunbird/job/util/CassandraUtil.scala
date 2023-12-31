package org.sunbird.job.util

import com.datastax.driver.core._
import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy
import org.slf4j.LoggerFactory

import java.util

class CassandraUtil(host: String, port: Int, isMultiDCEnabled: Boolean) {

  private[this] val logger = LoggerFactory.getLogger("CassandraUtil")

  var cluster = {
    val cb = Cluster.builder()
      .addContactPoint(host)
      .withPort(port)
      .withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM))
      .withoutJMXReporting()

    if(isMultiDCEnabled) {
      cb.withLoadBalancingPolicy(DCAwareRoundRobinPolicy.builder().build())
    }
    cb.build()
  }
  var session = cluster.connect()

  def findOne(query: String): Row = {
    try {
      val rs: ResultSet = session.execute(query)
      rs.one
    } catch {
      case ex: DriverException =>
        logger.error(s"findOne - Error while executing query $query :: ", ex)
        this.reconnect()
        this.findOne(query)
    }
  }

  def find(query: String): util.List[Row] = {
    try {
      val rs: ResultSet = session.execute(query)
      rs.all
    } catch {
      case ex: DriverException =>
        this.reconnect()
        this.find(query)
    }
  }

  def upsert(query: String): Boolean = {
    val rs: ResultSet = session.execute(query)
    rs.wasApplied
  }

  def getUDTType(keyspace: String, typeName: String): UserType = session.getCluster.getMetadata.getKeyspace(keyspace).getUserType(typeName)

  def reconnect(): Unit = {
    this.session.close()
    val cluster: Cluster = Cluster.builder.addContactPoint(host).withPort(port).build
    this.session = cluster.connect
  }

  def close(): Unit = {
    this.session.close()
  }

  def update(query: Statement): Boolean = {
    val rs: ResultSet = session.execute(query)
    rs.wasApplied
  }

  def executePreparedStatement(query: String, params: Object*): util.List[Row] = {
    val rs: ResultSet = session.execute(session.prepare(query).bind(params: _*))
    rs.all()
  }

}
