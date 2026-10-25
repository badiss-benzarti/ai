import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'itinerary' },
  // Module 6 - Itinerary & budget
  { path: 'itinerary', loadChildren: () => import('./features/itinerary/itinerary.routes').then(m => m.ITINERARY_ROUTES) },
];
