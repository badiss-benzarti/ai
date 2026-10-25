import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AiStatus, CatalogItem, GenerationResult, ItemRequest, ItemType, Itinerary, OptimizationResult, TripInfo,
} from '../models/itinerary.models';

@Injectable({ providedIn: 'root' })
export class ItineraryService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/itineraries`;

  getTrips(): Observable<TripInfo[]> {
    return this.http.get<TripInfo[]>(`${this.api}/trips`);
  }

  getAll(): Observable<Itinerary[]> {
    return this.http.get<Itinerary[]>(this.api);
  }

  getById(id: number): Observable<Itinerary> {
    return this.http.get<Itinerary>(`${this.api}/${id}`);
  }

  /** AI: generates (or regenerates) the itinerary of a trip, fixing the budget automatically if needed. */
  generate(tripId: number): Observable<GenerationResult> {
    return this.http.post<GenerationResult>(`${this.api}/generate`, { tripId });
  }

  getAiStatus(): Observable<AiStatus> {
    return this.http.get<AiStatus>(`${environment.apiUrl}/ai/status`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }

  getCatalog(id: number, type: ItemType): Observable<CatalogItem[]> {
    return this.http.get<CatalogItem[]>(`${this.api}/${id}/catalog`, { params: { type } });
  }

  addItem(id: number, dayId: number, request: ItemRequest): Observable<Itinerary> {
    return this.http.post<Itinerary>(`${this.api}/${id}/days/${dayId}/items`, request);
  }

  updateItem(itemId: number, request: ItemRequest): Observable<Itinerary> {
    return this.http.put<Itinerary>(`${this.api}/items/${itemId}`, request);
  }

  deleteItem(itemId: number): Observable<Itinerary> {
    return this.http.delete<Itinerary>(`${this.api}/items/${itemId}`);
  }

  /** AI: replaces items with cheaper alternatives when over budget. */
  optimize(id: number): Observable<OptimizationResult> {
    return this.http.post<OptimizationResult>(`${this.api}/${id}/optimize`, {});
  }
}
