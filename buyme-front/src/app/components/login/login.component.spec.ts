import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { LoginService } from '../../services/login.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let router: jasmine.SpyObj<Router>;
  let loginService: jasmine.SpyObj<LoginService>;

  beforeEach(async () => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    loginService = jasmine.createSpyObj<LoginService>('LoginService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: LoginService, useValue: loginService }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('redirects admins to the admin dashboard after login', () => {
    loginService.login.and.returnValue(of({
      token: 'admin-token',
      roles: ['ADMIN'],
      message: 'Login successful'
    }));

    component.onLogin();

    expect(router.navigate).toHaveBeenCalledWith(['/admin/dashboard']);
  });
});
