import { ComponentFixture, TestBed } from '@angular/core/testing';

import { McpServersPanelComponent } from './mcp-servers-panel.component';

describe('McpServersPanelComponent', () => {
  let component: McpServersPanelComponent;
  let fixture: ComponentFixture<McpServersPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [McpServersPanelComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(McpServersPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});