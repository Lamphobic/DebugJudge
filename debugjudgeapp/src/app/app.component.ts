import {Component, OnDestroy, OnInit} from "@angular/core";
import {ApiService} from "./api";
import {Profile} from "./models/profile";
import {Subscription} from "@reactivex/rxjs";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  providers: [ ApiService ],
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {


  constructor(private apiService : ApiService) {
    this.isTeam = false;
    this.isJudge = false;
  }

  ngOnInit() {
    this.profileSubscription = this.apiService.profile.subscribe(profile => {
      this.profile = profile;
      if (this.profile) {
        this.isTeam = this.profile.type === 'team';
        this.isJudge = this.profile.type === 'judge';
      }
    });
  }
  ngOnDestroy() {
    this.profileSubscription.unsubscribe();
  }

}
