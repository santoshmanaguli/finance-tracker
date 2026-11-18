const NON_ALNUM = /[^A-Z0-9]+/g;

/**
 * Turns the many spellings banks use for one merchant into one comparable key:
 *   "Swiggy Ltd"          -> "SWIGGY LTD"
 *   "SWIGGY*BANGALORE"    -> "SWIGGY BANGALORE"
 *   "swiggy.in@axisbank"  -> "SWIGGY IN"     (UPI VPA: only the part before '@')
 *   "  Big  Basket  "     -> "BIG BASKET"
 * Rules then match with CONTAINS "SWIGGY". A pure function: trivially unit-testable.
 */
export function normalizeMerchant(merchantRaw: string | null | undefined): string | null {
  if (!merchantRaw || merchantRaw.trim() === "") return null;
  let s = merchantRaw.trim();
  const at = s.indexOf("@");
  if (at >= 0) {
    s = s.slice(0, at); // UPI VPA: the bank handle (@okaxis, @ybl) is noise
  }
  // toUpperCase() is locale-independent in JS (unlike Java's default-locale version).
  s = s.toUpperCase().replace(NON_ALNUM, " ").trim();
  return s === "" ? null : s;
}
