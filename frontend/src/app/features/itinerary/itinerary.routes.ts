import { Routes } from '@angular/router';

/** Module 6 routes, lazy-loaded from app.routes.ts under /itinerary. */
export const ITINERARY_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./components/itinerary-home/itinerary-home').then(m => m.ItineraryHome) },
  { path: ':id', loadComponent: () => import('./components/itinerary-view/itinerary-view').then(m => m.ItineraryView) },
];
