package utils

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.io.Source

import com.github.nscala_time.time.Imports._

object MFLService {

  def franchises: Option[List[Franchise]] = {
    val league = {
      // TODO: read this from config file
      val url = "http://football26.myfantasyleague.com/2015/export?TYPE=league&L=34348&W=&JSON=1"
      val source = Source.fromURL(url).mkString
      Json.parse(source)
    }
    val maybeFranchises = (league \\ "franchise").headOption.map(_.as[List[JsObject]])
    maybeFranchises.map(_.map { franchiseJson =>
      val name = (franchiseJson \ "name").as[String]
      val id = (franchiseJson \ "id").as[String]
      Franchise(name, id)
    })
  }

  def projectedScores = {
    val nflWeek = getNFLWeek
    val url = s"http://football26.myfantasyleague.com/2015/export?TYPE=projectedScores&L=34348&W=$nflWeek&JSON=1"
    val json = Json.parse(Source.fromURL(url).mkString)
    json.as[ProjectedScores]
  }

  def liveScores = {
    val liveScoringUrl = "http://football26.myfantasyleague.com/2015/export?TYPE=liveScoring&L=34348&DETAILS=1&JSON=1"
    val json = Json.parse(Source.fromURL(liveScoringUrl).mkString)
    json.as[LiveScoring]
  }

  def getNFLWeek = {
    // Wednesday before the first week of NFL games
    val week1 = DateTime.parse("2015-09-09")

    // sequence where the (index + 1) of each element corresponds the NFL week that begins on the date stored at that index
    // e.g. DateTime("2015-09-23") is stored at index 2, and represents the first day of the 3rd NFL week
    val startOfNFLWeeks = {
      val numNFLWeeks = 17
      (0 until numNFLWeeks).map(i => week1.plusWeeks(i))
    }

    // the index of the first date in `startOfNFLWeeks` that is after today is equal to the current NFL week
    startOfNFLWeeks.indexWhere(DateTime.now < _)
  }
}

case class Franchise(name: String, id: String)

case class LiveScorePlayer(id: String, score: Double, gameSecondsRemaining: Int, status: String)
object LiveScorePlayer {
  implicit val reads: Reads[LiveScorePlayer] = (
    (__ \ "id").read[String] and
    (__ \ "score").read[String].map(_.toDouble) and
    (__ \ "gameSecondsRemaining").read[String].map(_.toInt) and
    (__ \ "status").read[String]
  )(LiveScorePlayer.apply _)
}

case class LiveScoreFranchise(id: String,
                              score: Double,
                              gameSecondsRemaining: Int,
                              playersYetToPlay: Int,
                              playersCurrentlyPlaying: Int,
                              players: Seq[LiveScorePlayer]) {
  val starters = players.filter(_.status == "starter")
}
object LiveScoreFranchise {
  implicit val reads: Reads[LiveScoreFranchise] = (
    (__ \ "id").read[String] and
    (__ \ "score").read[String].map(_.toDouble) and
    (__ \ "gameSecondsRemaining").read[String].map(_.toInt) and
    (__ \ "playersYetToPlay").read[String].map(_.toInt) and
    (__ \ "playersCurrentlyPlaying").read[String].map(_.toInt) and
    (__ \ "players" \ "player").read[Seq[LiveScorePlayer]]
  )(LiveScoreFranchise.apply _)
}

case class LiveScoreMatchup(franchises: Seq[LiveScoreFranchise])
object LiveScoreMatchup {
  implicit val reads: Reads[LiveScoreMatchup] = (__ \ "franchise").read[Seq[LiveScoreFranchise]].map(LiveScoreMatchup.apply)
}

case class LiveScoring(matchups: Seq[LiveScoreMatchup]) {
  def franchises = matchups.flatMap(_.franchises)
}
object LiveScoring {
  implicit val reads: Reads[LiveScoring] = (__ \ "liveScoring" \ "matchup").read[Seq[LiveScoreMatchup]].map(LiveScoring.apply)
}

case class ProjectedScore(id: String, score: Double)
object ProjectedScore {
  implicit val reads = (
    (__ \ "id").read[String] and
    (__ \ "score").read[String].map(s => if (s.isEmpty) 0 else s.toDouble)
  )(ProjectedScore.apply _)
}

case class ProjectedScores(scores: Seq[ProjectedScore])
object ProjectedScores {
  implicit val reads: Reads[ProjectedScores] = (__ \ "projectedScores" \ "playerScore").read[Seq[ProjectedScore]].map(ProjectedScores.apply)
}