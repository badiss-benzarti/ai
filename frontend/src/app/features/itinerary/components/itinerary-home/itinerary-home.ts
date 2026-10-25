import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ItineraryService } from '../../services/itinerary.service';
import { AiStatus, Itinerary, TripInfo, apiError } from '../../models/itinerary.models';
import { elapsedTimer } from '../../utils/elapsed-timer';

/** Lists the user's trips and lets them generate an itinerary with the AI. */
@Component({
  selector: 'app-itinerary-home',
  imports: [RouterLink, DatePipe, CurrencyPipe],
  templateUrl: './itinerary-home.html',
  styleUrl: './itinerary-home.css',
})
export class ItineraryHome implements OnInit {
  private service = inject(ItineraryService);
  private router = inject(Router);

  trips = signal<TripInfo[]>([]);
  itineraryByTrip = signal<Record<number, Itinerary>>({});
  loading = signal(true);
  generatingTripId = signal<number | null>(null);
  error = signal<string | null>(null);
  aiStatus = signal<AiStatus | null>(null);
  timer = elapsedTimer();

  ngOnInit(): void {
    this.service.getAiStatus().subscribe({ next: s => this.aiStatus.set(s), error: () => {} });
    forkJoin({ trips: this.service.getTrips(), itineraries: this.service.getAll() }).subscribe({
      next: ({ trips, itineraries }) => {
        this.trips.set(trips);
        const map: Record<number, Itinerary> = {};
        itineraries.forEach(it => (map[it.tripId] = it));
        this.itineraryByTrip.set(map);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(apiError(err));
        this.loading.set(false);
      },
    });
  }

  generate(trip: TripInfo): void {
    this.generatingTripId.set(trip.id);
    this.error.set(null);
    this.timer.start();
    this.service.generate(trip.id).subscribe({
      // The view shows the automatic budget optimization passed in the navigation state
      next: result => this.router.navigate(['/itinerary', result.itinerary.id], {
        state: { optimization: result.optimization },
      }),
      error: err => {
        this.error.set(apiError(err));
        this.generatingTripId.set(null);
        this.timer.stop();
      },
    });
  }

  nights(trip: TripInfo): number {
    const ms = new Date(trip.endDate).getTime() - new Date(trip.startDate).getTime();
    return Math.round(ms / 86_400_000);
  }
}
