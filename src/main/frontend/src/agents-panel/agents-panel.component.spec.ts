import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentsPanelComponent } from './agents-panel.component';

describe('AgentsPanelComponent', () => {
  let component: AgentsPanelComponent;
  let fixture: ComponentFixture<AgentsPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentsPanelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AgentsPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});