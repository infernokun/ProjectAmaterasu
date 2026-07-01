import { Component } from '@angular/core';
import { LabComponent } from '../app-lab/lab/lab.component';

@Component({
    selector: 'amaterasu-home',
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
    imports: [LabComponent]
})
export class HomeComponent { }
