import { Component, Input, SimpleChanges, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { LabTracker } from '../../models/lab-tracker.model';
import { MatTableDataSource } from '@angular/material/table';
import { LabTrackerService } from '../../services/lab-tracker.service';

@Component({
  selector: 'app-audit-log',
  templateUrl: './audit-log.component.html',
  styleUrls: ['./audit-log.component.scss']
})
export class AuditLogComponent {
  @ViewChild('auditLogSort') intersectionTableSort = new MatSort();
  @ViewChild(MatPaginator) paginator!: MatPaginator | null;

  @Input()
  labTrackers: LabTracker[] | undefined | null;

  @Input()
  actionsEnabled: boolean = false;

  outputColumns = [
    'id',
    'createdBy',
    'createdAt',
    'updatedAt',
    'labStarted',
    'labStatus',
    'labOwner',
  ];

  auditLogDataSource: MatTableDataSource<LabTracker> =
    new MatTableDataSource<LabTracker>();

  constructor(private labTrackerService: LabTrackerService) { }

  ngOnInit() {
    this.labTrackerService.getAllLabTrackers().subscribe((labTrackers) => {
      this.auditLogDataSource = new MatTableDataSource<LabTracker>(labTrackers);

      // Set the custom filter predicate here to filter all fields
      this.auditLogDataSource.filterPredicate = (data: LabTracker, filter: string) => {
        const filterLower = filter.toLowerCase();

        // Loop through each property of the data object
        return Object.entries(data).some(([key, value]) =>
          value && value.toString().toLowerCase().includes(filterLower)
        );
      };

      this.auditLogDataSource.sort = this.intersectionTableSort;
      this.auditLogDataSource.paginator = this.paginator;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    console.log('AuditLogComponent ngOnChanges', changes);

    // Toggle 'relevance' and 'actions' columns
    if (changes && changes['actionsEnabled'] && changes['actionsEnabled']['currentValue']) {
      this.outputColumns.push('relevance', 'actions');
    }
    if (changes && changes['actionsEnabled'] && !changes['actionsEnabled']['currentValue']) {
      this.outputColumns = this.outputColumns.filter(
        (val) => val !== 'actions' && val != 'relevance'
      );
    }

    // Ensure that the table data is correctly sorted and paginated
    this.auditLogDataSource.sort = this.intersectionTableSort;
    this.auditLogDataSource.paginator = this.paginator;
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    if (this.auditLogDataSource) {
      this.auditLogDataSource.filter = filterValue.trim().toLowerCase();
    }
  }
}
