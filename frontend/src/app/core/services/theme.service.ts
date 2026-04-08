import { Injectable, Renderer2, RendererFactory2 } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

interface ThemeSettings {
  primaryColor: String;
  secondaryColor: String;
  tertiaryColor: String;
  neutralColor: String;
  headlineFont: String;
  bodyFont: String;
}

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private renderer: Renderer2;
  private readonly API_URL = 'http://localhost:8080/api/v1/theme/active';

  constructor(private http: HttpClient, rendererFactory: RendererFactory2) {
    this.renderer = rendererFactory.createRenderer(null, null);
  }

  loadTheme(): Observable<ThemeSettings> {
    return this.http.get<ThemeSettings>(this.API_URL).pipe(
      tap(theme => this.applyTheme(theme))
    );
  }

  private applyTheme(theme: ThemeSettings) {
    const root = document.documentElement;
    this.renderer.setStyle(root, '--color-primary', theme.primaryColor);
    this.renderer.setStyle(root, '--color-secondary', theme.secondaryColor);
    this.renderer.setStyle(root, '--color-tertiary', theme.tertiaryColor);
    this.renderer.setStyle(root, '--color-neutral', theme.neutralColor);
    this.renderer.setStyle(root, '--font-headline', theme.headlineFont);
    this.renderer.setStyle(root, '--font-body', theme.bodyFont);
    
    console.log('Theme applied:', theme.primaryColor);
  }
}
