import { trigger, transition, style, animate } from "@angular/animations";

export const FADE_ANIMATION = trigger('fadeIn', [
    transition(':enter', [
      style({ opacity: 0, transform: 'translateY(10px)' }),
      animate('500ms ease-out', style({ opacity: 1, transform: 'translateY(0)' })),
    ]),
  ]);
  