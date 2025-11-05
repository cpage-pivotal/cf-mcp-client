import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentMessageDialog } from './agent-message-dialog';

describe('AgentMessageDialog', () => {
  let component: AgentMessageDialog;
  let fixture: ComponentFixture<AgentMessageDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentMessageDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AgentMessageDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
