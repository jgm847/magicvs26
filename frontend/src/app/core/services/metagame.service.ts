import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ArchetypeDeckCard {
  name: string;
  quantity: number;
  isSideboard: boolean;
  imageUrl: string;
  scryfallId: string;
}

export interface MetagameArchetype {
  id: number;
  name: string;
  tier: string;
  metaPercentage: number;
  winRate: number;
  topPlayer: string;
  creaturesCount: number;
  spellsCount: number;
  landsCount: number;
  cards: ArchetypeDeckCard[];
}

@Injectable({
  providedIn: 'root'
})
export class MetagameService {
  private readonly API_URL = 'http://localhost:8080/api/v1/metagame';

  constructor(private http: HttpClient) {}

  getMetagame(): Observable<MetagameArchetype[]> {
    return this.http.get<MetagameArchetype[]>(this.API_URL);
  }

  triggerScrape(): Observable<string> {
    return this.http.post<string>(`${this.API_URL}/scrape`, {});
  }
}
