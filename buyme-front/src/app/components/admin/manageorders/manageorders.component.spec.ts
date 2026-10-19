import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ManageordersComponent } from './manageorders.component';

describe('ManageordersComponent', () => {
  let component: ManageordersComponent;
  let fixture: ComponentFixture<ManageordersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ManageordersComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ManageordersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});