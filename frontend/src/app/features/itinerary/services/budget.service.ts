import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { BudgetSummary, Expense, ExpenseRequest } from '../models/itinerary.models';

@Injectable({ providedIn: 'root' })
export class BudgetService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/itineraries`;

  getSummary(itineraryId: number): Observable<BudgetSummary> {
    return this.http.get<BudgetSummary>(`${this.api}/${itineraryId}/budget`);
  }

  getExpenses(itineraryId: number): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.api}/${itineraryId}/expenses`);
  }

  addExpense(itineraryId: number, request: ExpenseRequest): Observable<Expense> {
    return this.http.post<Expense>(`${this.api}/${itineraryId}/expenses`, request);
  }

  deleteExpense(expenseId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/expenses/${expenseId}`);
  }

  /** Downloads the PDF through HttpClient so it keeps working once a JWT interceptor is added. */
  downloadPdf(itineraryId: number): Observable<Blob> {
    return this.http.get(`${this.api}/${itineraryId}/pdf`, { responseType: 'blob' });
  }
}
