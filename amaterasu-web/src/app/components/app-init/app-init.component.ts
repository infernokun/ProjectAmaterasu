import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';



@Component({
  selector: 'app-app-init',
  templateUrl: './app-init.component.html',
  styleUrls: ['./app-init.component.scss']
})
export class AppInitComponent implements OnInit {

  formGroup: FormGroup;

  constructor() {
    this.formGroup = new FormGroup({
      name: new FormControl(''),
      description: new FormControl(''),
      settings: new FormGroup({
        enableDarkMode: new FormControl(true),
        notifications: new FormControl(true),
        language: new FormControl('en')
      })
    });
  }

  ngOnInit(): void {
    this.formGroup.valueChanges.subscribe((value: any) => {
      console.log(value);
    });
  }

  handleAction() {
    console.log(this.formGroup.value);
  }
}
