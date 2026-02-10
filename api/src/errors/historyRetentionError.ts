/**
 * Domain error thrown when a free user requests history data before the
 * retention cutoff date. Carries `cutoffDate` and `retentionDays` for
 * the stable 403 response contract (HISTORY_RETENTION_LIMIT).
 *
 * Follows the same pattern as PatientLimitError (009-free-limit-gates).
 */
export class HistoryRetentionError extends Error {
  readonly statusCode = 403;
  readonly cutoffDate: string;
  readonly retentionDays: number;

  constructor(cutoffDate: string, retentionDays: number) {
    super("履歴の閲覧は直近30日間に制限されています。");
    this.name = "HistoryRetentionError";
    this.cutoffDate = cutoffDate;
    this.retentionDays = retentionDays;
  }
}
