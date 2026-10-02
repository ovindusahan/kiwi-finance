"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Trophy, X } from "lucide-react";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import type { Goal } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

const COLOURS = ["#0b6aa8", "#2a78d6", "#eda100", "#1baf7a", "#e87ba4", "#eb6834"];

type Piece = {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  angle: number;
  spin: number;
  colour: string;
  round: boolean;
};

/** Bursts of confetti from both sides of the screen. Skipped for people who prefer less motion. */
function Confetti() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const ratio = window.devicePixelRatio || 1;
    const resize = () => {
      canvas.width = window.innerWidth * ratio;
      canvas.height = window.innerHeight * ratio;
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
    };
    resize();
    window.addEventListener("resize", resize);

    const width = window.innerWidth;
    const height = window.innerHeight;
    const pieces: Piece[] = [];
    const burst = (fromLeft: boolean) => {
      for (let index = 0; index < 90; index++) {
        const angle = (fromLeft ? -60 : -120) + (Math.random() - 0.5) * 50;
        const speed = 9 + Math.random() * 9;
        pieces.push({
          x: fromLeft ? -10 : width + 10,
          y: height * 0.75,
          vx: Math.cos((angle * Math.PI) / 180) * speed,
          vy: Math.sin((angle * Math.PI) / 180) * speed,
          size: 6 + Math.random() * 6,
          angle: Math.random() * Math.PI,
          spin: (Math.random() - 0.5) * 0.3,
          colour: COLOURS[index % COLOURS.length]!,
          round: Math.random() < 0.3,
        });
      }
    };
    burst(true);
    burst(false);
    const second = window.setTimeout(() => {
      burst(true);
      burst(false);
    }, 450);

    let frame = 0;
    const started = performance.now();
    const draw = (now: number) => {
      const elapsed = now - started;
      context.clearRect(0, 0, width, height);
      context.globalAlpha = elapsed > 3200 ? Math.max(0, 1 - (elapsed - 3200) / 800) : 1;
      for (const piece of pieces) {
        piece.vy += 0.32;
        piece.vx *= 0.985;
        piece.vy *= 0.985;
        piece.x += piece.vx;
        piece.y += piece.vy;
        piece.angle += piece.spin;
        context.save();
        context.translate(piece.x, piece.y);
        context.rotate(piece.angle);
        context.fillStyle = piece.colour;
        if (piece.round) {
          context.beginPath();
          context.arc(0, 0, piece.size / 2.4, 0, Math.PI * 2);
          context.fill();
        } else {
          context.fillRect(-piece.size / 2, -piece.size / 4, piece.size, piece.size / 2);
        }
        context.restore();
      }
      if (elapsed < 4000) frame = requestAnimationFrame(draw);
      else context.clearRect(0, 0, width, height);
    };
    frame = requestAnimationFrame(draw);

    return () => {
      cancelAnimationFrame(frame);
      window.clearTimeout(second);
      window.removeEventListener("resize", resize);
    };
  }, []);

  return (
    <canvas ref={canvasRef} aria-hidden className="pointer-events-none fixed inset-0 z-[60] h-full w-full" />
  );
}

function monthsBetween(from: string, to: Date) {
  const start = new Date(from);
  return Math.max(1, (to.getFullYear() - start.getFullYear()) * 12 + to.getMonth() - start.getMonth());
}

/** The moment a goal is reached: confetti, the amount saved, and what to do next. */
export function GoalCelebration({
  goal,
  onOpenChange,
  onNewGoal,
}: {
  goal: Goal | null;
  onOpenChange: (open: boolean) => void;
  onNewGoal: () => void;
}) {
  const months = goal ? monthsBetween(goal.createdAt, new Date()) : 0;
  const freed = goal?.monthlyContribution.cents ?? 0;

  return (
    <DialogPrimitive.Root open={goal != null} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/45" />
        {goal ? <Confetti key={goal.id} /> : null}
        <DialogPrimitive.Content className="animate-pop fixed left-1/2 top-1/2 z-50 w-[min(440px,92vw)] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-2xl border border-line bg-surface text-center shadow-pop">
          <DialogPrimitive.Close
            className="absolute right-3 top-3 rounded-full p-2 text-muted hover:bg-surface-2 hover:text-ink"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </DialogPrimitive.Close>
          <div className="bg-gradient-to-b from-gold-soft to-surface px-6 pb-2 pt-9">
            <span className="mx-auto grid h-20 w-20 place-items-center rounded-full bg-surface shadow-[0_0_0_8px_rgba(237,161,0,0.18)]">
              <Trophy className="h-10 w-10 text-gold" aria-hidden />
            </span>
            <p className="mt-5 text-sm font-semibold uppercase tracking-wider text-gold">Goal reached</p>
            <DialogPrimitive.Title className="mt-1 text-2xl font-semibold">
              {goal ? `You did it. ${goal.name} is fully funded!` : "Goal reached"}
            </DialogPrimitive.Title>
          </div>
          <div className="px-6 pb-6">
            <DialogPrimitive.Description className="text-ink-2">
              {goal
                ? `You saved ${formatMoney(goal.target, { whole: true })} in ${months} month${months === 1 ? "" : "s"}.`
                : ""}
            </DialogPrimitive.Description>
            {freed > 0 ? (
              <p className="mt-3 rounded-xl bg-panel px-4 py-3 text-sm text-ink-2">
                The {formatMoney(freed, { whole: true })} a month you were putting in is now free for your
                other goals.
              </p>
            ) : null}
            <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
              <Button onClick={onNewGoal}>Start a new goal</Button>
              <DialogPrimitive.Close asChild>
                <Button variant="secondary">Done</Button>
              </DialogPrimitive.Close>
            </div>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
