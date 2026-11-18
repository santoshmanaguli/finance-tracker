const AMOUNT = /^(\d+)(?:\.(\d{1,2}))?$/;

/**
 * Converts the amount text banks write ("500", "1,234.5", "1,23,456.78") into integer paise.
 *
 * Pure string arithmetic, never floating point: in JavaScript `1234.56 * 100` is
 * 123455.99999999999, which truncates to the wrong number of paise.
 * Currency prefixes ("Rs.", "INR") are the parser regex's job, not this function's.
 *
 * @throws RangeError if not a valid non-negative amount with at most 2 decimals
 */
export function toPaise(amountText: string): number {
  const match = AMOUNT.exec(amountText.trim().replaceAll(",", ""));
  if (!match) {
    throw new RangeError(`not an amount: ${amountText}`);
  }
  const rupees = match[1] ?? "0";
  const fraction = (match[2] ?? "").padEnd(2, "0");
  const paise = Number(rupees) * 100 + Number(fraction);
  if (!Number.isSafeInteger(paise)) {
    throw new RangeError(`amount out of range: ${amountText}`);
  }
  return paise;
}
