"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Check, ChevronRight } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { accountTypes } from "@/features/settings/accounts-page";
import { employmentTypes, payFrequencies, taxCodes } from "@/features/settings/options";
import { api } from "@/lib/api/client";
import { useAccounts, useApiMutation, useMe, useProfile } from "@/lib/api/queries";
import type {
  Account,
  AccountType,
  EmergencyFund,
  IncomeSource,
  IncomeSourceRequest,
  PayBreakdown,
  Profile,
  ProfileRequest,
} from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatMoney, parseDollars } from "@/lib/format";

const steps = ["About you", "Your income", "Your accounts", "Your safety net", "All set"] as const;

/**
 * The first few minutes after signing up: the essentials that make every number in the app
 * accurate, one short step at a time, before the person sees their home screen.
 */
export function OnboardingPage() {
  const [step, setStep] = useState(0);
  const { data: me } = useMe();
  const next = () => setStep((value) => Math.min(steps.length - 1, value + 1));

  return (
    <div className="min-h-dvh">
      <header className="bg-navy text-on-navy">
        <div className="mx-auto flex h-14 max-w-3xl items-center px-4 sm:px-6">
          <span className="text-xl font-bold tracking-tight text-white">Kiwi Finance</span>
        </div>
        <div className="h-3 bg-navy-2" aria-hidden />
      </header>
      <main className="mx-auto w-full max-w-3xl px-4 pb-16 pt-8 sm:px-6">
        <p className="text-sm text-muted">{me?.displayName ? `Kia ora, ${me.displayName}` : "Kia ora"}</p>
        <h1 className="mt-1 text-3xl font-semibold">Let&apos;s set up Kiwi Finance</h1>
        <p className="mt-2 text-ink-2">
          A few quick questions so your budget, goals and pay figures are right from the start. You can skip
          any step and come back to it in Settings.
        </p>

        <ol className="mt-6 grid grid-cols-5 gap-2" aria-label="Progress">
          {steps.map((label, index) => (
            <li key={label} aria-current={index === step ? "step" : undefined}>
              <span className={cn("block h-1.5 rounded-full", index <= step ? "bg-brand" : "bg-surface-3")} />
              <span
                className={cn(
                  "mt-1.5 hidden text-xs sm:block",
                  index === step ? "font-semibold text-ink" : "text-muted",
                )}
              >
                {label}
              </span>
            </li>
          ))}
        </ol>
        <p className="sr-only">
          Step {step + 1} of {steps.length}: {steps[step]}
        </p>

        <section
          className="mt-8 rounded-2xl border border-line bg-surface p-6 sm:p-8"
          aria-labelledby="step-heading"
        >
          {step === 0 ? <AboutYou onDone={next} /> : null}
          {step === 1 ? <Income onDone={next} /> : null}
          {step === 2 ? <Accounts onDone={next} /> : null}
          {step === 3 ? <SafetyNet onDone={next} /> : null}
          {step === 4 ? <AllSet /> : null}
        </section>

        {step > 0 && step < steps.length - 1 ? (
          <button
            type="button"
            onClick={() => setStep((value) => value - 1)}
            className="mt-4 text-sm text-brand hover:underline"
          >
            Back
          </button>
        ) : null}
      </main>
    </div>
  );
}

function StepHeading({ title, description }: { title: string; description: string }) {
  return (
    <div className="mb-6">
      <h2 id="step-heading" className="text-2xl font-semibold">
        {title}
      </h2>
      <p className="mt-1 text-ink-2">{description}</p>
    </div>
  );
}

function Actions({
  onSkip,
  saving,
  label = "Continue",
}: {
  onSkip: () => void;
  saving?: boolean;
  label?: string;
}) {
  return (
    <div className="mt-8 flex flex-wrap items-center gap-4">
      <Button type="submit" size="lg" disabled={saving}>
        {label}
      </Button>
      <button type="button" onClick={onSkip} className="text-sm text-brand hover:underline">
        Skip for now
      </button>
    </div>
  );
}

function AboutYou({ onDone }: { onDone: () => void }) {
  const { data: profile } = useProfile();
  if (!profile) return <Skeleton className="h-80" />;
  return <AboutYouForm profile={profile} onDone={onDone} />;
}

function AboutYouForm({ profile, onDone }: { profile: Profile; onDone: () => void }) {
  const toast = useToast();
  const [employment, setEmployment] = useState(profile.employmentType);
  const [frequency, setFrequency] = useState(profile.payFrequency);
  const [taxCode, setTaxCode] = useState(profile.taxCode);
  const [kiwiSaver, setKiwiSaver] = useState(profile.kiwiSaverMember);
  const [rate, setRate] = useState(String(profile.kiwiSaverRate ?? 0.035));
  const [studentLoan, setStudentLoan] = useState(profile.hasStudentLoan);
  const [firstHome, setFirstHome] = useState(profile.firstHomeBuyer);
  const save = useApiMutation((request: ProfileRequest) => api.put<Profile>("/profile", request));

  function submit(event: React.FormEvent) {
    event.preventDefault();
    save.mutate(
      {
        employmentType: employment,
        payFrequency: frequency,
        taxCode,
        hasStudentLoan: studentLoan,
        kiwiSaverMember: kiwiSaver,
        ...(kiwiSaver ? { kiwiSaverRate: Number(rate) } : {}),
        ...(profile.kiwiSaverBalance ? { kiwiSaverBalanceCents: profile.kiwiSaverBalance.cents } : {}),
        ...(profile.kiwiSaverJoinedOn ? { kiwiSaverJoinedOn: profile.kiwiSaverJoinedOn } : {}),
        firstHomeBuyer: firstHome,
        ...(profile.region ? { region: profile.region } : {}),
        ...(profile.housingType ? { housingType: profile.housingType } : {}),
        householdSize: profile.householdSize,
        dependants: profile.dependants,
        singleIncomeHousehold: profile.singleIncomeHousehold,
        ...(profile.dateOfBirth ? { dateOfBirth: profile.dateOfBirth } : {}),
        savingsInterestRate: profile.savingsInterestRate,
        targetSavingsRate: profile.targetSavingsRate,
      },
      { onSuccess: onDone, onError: (failure) => toast(failure.message, "error") },
    );
  }

  return (
    <form onSubmit={submit}>
      <StepHeading
        title="About you"
        description="This is how we work out your tax, KiwiSaver and student loan correctly."
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Work">
          {(props) => (
            <Select
              {...props}
              value={employment}
              onChange={(e) => setEmployment(e.target.value as Profile["employmentType"])}
            >
              {employmentTypes.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="How often you're paid">
          {(props) => (
            <Select
              {...props}
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as Profile["payFrequency"])}
            >
              {payFrequencies.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field
          label="Tax code"
          hint="It's on your payslip. Most people with one job are M."
          className="sm:col-span-2"
        >
          {(props) => (
            <Select
              {...props}
              value={taxCode}
              onChange={(e) => setTaxCode(e.target.value as Profile["taxCode"])}
            >
              {taxCodes.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
      </div>
      <div className="mt-4 space-y-3">
        <Toggle label="I'm in KiwiSaver" checked={kiwiSaver} onChange={setKiwiSaver} />
        {kiwiSaver ? (
          <Field label="KiwiSaver contribution">
            {(props) => (
              <Select {...props} value={rate} onChange={(e) => setRate(e.target.value)}>
                {["0.03", "0.035", "0.04", "0.06", "0.08", "0.1"].map((option) => (
                  <option key={option} value={option}>
                    {Math.round(Number(option) * 1000) / 10}% of pay
                  </option>
                ))}
              </Select>
            )}
          </Field>
        ) : null}
        <Toggle label="I'm repaying a student loan" checked={studentLoan} onChange={setStudentLoan} />
        <Toggle label="I'm saving for my first home" checked={firstHome} onChange={setFirstHome} />
      </div>
      <Actions onSkip={onDone} saving={save.isPending} />
    </form>
  );
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between gap-3 rounded-lg bg-surface p-4">
      <span className="font-medium">{label}</span>
      <Switch checked={checked} onCheckedChange={onChange} aria-label={label} />
    </label>
  );
}

function Income({ onDone }: { onDone: () => void }) {
  const toast = useToast();
  const { data: profile } = useProfile();
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState<IncomeSourceRequest["frequency"]>("ANNUALLY");
  const [type, setType] = useState<IncomeSourceRequest["type"]>("SALARY");
  const cents = parseDollars(amount);
  const save = useApiMutation((request: IncomeSourceRequest) =>
    api.post<IncomeSource>("/income-sources", request),
  );
  const preview = useQuery({
    queryKey: [
      "onboarding-pay",
      cents,
      frequency,
      profile?.taxCode,
      profile?.kiwiSaverRate,
      profile?.hasStudentLoan,
    ],
    queryFn: () =>
      api.post<PayBreakdown>("/income/pay-calculator", {
        amountCents: cents,
        frequency,
        basis: "GROSS",
        taxCode: profile!.taxCode,
        studentLoan: profile!.hasStudentLoan,
        ...(profile!.kiwiSaverMember && profile!.kiwiSaverRate != null
          ? { kiwiSaverRate: profile!.kiwiSaverRate }
          : {}),
      }),
    enabled: profile != null && cents != null && cents > 0,
    placeholderData: keepPreviousData,
  });
  const payPeriods = { WEEKLY: 52, FORTNIGHTLY: 26, FOUR_WEEKLY: 13, MONTHLY: 12, ANNUALLY: 1 };
  const perPay =
    preview.data && profile ? preview.data.annual.takeHome.cents / payPeriods[profile.payFrequency] : null;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (cents == null || cents <= 0) {
      onDone();
      return;
    }
    save.mutate(
      {
        name: type === "BENEFIT" ? "Benefit" : "Main job",
        type,
        amountCents: cents,
        frequency,
        basis: "GROSS",
        ...(profile ? { taxCode: profile.taxCode } : {}),
      },
      { onSuccess: onDone, onError: (failure) => toast(failure.message, "error") },
    );
  }

  return (
    <form onSubmit={submit}>
      <StepHeading
        title="Your income"
        description="Your pay before tax. If you connect your bank, we'll also spot it in your transactions."
      />
      <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_10rem]">
        <Field label="Pay before tax">
          {(props) => (
            <MoneyInput
              {...props}
              value={amount}
              placeholder="65000"
              onChange={(e) => setAmount(e.target.value)}
            />
          )}
        </Field>
        <Field label="Per">
          {(props) => (
            <Select
              {...props}
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as IncomeSourceRequest["frequency"])}
            >
              <option value="ANNUALLY">year</option>
              <option value="MONTHLY">month</option>
              <option value="FORTNIGHTLY">fortnight</option>
              <option value="WEEKLY">week</option>
            </Select>
          )}
        </Field>
        <Field label="It's from" className="sm:col-span-2">
          {(props) => (
            <Select
              {...props}
              value={type}
              onChange={(e) => setType(e.target.value as IncomeSourceRequest["type"])}
            >
              <option value="SALARY">A salary</option>
              <option value="WAGES">Hourly wages</option>
              <option value="SELF_EMPLOYED">Self-employment</option>
              <option value="BENEFIT">A benefit</option>
              <option value="OTHER">Something else</option>
            </Select>
          )}
        </Field>
      </div>
      {perPay != null && profile ? (
        <p className="mt-5 rounded-lg bg-surface p-4 text-ink-2">
          That&apos;s about <span className="figure text-2xl">{formatMoney(perPay, { whole: true })}</span>{" "}
          take-home each{" "}
          {payFrequencies
            .find((option) => option.value === profile.payFrequency)
            ?.label.toLowerCase()
            .replace("every ", "") ?? "pay"}{" "}
          after tax, ACC{profile.kiwiSaverMember ? ", KiwiSaver" : ""}
          {profile.hasStudentLoan ? " and student loan" : ""}.
        </p>
      ) : null}
      <Actions onSkip={onDone} saving={save.isPending} />
    </form>
  );
}

function Accounts({ onDone }: { onDone: () => void }) {
  const toast = useToast();
  const { data: accounts } = useAccounts();
  const [name, setName] = useState("");
  const [type, setType] = useState<AccountType>("EVERYDAY");
  const [balance, setBalance] = useState("");
  const create = useApiMutation((body: Record<string, unknown>) => api.post<Account>("/accounts", body));

  function add(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      toast("Give the account a name.", "error");
      return;
    }
    create.mutate(
      { name: name.trim(), type, currentBalanceCents: parseDollars(balance || "0") ?? 0 },
      {
        onSuccess: () => {
          setName("");
          setBalance("");
          toast("Account added");
        },
        onError: (failure) => toast(failure.message, "error"),
      },
    );
  }

  return (
    <div>
      <StepHeading
        title="Your accounts"
        description="Connecting your bank brings in balances and transactions automatically. Or add your accounts yourself."
      />
      <Link href="/connect" className={buttonVariants({ variant: "outline" })}>
        Connect your bank with Akahu
      </Link>
      <form
        onSubmit={add}
        className="mt-6 grid gap-3 sm:grid-cols-[minmax(0,1fr)_9rem_8rem_auto] sm:items-end"
      >
        <Field label="Account name">
          {(props) => (
            <Input {...props} value={name} placeholder="Everyday" onChange={(e) => setName(e.target.value)} />
          )}
        </Field>
        <Field label="Type">
          {(props) => (
            <Select {...props} value={type} onChange={(e) => setType(e.target.value as AccountType)}>
              {accountTypes.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Balance">
          {(props) => (
            <MoneyInput
              {...props}
              value={balance}
              placeholder="0"
              onChange={(e) => setBalance(e.target.value)}
            />
          )}
        </Field>
        <Button type="submit" variant="outline" disabled={create.isPending}>
          Add account
        </Button>
      </form>
      {accounts && accounts.length > 0 ? (
        <ul className="mt-5 divide-y divide-line rounded-lg bg-surface px-4">
          {accounts.map((account) => (
            <li key={account.id} className="flex items-center justify-between py-3">
              <span className="flex items-center gap-2 font-medium">
                <Check className="h-4 w-4 text-good" aria-hidden /> {account.name}
              </span>
              <span className="tabular">{formatMoney(account.balance)}</span>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="mt-8 flex flex-wrap items-center gap-4">
        <Button size="lg" onClick={onDone}>
          Continue
        </Button>
        {!accounts?.length ? (
          <button type="button" onClick={onDone} className="text-sm text-brand hover:underline">
            Skip for now
          </button>
        ) : null}
      </div>
    </div>
  );
}

function SafetyNet({ onDone }: { onDone: () => void }) {
  const toast = useToast();
  const { data: accounts } = useAccounts();
  const options = (accounts ?? []).filter((account) => account.liquid && account.type !== "CREDIT_CARD");
  const suggested = options.find((account) => account.type === "SAVINGS") ?? options[0];
  const [selected, setSelected] = useState<string | null>(null);
  const choice = selected ?? suggested?.id ?? "";
  const choose = useApiMutation((accountId: string) =>
    api.put<EmergencyFund>("/emergency-fund/account", { accountId }),
  );

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!choice) {
      onDone();
      return;
    }
    choose.mutate(choice, { onSuccess: onDone, onError: (failure) => toast(failure.message, "error") });
  }

  return (
    <form onSubmit={submit}>
      <StepHeading
        title="Your safety net"
        description="Which account holds your emergency fund? We'll size a target from your essential costs and help you build it."
      />
      {options.length === 0 ? (
        <p className="rounded-lg bg-surface p-4 text-ink-2">
          Add or connect an account first, then choose it here or on the Emergency fund page.
        </p>
      ) : (
        <fieldset>
          <legend className="sr-only">Accounts</legend>
          <ul className="divide-y divide-line rounded-lg bg-surface px-4">
            {options.map((account) => (
              <li key={account.id}>
                <label className="flex cursor-pointer items-center gap-3 py-3.5">
                  <input
                    type="radio"
                    name="safety-net"
                    checked={choice === account.id}
                    onChange={() => setSelected(account.id)}
                    className="h-5 w-5 accent-[var(--brand)]"
                  />
                  <span className="flex-1 font-medium">{account.name}</span>
                  <span className="tabular">{formatMoney(account.balance)}</span>
                </label>
              </li>
            ))}
          </ul>
        </fieldset>
      )}
      <Actions
        onSkip={onDone}
        saving={choose.isPending}
        label={options.length ? "Use this account" : "Continue"}
      />
    </form>
  );
}

function AllSet() {
  const router = useRouter();
  return (
    <div>
      <StepHeading
        title="You're all set"
        description="Your home screen is ready. Here are a few good next steps whenever you're ready."
      />
      <Button size="lg" onClick={() => router.replace("/home")}>
        Go to my home
      </Button>
      <div className="mt-6 flex flex-wrap gap-x-6 gap-y-2">
        {[
          ["/budget", "Create a budget"],
          ["/goals/new", "Set a goal"],
          ["/pay", "Check your pay"],
        ].map(([href, label]) => (
          <Link key={href} href={href!} className="inline-flex items-center gap-1 text-brand hover:underline">
            {label} <ChevronRight className="h-4 w-4" aria-hidden />
          </Link>
        ))}
      </div>
    </div>
  );
}
