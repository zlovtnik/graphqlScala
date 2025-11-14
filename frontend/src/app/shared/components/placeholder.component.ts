import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzResultModule } from 'ng-zorro-antd/result';

@Component({
  selector: 'app-placeholder',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterModule, NzButtonModule, NzResultModule],
  template: `
    <nz-result
      nzStatus="info"
      [nzTitle]="title"
      nzSubTitle="We are working hard to bring this feature online."
    >
      <div nz-result-extra>
        <a nz-button nzType="primary" [routerLink]="['/main']">
          Back to Dashboard
        </a>
      </div>
    </nz-result>
  `,
})
export class PlaceholderComponent {
  private readonly route = inject(ActivatedRoute);

  get title(): string {
    return `${this.getRouteLabel()} — Coming Soon`;
  }

  private getRouteLabel(): string {
    // First, check for explicit title override in route data
    if (this.route.snapshot.data?.['title']) {
      return this.route.snapshot.data['title'];
    }

    // Get raw path from route config
    const rawPath = this.route.snapshot.routeConfig?.path ?? '';

    // Split by '/', filter out empty segments and route parameters (starting with ':')
    const segments = rawPath
      .split('/')
      .filter(segment => segment && !segment.startsWith(':'));

    // If no printable segments remain, return 'Home'
    if (segments.length === 0) {
      return 'Home';
    }

    // Transform each segment: replace [-_] with spaces, Title-case each word
    return segments
      .map(segment => {
        return segment
          .replace(/[-_]/g, ' ')
          .split(' ')
          .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
          .join(' ');
      })
      .join(' ');
  }
}
