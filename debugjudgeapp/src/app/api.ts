import {Injectable, EventEmitter} from "@angular/core";
import {Http, Response, RequestOptions, Headers} from '@angular/http';

import * as Rx from '@reactivex/rxjs';
import 'rxjs/add/operator/toPromise';

import {Problem} from "./models/problem";
import {Submission} from "./models/submission";
import {Observable} from "@reactivex/rxjs";
import {Profile} from "./models/profile";
import {JudgingApi} from "./api/jdg.api";
import {RxWebSocketSubject} from "./api/RxWebSocketSubject";
import {BehaviorSubject} from "@reactivex/rxjs";

@Injectable()
export class ApiService {

  private nonceUrl = '/ws/nonce';
  private wsUrl = 'ws://'+window.location.host+'/ws/connect';

  public problems: Rx.BehaviorSubject<Problem[]>;
  public submissions: Rx.BehaviorSubject<Submission[]>;
  public profile: Rx.BehaviorSubject<Profile>;

  private socket: RxWebSocketSubject<any>;

  public judgingApi: JudgingApi;
  public loggedInStatus: BehaviorSubject<boolean>;

  constructor(private http: Http) {
    this.problems = new Rx.BehaviorSubject<Problem[]>([]);
    this.submissions = new Rx.BehaviorSubject<Submission[]>([]);
    this.profile = new Rx.BehaviorSubject<Profile>(undefined);

    this.socket = new RxWebSocketSubject<any>(this.wsUrl);
    this.loggedInStatus = new BehaviorSubject<boolean>(false);
    this.setUpSocket();

    this.judgingApi = new JudgingApi(this, this.socket);

    this.socket.connectionStatus.subscribe((connectionStatus) => {
      if (connectionStatus == true) {
        this.http.get(this.nonceUrl).forEach((response) => {
          console.log(response);
          this.socket.send({who: 'login', data: response.text()});
          this.loggedInStatus.next(true);
        });
      }
      else {
        this.loggedInStatus.next(false);
      }
    });
  }

  private setUpSocket() {
    this.socket.asObservable()
      .subscribe(msg => console.debug("WS:", msg));

    this.socket.asObservable()
      .filter(msg => msg.who === 'dbg')
      .subscribe(msg => console.debug("WS DBG:", msg.data));

    this.socket.asObservable()
      .filter(msg => msg.who === 'alert')
      .subscribe(msg => alert(msg.data));

    this.socket.asObservable()
      .filter(msg => msg.who === "api" && msg.what === 'rld-submissions')
      .subscribe((msg) => {
        console.log("I should reload my submissions:", msg);
        this.getSubmissions()
          .then((submissions) => this.submissions.next(submissions));
      });
    this.getSubmissions()
      .then((submissions) => this.submissions.next(submissions));

    this.socket.asObservable()
      .filter(msg => msg.who === "api" && msg.what === 'rld-problems')
      .subscribe((msg) => {
        console.log("I should reload my problems:", msg);
        this.getProblems()
          .then((problems) => this.problems.next(problems));
      });
    this.getProblems()
      .then((problems) => this.problems.next(problems));

    this.socket.asObservable()
      .filter(msg => msg.who === "api" && msg.what === 'rld-profile')
      .subscribe((msg) => {
        this.getProfile()
          .then((profile) => this.profile.next(profile));
      });
    this.getProfile()
      .then((profile) => this.profile.next(profile));
  }


  public getSubmission(id: number): Promise<Submission> {
    let headers = new Headers({'Content-Type': 'application/json'});
    let options = new RequestOptions({headers: headers});

    return this.http.get("/api/submission/"+id, options)
      .map(this.extractData)
      .map((s) => new Submission(
        s.id,
        s.problem,
        s.team_id,
        new Date(s.submittedAt),
        s.code,
        s.accepted))
      .catch(this.handleError)
      .toPromise()
  }
  public getSubmissions(): Promise<Submission[]> {
    let headers = new Headers({'Content-Type': 'application/json'});
    let options = new RequestOptions({headers: headers});

    return this.http.get("/api/submissions", options)
      .map(this.extractDataArray)
      .map((data) => data.map(s => new Submission(
        s.id,
        s.problem,
        s.team_id,
        new Date(s.submittedAt),
        s.code,
        s.accepted)))
      // .filter(submissions => {
      //   if (!this.arraysEqual(this.submissions.getValue(), submissions)) {
      //     console.log("New submissions!");
      //     return true;
      //   }
      //   console.log("Steady Freddie");
      //   return false;
      // })
      .catch(this.handleError)
      .toPromise();
  }
  public getProblems(): Promise<Problem[]> {
    let headers = new Headers({'Content-Type': 'application/json'});
    let options = new RequestOptions({headers: headers});

    return this.http.get("/api/problems", options)
      .map(this.extractDataArray)
      .map((data) => data.map(p => new Problem(
        p.id,
        p.orderIndex,
        p.title,
        p.description,
        p.language,
        p.precode,
        p.code,
        p.postcode,
        p.answer)))
      // .filter(problems => {
      //   if (!this.arraysEqual(this.problems.getValue(), problems)) {
      //     console.log("New problems!");
      //     return true;
      //   }
      //   console.log("Steady Freddie");
      //   return false;
      // })
      .catch(this.handleError)
      .toPromise();
  }
  public getProfile(): Promise<Profile> {
    let headers = new Headers({'Content-Type': 'application/json'});
    let options = new RequestOptions({headers: headers});

    return this.http.get("/api/profile", options)
      .map(this.extractData)
      .map(p => new Profile(
        p.id,
        p.type,
        p.name,
        p.members))
      .filter(profile => JSON.stringify(this.profile.getValue()) != JSON.stringify(profile))
      .catch(this.handleError)
      .toPromise();
  }

  public submit(problem: Problem, code: String) {
    let headers = new Headers({ 'Content-Type': 'application/json' });
    let options = new RequestOptions({ headers: headers });

    try {
      this.http.post("/api/submissions", JSON.stringify({problem_id: problem.id, code: code}), options)
        .map((response) => {
          console.log("Shah dud");
          return response.json();
        })
        .catch(this.handleError)
        .subscribe();
    } catch (err) {
      console.log(err);
    }
  }
  public accept(submission: Submission) {
    let headers = new Headers({ 'Content-Type': 'application/json' });
    let options = new RequestOptions({ headers: headers });

    try {
      return this.http.post("/api/submission/"+submission.id+"/accept", "", options)
        .map((response) => {
          console.log("Shah dud");
          return response.json();
        })
        .catch(this.handleError);
    } catch (err) {
      console.log(err);
      return undefined;
    }
  }
  public reject(submission: Submission) {
    let headers = new Headers({ 'Content-Type': 'application/json' });
    let options = new RequestOptions({ headers: headers });

    try {
      console.log(submission);
      return this.http.post("/api/submission/"+submission.id+"/reject", "", options)
        .map((response) => {
          console.log("Shah dud");
          return response.json();
        })
        .catch(this.handleError);
    } catch (err) {
      console.log(err);
      return undefined;
    }
  }

  private extractDataArray(res: Response) : any[] {
    let body = res.json();
    return body || [];
  }
  private extractData(res: Response) : any {
    let body = res.json();
    return body || {};
  }
  private handleError (error: Response | any) {
    // In a real world app, you might use a remote logging infrastructure
    let errMsg: string;
    if (error instanceof Response) {
      console.error(error.text());
      const body = error.json() || '';
      const err = body.error || JSON.stringify(body);
      errMsg = `${error.status} - ${error.statusText || ''} ${err}`;
    } else {
      errMsg = error.message ? error.message : error.toString();
    }
    console.error(errMsg);
    console.log(error);
    return Observable.throw(errMsg);
  }

  public static arraysEqual(a: any[], b: any[]) : boolean {
    if (a === b) return true;
    if (a == null || b == null) return false;
    if (a.length != b.length) return false;

    for (let i = 0; i < a.length; ++i) {
      if (JSON.stringify(a[i]) !== JSON.stringify(b[i])) return false;
    }
    return true;
  }
}
