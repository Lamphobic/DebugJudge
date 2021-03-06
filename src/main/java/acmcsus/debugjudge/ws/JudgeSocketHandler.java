package acmcsus.debugjudge.ws;

import acmcsus.debugjudge.ctrl.*;
import acmcsus.debugjudge.model.*;
import acmcsus.debugjudge.proto.*;
import acmcsus.debugjudge.proto.Competition.*;
import acmcsus.debugjudge.proto.WebSocket.*;
import io.reactivex.functions.*;
import org.eclipse.jetty.websocket.api.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static acmcsus.debugjudge.ctrl.MessageStores.SUBMISSION_STORE;
import static acmcsus.debugjudge.ws.SocketHandler.addObserver;
import static acmcsus.debugjudge.ws.SocketHandler.sendMessage;
import static java.lang.String.format;
import static spark.Spark.halt;

public class JudgeSocketHandler {

  private static Logger logger = LoggerFactory.getLogger(JudgeSocketHandler.class);

  static void handleJ2SMessage(WebSocketContext ctx) {
    C2SMessage.J2SMessage j2SMessage = ctx.req.getJ2SMessage();

    JudgeQueueHandler judgeQueueHandler = JudgeQueueHandler.getInstance();

    switch (j2SMessage.getValueCase()) {
      case CHANGECOMPETITIONSTATEMESSAGE: {
        CompetitionController.changeCompetitionState(
          j2SMessage.getChangeCompetitionStateMessage().getState());
        break;
      }
      case STARTJUDGINGMESSAGE: {
        judgeQueueHandler.connected(ctx.profile, ctx.session);
        break;
      }
      case STOPJUDGINGMESSAGE: {
        judgeQueueHandler.disconnected(ctx.profile, ctx.session);
        break;
      }
      case SUBMISSIONJUDGEMENTMESSAGE: {
        Integer tid = j2SMessage.getSubmissionJudgementMessage().getTeamId();
        Integer pid = j2SMessage.getSubmissionJudgementMessage().getProblemId();
        Long sid = j2SMessage.getSubmissionJudgementMessage().getSubmissionId();

        SubmissionJudgement ruling = j2SMessage.getSubmissionJudgementMessage().getRuling();

        switch (ruling) {
          case JUDGEMENT_UNKNOWN: {
            judgeQueueHandler.defer(ctx.profile);
            break;
          }
          case JUDGEMENT_SUCCESS:
          case JUDGEMENT_FAILURE: {
            Submission submission;

            try {
              submission = SUBMISSION_STORE.readFromPath(SUBMISSION_STORE.pathForIds(tid, pid, sid));
            }
            catch (IOException e) {
              logger.error(format("Submission %d/%d/%d not found for judge's ruling",
                  tid, pid, sid), e);
              throw halt(400);
            }

            // TODO: Judgement Messages (like "TLE" or "Excessive Output")
            StateService.instance.submissionRuling(
                submission, ctx.profile.getId(), ruling, "lorem ipsum");
            break;
          }
          default: {
            logger.warn("Unrecognized submission judgement: " + ruling);
          }
        }
        break;
      }
      case JUDGINGPREFERENCESMESSAGE: {
        Map<Integer, Boolean> map = ctx.req.getJ2SMessage()
          .getJudgingPreferencesMessage()
          .getPreferencesMap();

        judgeQueueHandler.setJudgePreferences(ctx.profile, ctx.session, map);
        break;
      }
      default: {
        logger.error("WS: Backend does not recognize J2SMessage: {}", j2SMessage.getValueCase());
      }
    }
  }

  public static void subscribeNewJudge(Session session, Profile profile) throws IOException {
    Consumer<List<Problem>> problemReloader =
        (problems) -> SocketHandler.sendMessage(session, S2CMessage.newBuilder()
            .setReloadProblemsMessage(S2CMessage.ReloadProblemsMessage.newBuilder()
                .setProblems(Problem.List.newBuilder().addAllValue(problems))).build());

    Scoreboard lastScoreboard = ScoreboardBroadcaster.getLastScoreboard();
    if (lastScoreboard != null) {
      sendMessage(session, WebSocket.S2CMessage.newBuilder()
          .setScoreboardUpdateMessage(S2CMessage.ScoreboardUpdateMessage.newBuilder()
              .setScoreboard(lastScoreboard))
          .build());
    }

    addObserver(session, StateService.instance.addJudgeProblemsListener(problemReloader));
  }
}
