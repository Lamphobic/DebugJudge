import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';
import { RouterModule, Routes } from '@angular/router';
import { MaterialModule } from '@angular/material';

import { AppComponent } from './app.component';
import { TeamComponent } from './team.component';
import { JudgeComponent } from "./judge.component";
import { ScoreboardComponent } from "./scoreboard.component";
import { CodeEditorComponent } from "./codeeditor.component";

import {Autosize} from 'angular2-autosize/src/autosize.directive';
import {ProblemCardComponent} from "./problemcard.component";
import {IndexComponent} from "./index.component";
import { AceEditorComponent } from 'ng2-ace-editor';

const appRoutes: Routes = [
  { path: '', component: IndexComponent },
  { path: 'scoreboard', component: ScoreboardComponent },
];

@NgModule({
  declarations: [
    Autosize,
    AceEditorComponent,

    AppComponent,
    IndexComponent,
    TeamComponent,
    JudgeComponent,
    ScoreboardComponent,
    ProblemCardComponent,
    CodeEditorComponent
  ],
  imports: [
    BrowserAnimationsModule,
    BrowserModule,
    FormsModule,
    HttpModule,
    RouterModule.forRoot(appRoutes),
    MaterialModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
