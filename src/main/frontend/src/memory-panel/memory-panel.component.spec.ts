import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemoryPanelComponent } from './memory-panel.component';

describe('MemoryPanelComponent', () => {
  let component: MemoryPanelComponent;
  let fixture: ComponentFixture<MemoryPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MemoryPanelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MemoryPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
