package utils

import java.net.URI
import com.redis._

object RedisService {

  private val connection = {
    val uri = new URI(sys.env("REDIS_URL"))
    val secret = uri.getUserInfo.split(":",2).lastOption
    new RedisClient(uri.getHost, uri.getPort, secret = secret)
  }

  private val TeamIdsKey = "teamIds"
  private val AdvancerCountKey = "advancerCount"

  // TODO: toInt is unsafe
  def getAdvancerCount = connection.get(AdvancerCountKey).map(_.toInt).getOrElse(0)
  def setAdvancerCount(advancerCount: Int) = connection.set(AdvancerCountKey, advancerCount)

  def getTeamIds = connection.lrange(TeamIdsKey, 0, -1).fold(List.empty[String])(_.flatten)
  def setTeamIds(teamIds: Seq[String]) = {
    // clear teamIds
    connection.ltrim(TeamIdsKey, 1, 0)
    // add new ids to teamIds
    teamIds.foreach { teamId =>
      connection.lpush(TeamIdsKey, teamId)
    }
  }
}