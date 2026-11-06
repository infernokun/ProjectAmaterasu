import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Role } from '../../../enums/role.enum';
import { ImportResult } from './admin-users.service';
import { ImportUsersData, ImportUsersDialogComponent } from './import-users-dialog';

describe('ImportUsersDialogComponent', () => {
  let fixture: ComponentFixture<ImportUsersDialogComponent>;
  let data: ImportUsersData;
  let ref: jasmine.SpyObj<MatDialogRef<ImportUsersDialogComponent>>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    ref = jasmine.createSpyObj('MatDialogRef', ['close']);
    data = {
      teams: [{ id: 't1', name: 'Red Fox' }],
      existing: ['sbell'],
      run: jasmine.createSpy('run'),
    };
    TestBed.configureTestingModule({
      imports: [ImportUsersDialogComponent],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: ref }],
    });
    fixture = TestBed.createComponent(ImportUsersDialogComponent);
    fixture.detectChanges();
  });

  async function choose(text: string): Promise<void> {
    const input = el().querySelector<HTMLInputElement>('input[type=file]')!;
    const transfer = new DataTransfer();
    transfer.items.add(new File([text], 'users.csv', { type: 'text/csv' }));
    input.files = transfer.files;
    input.dispatchEvent(new Event('change'));
    await new Promise(resolve => setTimeout(resolve, 20));
    fixture.detectChanges();
  }

  const importButton = () => [...el().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent!.includes('Import '))!;

  it('explains the columns before a file is chosen', () => {
    expect(el().textContent).toContain('username is required');
    expect(importButton().disabled).toBeTrue();
  });

  it('previews every row and imports only the ready ones', async () => {
    await choose('username,role,team\njmorales,member,Red Fox\nsbell,admin,\nlchen,facilitator,\n');
    const rows = el().querySelectorAll('tbody tr');
    expect(rows.length).toBe(3);
    expect(rows[1].textContent).toContain('An account with this name already exists');
    expect(el().textContent).toContain('2 ready');
    expect(el().textContent).toContain('1 with problems');
    expect(importButton().textContent).toContain('Import 2 accounts');

    const result: ImportResult = {
      created: [{ row: 2, id: 'n1', username: 'jmorales', role: Role.MEMBER, team: 'Red Fox' }],
      skipped: [{ row: 4, username: 'lchen', reason: 'An account named lchen already exists' }],
    };
    (data.run as jasmine.Spy).and.resolveTo(result);
    importButton().click();
    await fixture.whenStable();
    fixture.detectChanges();

    const sent = (data.run as jasmine.Spy).calls.mostRecent().args[0];
    expect(sent.map((row: { username: string }) => row.username)).toEqual(['jmorales', 'lchen']);
    expect(el().textContent).toContain('1 created, 2 skipped.');
    const reasons = [...el().querySelectorAll('tbody tr')].map(row => row.textContent);
    expect(reasons[0]).toContain('sbell');
    expect(reasons[1]).toContain('An account named lchen already exists');
    expect(el().textContent).toContain('Download passwords');
  });

  it('says why a file cannot be used', async () => {
    await choose('name,team\nx,y\n');
    expect(el().querySelector('[role=alert]')!.textContent).toContain('username column');
    expect(importButton().disabled).toBeTrue();
  });
});
