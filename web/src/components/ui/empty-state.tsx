import { CircleAlert, Inbox, type LucideIcon } from "lucide-react";

export function EmptyState({
  title,
  message,
  action,
  icon: Glyph = Inbox,
}: {
  title: string;
  message: React.ReactNode;
  action?: React.ReactNode;
  icon?: LucideIcon;
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-line bg-surface px-6 py-12 text-center">
      <span className="grid h-12 w-12 place-items-center rounded-full bg-brand-soft text-brand">
        <Glyph className="h-6 w-6" aria-hidden />
      </span>
      <h3 className="text-lg font-semibold">{title}</h3>
      <p className="max-w-md text-ink-2">{message}</p>
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-2xl border border-line bg-surface px-6 py-12 text-center"
    >
      <span className="grid h-12 w-12 place-items-center rounded-full bg-danger-soft text-danger">
        <CircleAlert className="h-6 w-6" aria-hidden />
      </span>
      <h3 className="text-lg font-semibold">That didn&apos;t work</h3>
      <p className="max-w-md text-ink-2">{message}</p>
      {onRetry ? (
        <button type="button" onClick={onRetry} className="font-semibold text-brand hover:underline">
          Try again
        </button>
      ) : null}
    </div>
  );
}
