import { Injectable, Renderer2, RendererFactory2 } from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SidenavService {
  private sidenavs: { [key: string]: MatSidenav } = {};
  private renderer: Renderer2;
  private readonly activePanelSubject = new BehaviorSubject<string | null>(null);
  public readonly activePanel$ = this.activePanelSubject.asObservable();

  constructor(private rendererFactory: RendererFactory2) {
    this.renderer = this.rendererFactory.createRenderer(null, null);
  }

  registerSidenav(id: string, sidenav: MatSidenav): void {
    this.sidenavs[id] = sidenav;
  }

  open(id: string): void {
    const currentActivePanel = this.activePanelSubject.value;

    // If opening the same panel, do nothing
    if (currentActivePanel === id) {
      return;
    }

    // Apply shared axis transition for panel switching
    if (currentActivePanel) {
      this.applySharedAxisTransition(currentActivePanel, id);
    }

    // Close all other sidenavs with exit animation
    Object.entries(this.sidenavs).forEach(([sidenavId, sidenav]) => {
      if (sidenavId !== id && sidenav.opened) {
        this.applySidenavExitAnimation(sidenav);
        sidenav.close();
      }
    });

    // Open the requested sidenav with entrance animation
    const sidenav = this.sidenavs[id];
    if (sidenav && !sidenav.opened) {
      this.applySidenavEnterAnimation(sidenav);
      sidenav.open();
      this.activePanelSubject.next(id);
    }
  }

  close(id: string): void {
    const sidenav = this.sidenavs[id];
    if (sidenav && sidenav.opened) {
      this.applySidenavExitAnimation(sidenav);
      sidenav.close();
      this.activePanelSubject.next(null);
    }
  }

  toggle(id: string): void {
    const sidenav = this.sidenavs[id];
    if (!sidenav) return;

    if (sidenav.opened) {
      this.close(id);
    } else {
      this.open(id);
    }
  }

  /**
   * Apply Material Design container transform entrance animation
   */
  private applySidenavEnterAnimation(sidenav: MatSidenav): void {
    // Use element ID to find the sidenav element in DOM since _elementRef is private
    const element = document.querySelector(`mat-sidenav[data-panel-id]`) as HTMLElement;
    if (element) {
      this.renderer.addClass(element, 'md-panel-enter');

      // Remove the class after animation completes
      setTimeout(() => {
        this.renderer.removeClass(element, 'md-panel-enter');
      }, 450); // Duration matches --md-sys-motion-duration-long1
    }
  }

  /**
   * Apply Material Design container transform exit animation
   */
  private applySidenavExitAnimation(sidenav: MatSidenav): void {
    // Use element ID to find the sidenav element in DOM since _elementRef is private
    const element = document.querySelector(`mat-sidenav[data-panel-id]`) as HTMLElement;
    if (element) {
      this.renderer.addClass(element, 'md-panel-exit');

      // Remove the class after animation completes
      setTimeout(() => {
        this.renderer.removeClass(element, 'md-panel-exit');
      }, 250); // Duration matches --md-sys-motion-duration-medium
    }
  }

  /**
   * Apply Material Design shared axis transition between panels
   * For now, this is implemented through CSS classes applied to all sidenav elements
   */
  private applySharedAxisTransition(fromPanelId: string, toPanelId: string): void {
    // Apply shared axis transition using CSS classes
    const sidenavElements = document.querySelectorAll('mat-sidenav');
    sidenavElements.forEach(element => {
      this.renderer.addClass(element, 'md-navigation-transition');
    });

    // Clean up classes after transition
    setTimeout(() => {
      sidenavElements.forEach(element => {
        this.renderer.removeClass(element, 'md-navigation-transition');
      });
    }, 250); // Duration matches --md-sys-motion-duration-medium
  }

  /**
   * Get the currently active panel ID
   */
  getActivePanel(): string | null {
    return this.activePanelSubject.value;
  }

  /**
   * Notify that a panel was closed externally (e.g., by backdrop click)
   */
  notifyPanelClosed(id: string): void {
    if (this.activePanelSubject.value === id) {
      this.activePanelSubject.next(null);
    }
  }
}
