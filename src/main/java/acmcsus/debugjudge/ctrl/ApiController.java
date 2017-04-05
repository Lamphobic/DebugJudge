package acmcsus.debugjudge.ctrl;

import acmcsus.debugjudge.ProcessBody;
import acmcsus.debugjudge.Views;
import acmcsus.debugjudge.model.*;
import acmcsus.debugjudge.ws.SocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ebean.Ebean;
import spark.Request;
import spark.Response;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Date;
import java.time.Instant;
import java.util.List;

import static acmcsus.debugjudge.ctrl.SecurityApi.getCompetition;
import static acmcsus.debugjudge.ctrl.SecurityApi.getJudge;
import static java.lang.String.format;
import static spark.Spark.*;

public class ApiController {
    private ApiController(){ /* Static */ }
    
    private static final ObjectMapper errorMapper = new ObjectMapper();
    
    public static void routeAPI() {
        path("/api", () -> {
            
            before("/profile", SecurityApi::loggedInFilter);
            get("/profile", ApiController::getProfile);
            
            before("/teams", SecurityApi::loggedInFilter);
            get("/teams", ApiController::getTeams);
//            post("/teams" ApiController::newTeam);
    
            path("/team", () -> {
                before("/:id", SecurityApi::loggedInFilter);
                get("/:id", ApiController::getTeam);
            });
            
            
            before("/submissions", SecurityApi::loggedInFilter);
            get("/submissions", ApiController::getSubmissions);
            post("/submissions", ApiController::newSubmission);
            
            path("/submission", () -> {
                before("/:id", SecurityApi::loggedInFilter);
                get("/:id", ApiController::getSubmission);
                
                before("/:id/accept", SecurityApi::judgeFilter);
                post("/:id/accept", ApiController::acceptSubmission);
    
                before("/:id/reject", SecurityApi::judgeFilter);
                post("/:id/reject", ApiController::rejectSubmission);
            });
            
            
            before("/problems", SecurityApi::loggedInFilter);
            get("/problems", ApiController::getProblems);
            
            path("/problem", () -> {
                before("/:id", SecurityApi::loggedInFilter);
                get("/:id", ApiController::getProblem);
            });
            
            
            get("/scoreboard", ScoreboardController::getScoreboard);
            
            
            after("/*", (req, res) -> res.type("application/json"));
        });
    }
    
    private static String getProfile(Request req, Response res) throws JsonProcessingException {
        ObjectNode jsonNode = new ObjectNode(JsonNodeFactory.instance);
        
        Team team = SecurityApi.getTeam(req);
        if (team != null) {
            jsonNode.put("type", "team");
            jsonNode.put("id", team.id);
            jsonNode.put("name", team.teamName);
            jsonNode.put("members", team.memberNames);
        } else {
            Judge judge = SecurityApi.getJudge(req);
            
            if (judge == null)
                throw halt("LOGIC NO LONGER EXISTS");
            
            jsonNode.put("type", "judge");
            jsonNode.put("id", judge.id);
            jsonNode.put("name", judge.name);
        }
    
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(jsonNode);
    }
    
    public static String getTeams(Request req, Response res) throws JsonProcessingException {
        Competition competition = getCompetition(req);
        List<Team> result = Team.find.query().where()
                .eq("competition_id", competition.id)
                .findList();
        
        return Ebean.json().toJson(result);
    }
    public static String getTeam(Request req, Response res) throws JsonProcessingException {
        Team team = SecurityApi.getTeam(req);
        Judge judge = getJudge(req);
        
        Team result = Team.find.query().where()
                .eq("id", req.params("id"))
                .findUnique();
        
        if (result == null) throw halt(404);
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer;
        
        if (judge != null)
            writer = mapper.writerWithView(Views.JudgeView.class);
        else if (team != null && team.id.equals(result.id))
            writer = mapper.writerWithView(Views.TeamView.class);
        else
            writer = mapper.writerWithView(Views.PublicView.class);
        
        return writer.writeValueAsString(result);
    }
//    private static String newTeam(Request req, Response res) {
//        SecurityApi.teamFilter(req, res);
//        try {
//            JsonNode json = ProcessBody.asJson(req);
//
//            Submission submission = new Submission();
//            submission.team = SecurityApi.getTeam(req);
//            submission.problem = Problem.find.byId(json.get("problem_id").asLong());
//            submission.submittedAt = Date.from(Instant.now());
//            submission.text = json.get("text").asText();
//
//            submission.save();
//            submission.refresh();
//
//            return Long.toString(submission.id);
//        } catch (IOException e) {
//            throw halt(400);
//        }
//    }
    
    public static String getSubmissions(Request req, Response res) throws JsonProcessingException {
        Team team = SecurityApi.getTeam(req);
        if (team != null) {
            List<Submission> result = Submission.find.query()
                    .fetch("problem", "*")
                    .where()
                    .eq("team_id", team.id)
                    .findList();
    
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(result);
        }
        
        Judge judge = SecurityApi.getJudge(req);
        if (judge != null) {
            List<Submission> result = Submission.find.query()
                    .fetch("problem", "*")
                    .where()
                    .eq("competition_id", judge.competition.id)
                    .findList();
    
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(result);
        }
        
        throw halt(401);
    }
    public static String getSubmission(Request req, Response res) throws JsonProcessingException {
        Team team = SecurityApi.getTeam(req);
        Judge judge = getJudge(req);
        
        Submission result = Submission.find.query()
                .fetch("problem", "*")
                .where()
                .eq("id", req.params("id"))
                .findUnique();
        
        if (result == null || (judge == null && (team == null || !team.id.equals(result.team.id))))
            throw halt(404);
    
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(result);
    }
    private static String newSubmission(Request req, Response res) {
        SecurityApi.teamFilter(req, res);
        try {
            JsonNode json = ProcessBody.asJson(req);
            
            Submission submission = new Submission();
            submission.team = SecurityApi.getTeam(req);
            submission.problem = Problem.find.byId(json.get("problem_id").asLong());
            submission.submittedAt = Date.from(Instant.now());
            submission.code = json.get("code").asText();
            
            submission.save();
            submission.refresh();
            
            Event event = new Event();
            event.submission = submission;
            event.eventType = Event.EventType.SUBMISSION;
            SocketHandler.notify(submission.team, event);
            
            return Long.toString(submission.id);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            
            throw halt(400, format("{\"error\":\"%s\"}",
                    sw.toString()
                            .replaceAll("\"", "\\\"")
                            .replaceAll("\\\\", "\\\\")));
        }
    }
    private static String acceptSubmission(Request req, Response res) {
        Judge judge = SecurityApi.getJudge(req);
//TODO:        if (judge == null) throw halt(403);
    
        try {
            Submission submission = Submission.find.byId(Long.valueOf(req.params("id")));
            if (submission == null) throw halt(404);
            
            submission.accepted(judge, Date.from(Instant.now()));
            submission.update();
    
            Event event = new Event();
            event.submission = submission;
            event.eventType = Event.EventType.ACCEPTANCE;
            SocketHandler.notify(submission.team, event);
            
            return "200";
        } catch (Exception e) {
            e.printStackTrace();
            throw halt(400);
        }
    }
    private static String rejectSubmission(Request req, Response res) {
        Judge judge = SecurityApi.getJudge(req);
//TODO:        if (judge == null) throw halt(403);
        
        try {
            Submission submission = Submission.find.byId(Long.valueOf(req.params("id")));
            if (submission == null) throw halt(404);
    
            submission.rejected(judge, Date.from(Instant.now()));
            submission.update();
    
            Event event = new Event();
            event.submission = submission;
            event.eventType = Event.EventType.REJECTION;
            SocketHandler.notify(submission.team, event);
            
            return "200";
        } catch (Exception e) {
            e.printStackTrace();
            throw halt(400);
        }
    }
    
    public static String getProblems(Request req, Response res) throws JsonProcessingException {
        Competition competition = getCompetition(req);
        
        List<Problem> result = Problem.find.query().where()
                .eq("competition_id", competition.id)
                .findList();
        
        return Ebean.json().toJson(result);
    }
    public static String getProblem(Request req, Response res) throws JsonProcessingException {
        Competition competition = getCompetition(req);
        
        Problem result = Problem.find.query().where()
                .eq("competition", competition.id)
                .eq("id", req.params("id"))
                .findUnique();
        
        if (result == null)
            throw halt(404);
        
        return Ebean.json().toJson(result);
    }
    
    
}
