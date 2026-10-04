import { render, screen } from "@testing-library/react";
import { ProgressBar, ProgressRing } from "./progress";

describe("ProgressBar", () => {
  it("reports its value to assistive technology and clamps it", () => {
    render(<ProgressBar value={1.4} label="Budget used" />);
    const bar = screen.getByRole("progressbar", { name: "Budget used" });
    expect(bar).toHaveAttribute("aria-valuenow", "100");
  });

  it("rounds partial progress", () => {
    render(<ProgressBar value={0.636} label="Emergency fund" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "64");
  });
});

describe("ProgressRing", () => {
  it("labels the ring and renders its content", () => {
    render(
      <ProgressRing value={0.69} label="Kiwi Score 69 out of 100">
        <span>69</span>
      </ProgressRing>,
    );
    expect(screen.getByRole("img", { name: "Kiwi Score 69 out of 100" })).toBeInTheDocument();
    expect(screen.getByText("69")).toBeInTheDocument();
  });
});
