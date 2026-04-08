import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MetagameService, MetagameArchetype } from '../../core/services/metagame.service';
import { ThemeService } from '../../core/services/theme.service';
import { trigger, state, style, transition, animate } from '@angular/animations';

@Component({
  selector: 'app-metagame',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metagame.html',
  styleUrl: './metagame.scss',
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({ height: '0px', minHeight: '0', opacity: 0 })),
      state('expanded', style({ height: '*', opacity: 1 })),
      transition('expanded <=> collapsed', animate('300ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
  ]
})
export class Metagame implements OnInit {
  archetypes: MetagameArchetype[] = [];
  expandedArchetypeId: number | null = null;
  loading = true;
  activeFilter = 'Todos';
  filters = ['Todos', 'Aggro', 'Control', 'Midrange', 'Combo'];

  constructor(
    private metagameService: MetagameService,
    private themeService: ThemeService
  ) {}

  ngOnInit() {
    this.themeService.loadTheme().subscribe();
    this.loadMetagame();
  }

  loadMetagame() {
    this.loading = true;
    this.metagameService.getMetagame().subscribe({
      next: (data) => {
        this.archetypes = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading metagame:', err);
        this.loading = false;
      }
    });
  }

  toggleExpand(id: number) {
    this.expandedArchetypeId = this.expandedArchetypeId === id ? null : id;
  }

  getGroupedCards(cards: any[]) {
    return {
      creatures: cards.filter(c => !c.isSideboard && c.name.toLowerCase().includes('')), // Logic managed in backend but we can secondary sort
      spells: cards.filter(c => !c.isSideboard),
      sideboard: cards.filter(c => c.isSideboard)
    };
  }

  // Simplified categorization for demo (real logic should be in backend metadata)
  getFilteredArchetypes() {
    if (this.activeFilter === 'Todos') return this.archetypes;
    return this.archetypes.filter(a => a.name.includes(this.activeFilter));
  }

  setFilter(filter: string) {
    this.activeFilter = filter;
  }
}
