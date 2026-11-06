import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { SearchResults } from '../../models/search.model';
import { EnvironmentService } from '../../services/environment.service';

/** The top bar search. The API scopes results to the caller's role and team. */
@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  search(query: string, limit = 5): Observable<SearchResults> {
    const params = new HttpParams().set('q', query).set('limit', limit);
    return this.http
      .get<ApiResponse<SearchResults>>(`${this.env.settings?.restUrl}/search`, { params })
      .pipe(map(response => response.data));
  }
}
