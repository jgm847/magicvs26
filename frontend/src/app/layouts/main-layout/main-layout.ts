import { Component } from '@angular/core';
import { Router, RouterOutlet, RouterLink, NavigationEnd } from '@angular/router';
import { NgIf } from '@angular/common';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-main-layout',
  imports: [RouterOutlet, RouterLink, NgIf],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  isLoggedIn = false;

  constructor(private router: Router) {
    this.isLoggedIn = !!localStorage.getItem('user');

    // Re-check login state after every navigation (e.g. after login redirects to /)
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.isLoggedIn = !!localStorage.getItem('user');
      });
  }

  logout(): void {
    localStorage.removeItem('user');
    this.isLoggedIn = false;
    this.router.navigateByUrl('/login');
  }
}
