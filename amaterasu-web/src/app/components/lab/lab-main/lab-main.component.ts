import { trigger, transition, style, animate } from '@angular/animations';
import { Component, Input } from '@angular/core';
import { Lab } from '../../../models/lab.model';
import { Observable } from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';

@Component({
  selector: 'app-lab-main',
  standalone: false,
  templateUrl: './lab-main.component.html',
  styleUrl: './lab-main.component.scss',
  animations: [
    trigger('fadeIn', [
        transition(':enter', [
            style({ opacity: 0, transform: 'translateY(10px)' }),
            animate('300ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
        ])
    ])
],
})
export class LabMainComponent {
  labs: Lab[] = [];

  @Input() labs$: Observable<Lab[] | undefined> | undefined;

  isHovered: boolean = false;

  isAlreadyDeployedByTeam: boolean = false;

  LabType = LabType;


  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }

  deployLab(labId: string) {
    
  }
}
