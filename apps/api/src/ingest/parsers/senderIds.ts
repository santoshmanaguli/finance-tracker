/**
 * Indian commercial SMS senders (TRAI DLT) look like `AX-HDFCBK` or `VM-HDFCBK-S`:
 * - 2-letter prefix = telecom operator + circle (varies by route, NOT stable)
 * - 6-ish character header = the brand (stable: this identifies the bank)
 * - optional suffix -S/-T/-P/-G = service / transactional / promotional / government
 * The same bank can reach you as AX-HDFCBK today and JD-HDFCBK tomorrow, so parsers are keyed
 * on the header alone.
 */
const DLT = /^[A-Z]{2}-([A-Z0-9]{3,9})(?:-[A-Z])?$/;

export function normalizeSender(sender: string | null | undefined): string {
  if (!sender) return "";
  const s = sender.trim().toUpperCase();
  return DLT.exec(s)?.[1] ?? s;
}
