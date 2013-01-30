package ru.megaplan.jira.plugin.boomboom.action

import com.atlassian.jira.web.action.JiraWebActionSupport
import com.atlassian.jira.security.PermissionManager
import scala.Either
import com.atlassian.jira.issue.CustomFieldManager
import org.ofbiz.core.entity.jdbc.SQLProcessor
import java.sql.ResultSet
import collection.mutable
import collection.mutable.ArrayBuffer
import webwork.action.Action
import org.apache.log4j.Logger
import java.text.DecimalFormat
import collection.JavaConverters._

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 09.01.13
 * Time: 15:34
 * To change this template use File | Settings | File Templates.
 */
class AccountTasksAction(permissionManager: PermissionManager,
                         customFieldManager: CustomFieldManager) extends JiraWebActionSupport {

  private val loggerName = this.getClass.getName
  private[this] val log = Logger.getLogger(loggerName)


  @scala.reflect.BeanProperty
  var account: String = ""

  @scala.reflect.BeanProperty
  var results: Array[Map[String, Any]] = Array.empty

  override def doExecute(): String = {

    log.warn("starting execute")

    val acc: Option[scala.Either[String, Long]] = {
      try {
        Some(Right(account.toLong))
      } catch {
        case e: NumberFormatException => {
          Some(Left(account))
        }
        case e: NullPointerException => {
          None
        }
        case e => {
          log.error("some exception ", e)
          None
        }
      }
    }

    val n = acc.foldLeft("") ((s, ei) => {
      ei match {
        case Right(id) => {
          id.toString // aah whatever
        }
        case Left(name) => {
          name
        }
      }
    })

    import CfConstants._
    import DbRequest._

    val request = String.format(r, accName, accId, proj)

    val rs: ResultSet = {
      val sqlProcessor: SQLProcessor = new SQLProcessor("defaultDS")
      sqlProcessor.prepareStatement(request)
      sqlProcessor.setValue(n)
      sqlProcessor.executeQuery
    }

   results = {
    var l = new ArrayBuffer[Map[String, Any]]
    while(rs.next()) {
      val pkey = rs.getString(1)
      val summary = rs.getString(2)
      val assignee = rs.getString(3)
      val assigneeName = {
        val u = getUserManager.getUser(assignee)
        if (u == null) {
          ""
        } else {
          u.getDisplayName
        }
      }
      val created = rs.getTimestamp(4)
      val createdString = getDateTimeFormatter.format(created)
      val status = rs.getString(5)
      l += Map("pkey" -> pkey, "summary" -> summary, "assigneeName" -> assigneeName, "createdString" -> createdString, "status" -> status)
    }
    l.toArray
  }



    Action.SUCCESS

  }
}

object CfConstants {
  val accName = "Название аккаунта"
  val accId = "MPS Account ID"
  val proj = "MPS"
}

object DbRequest {
  val r = """|select ji.pkey, ji.summary, ji.assignee, ji.created, ist.pname as status from jiraissue ji
            |join customfieldvalue cfv on ji.id = cfv.issue
            |join customfield cf on cf.id = cfv.customfield and cfv.customfield in (select id from customfield where cfname = '%s' or cfname = '%s')
            |join issuestatus ist on ist.id = ji.issuestatus
            |where ji.project = (select id from project where pkey = '%s')
            |and cfv.stringvalue = ?
            |order by ji.created desc""".stripMargin
}