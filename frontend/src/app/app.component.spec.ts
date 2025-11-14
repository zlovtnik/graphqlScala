import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HttpClientModule } from '@angular/common/http';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { icons } from './icons-provider';
import { AppComponent } from './app.component';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzNotificationService } from 'ng-zorro-antd/notification';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AppComponent,
        RouterTestingModule,
        BrowserAnimationsModule,
        HttpClientModule,
        NzMenuModule,
        NzIconModule
      ],
      providers: [
        provideNzIcons(icons),
        { provide: NzMessageService, useValue: jasmine.createSpyObj('NzMessageService', ['create', 'success', 'error', 'info', 'warning']) },
        { provide: NzNotificationService, useValue: jasmine.createSpyObj('NzNotificationService', ['create', 'success', 'error', 'info', 'warning']) }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'SSF GraphQL Platform' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('SSF GraphQL Platform');
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('SSF GraphQL Platform');
  });

  it('should have NzMessageService with success method', () => {
    const messageService = TestBed.inject(NzMessageService);
    messageService.success('Test message');
    expect(messageService.success).toHaveBeenCalledWith('Test message');
  });

  it('should have NzMessageService with error method', () => {
    const messageService = TestBed.inject(NzMessageService);
    messageService.error('Error message');
    expect(messageService.error).toHaveBeenCalledWith('Error message');
  });

  it('should have NzMessageService with info method', () => {
    const messageService = TestBed.inject(NzMessageService);
    messageService.info('Info message');
    expect(messageService.info).toHaveBeenCalledWith('Info message');
  });

  it('should have NzMessageService with warning method', () => {
    const messageService = TestBed.inject(NzMessageService);
    messageService.warning('Warning message');
    expect(messageService.warning).toHaveBeenCalledWith('Warning message');
  });

  it('should have NzNotificationService with success method', () => {
    const notificationService = TestBed.inject(NzNotificationService);
    notificationService.success('Success title', 'Success content');
    expect(notificationService.success).toHaveBeenCalledWith('Success title', 'Success content');
  });

  it('should have NzNotificationService with error method', () => {
    const notificationService = TestBed.inject(NzNotificationService);
    notificationService.error('Error title', 'Error content');
    expect(notificationService.error).toHaveBeenCalledWith('Error title', 'Error content');
  });

  it('should have NzNotificationService with info method', () => {
    const notificationService = TestBed.inject(NzNotificationService);
    notificationService.info('Info title', 'Info content');
    expect(notificationService.info).toHaveBeenCalledWith('Info title', 'Info content');
  });

  it('should have NzNotificationService with warning method', () => {
    const notificationService = TestBed.inject(NzNotificationService);
    notificationService.warning('Warning title', 'Warning content');
    expect(notificationService.warning).toHaveBeenCalledWith('Warning title', 'Warning content');
  });
});
