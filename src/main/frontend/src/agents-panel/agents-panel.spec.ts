import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentsPanel } from './agents-panel';

describe('AgentsPanel', () => {
  let component: AgentsPanel;
  let fixture: ComponentFixture<AgentsPanel>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentsPanel]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AgentsPanel);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
