import {
  centsToDollarsInput,
  formatMoney,
  formatPercent,
  formatRelative,
  greeting,
  parseDate,
  parseDollars,
} from "./format";

describe("formatMoney", () => {
  it("formats cents as NZD", () => {
    expect(formatMoney(123_450)).toBe("$1,234.50");
    expect(formatMoney({ cents: -9_99, currency: "NZD" })).toBe("-$9.99");
  });

  it("rounds to whole dollars and adds a sign when asked", () => {
    expect(formatMoney(123_450, { whole: true })).toBe("$1,235");
    expect(formatMoney(5_000, { signed: true })).toBe("+$50.00");
    expect(formatMoney(0, { signed: true })).toBe("$0.00");
  });

  it("treats a missing amount as zero", () => {
    expect(formatMoney(null)).toBe("$0.00");
  });
});

describe("parseDollars", () => {
  it.each([
    ["1,234.5", 123_450],
    ["$20", 2_000],
    ["0.07", 7],
    ["-15.25", -1_525],
    [" 3 000 ", 300_000],
  ])("reads %s as %i cents", (input, cents) => {
    expect(parseDollars(input)).toBe(cents);
  });

  it.each(["", "abc", "1.234", "12.3.4"])("rejects %s", (input) => {
    expect(parseDollars(input)).toBeNull();
  });

  it("round-trips with the input formatter", () => {
    expect(centsToDollarsInput(250_000)).toBe("2500");
    expect(centsToDollarsInput(1_999)).toBe("19.99");
    expect(parseDollars(centsToDollarsInput(1_999))).toBe(1_999);
  });
});

describe("dates", () => {
  it("parses calendar dates without shifting the day", () => {
    const date = parseDate("2026-04-01");
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 3, 1]);
  });

  it("describes how long ago something happened", () => {
    const now = new Date("2026-10-01T12:00:00Z");
    expect(formatRelative("2026-10-01T11:59:30Z", now)).toBe("just now");
    expect(formatRelative("2026-10-01T11:00:00Z", now)).toBe("1 hour ago");
    expect(formatRelative("2026-09-28T12:00:00Z", now)).toBe("3 days ago");
    expect(formatRelative(null, now)).toBe("never");
  });

  it("greets in te reo Māori by time of day", () => {
    expect(greeting(new Date(2026, 9, 1, 8))).toBe("Mōrena");
    expect(greeting(new Date(2026, 9, 1, 14))).toBe("Kia ora");
    expect(greeting(new Date(2026, 9, 1, 21))).toBe("Pō mārie");
  });
});

describe("formatPercent", () => {
  it("formats fractions", () => {
    expect(formatPercent(0.1338)).toBe("13%");
    expect(formatPercent(0.1338, 1)).toBe("13.4%");
    expect(formatPercent(null)).toBe("");
  });
});
