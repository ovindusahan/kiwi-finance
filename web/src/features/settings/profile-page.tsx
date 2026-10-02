"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/empty-state";
import { Field, Input, MoneyInput, Select } from "@/components/ui/field";
import { PageSkeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { api, ApiError } from "@/lib/api/client";
import { useApiMutation, useProfile } from "@/lib/api/queries";
import type { Profile, ProfileRequest } from "@/lib/api/types";
import { centsToDollarsInput, parseDollars } from "@/lib/format";
import { employmentTypes, housingTypes, kiwiSaverRates, payFrequencies, regions, taxCodes } from "./options";
import { SettingsFrame } from "./settings-nav";

type Form = {
  employmentType: Profile["employmentType"];
  payFrequency: Profile["payFrequency"];
  taxCode: Profile["taxCode"];
  hasStudentLoan: boolean;
  kiwiSaverMember: boolean;
  kiwiSaverRate: string;
  kiwiSaverBalance: string;
  kiwiSaverJoinedOn: string;
  firstHomeBuyer: boolean;
  region: NonNullable<Profile["region"]> | "";
  housingType: NonNullable<Profile["housingType"]> | "";
  householdSize: string;
  dependants: string;
  singleIncomeHousehold: boolean;
  dateOfBirth: string;
  savingsInterestRate: string;
  targetSavingsRate: string;
};

function toForm(profile: Profile): Form {
  return {
    employmentType: profile.employmentType,
    payFrequency: profile.payFrequency === "ANNUALLY" ? "MONTHLY" : profile.payFrequency,
    taxCode: profile.taxCode,
    hasStudentLoan: profile.hasStudentLoan,
    kiwiSaverMember: profile.kiwiSaverMember,
    kiwiSaverRate: String(profile.kiwiSaverRate ?? 0.035),
    kiwiSaverBalance: centsToDollarsInput(profile.kiwiSaverBalance?.cents),
    kiwiSaverJoinedOn: profile.kiwiSaverJoinedOn ?? "",
    firstHomeBuyer: profile.firstHomeBuyer,
    region: profile.region ?? "",
    housingType: profile.housingType ?? "",
    householdSize: String(profile.householdSize),
    dependants: String(profile.dependants),
    singleIncomeHousehold: profile.singleIncomeHousehold,
    dateOfBirth: profile.dateOfBirth ?? "",
    savingsInterestRate: percent(profile.savingsInterestRate),
    targetSavingsRate: percent(profile.targetSavingsRate),
  };
}

function percent(fraction: number): string {
  return String(Math.round(fraction * 10_000) / 100);
}

const fieldNames: Record<string, string> = {
  kiwiSaverRateProvided: "kiwiSaverRate",
  payFrequencyAllowed: "payFrequency",
};

export function ProfilePage() {
  const { data: profile, isLoading, error, refetch } = useProfile();
  if (isLoading) return <PageSkeleton />;
  if (error || !profile)
    return <ErrorState message="We couldn't load your profile." onRetry={() => refetch()} />;
  return (
    <SettingsFrame
      title="Pay and tax"
      description="These details shape your tax, KiwiSaver and the size of your safety net."
    >
      <ProfileForm key={profile.onboardedAt ?? "new"} profile={profile} />
    </SettingsFrame>
  );
}

function ProfileForm({ profile }: { profile: Profile }) {
  const toast = useToast();
  const [form, setForm] = useState<Form>(() => toForm(profile));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const save = useApiMutation((request: ProfileRequest) => api.put<Profile>("/profile", request));

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function submit() {
    const request: ProfileRequest = {
      employmentType: form.employmentType,
      payFrequency: form.payFrequency,
      taxCode: form.taxCode,
      hasStudentLoan: form.hasStudentLoan,
      kiwiSaverMember: form.kiwiSaverMember,
      ...(form.kiwiSaverMember ? { kiwiSaverRate: Number(form.kiwiSaverRate) } : {}),
      ...(form.kiwiSaverBalance ? { kiwiSaverBalanceCents: parseDollars(form.kiwiSaverBalance) ?? 0 } : {}),
      ...(form.kiwiSaverJoinedOn ? { kiwiSaverJoinedOn: form.kiwiSaverJoinedOn } : {}),
      firstHomeBuyer: form.firstHomeBuyer,
      ...(form.region ? { region: form.region } : {}),
      ...(form.housingType ? { housingType: form.housingType } : {}),
      householdSize: Number(form.householdSize) || 1,
      dependants: Number(form.dependants) || 0,
      singleIncomeHousehold: form.singleIncomeHousehold,
      ...(form.dateOfBirth ? { dateOfBirth: form.dateOfBirth } : {}),
      savingsInterestRate: Number(form.savingsInterestRate) / 100,
      targetSavingsRate: Number(form.targetSavingsRate) / 100,
    };
    save.mutate(request, {
      onSuccess: () => {
        setErrors({});
        toast("Profile saved");
      },
      onError: (error) => {
        if (error instanceof ApiError && error.errors.length) {
          setErrors(Object.fromEntries(error.errors.map((e) => [fieldNames[e.field] ?? e.field, e.message])));
          toast("Check the highlighted fields", "error");
        } else toast(error.message, "error");
      },
    });
  }

  return (
    <form
      noValidate
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <Card>
        <CardHeader title="Work and pay" description="Used for PAYE, ACC and student loan deductions." />
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Employment" error={errors.employmentType}>
            {(props) => (
              <Select
                {...props}
                value={form.employmentType}
                onChange={(event) => set("employmentType", event.target.value as Form["employmentType"])}
              >
                {employmentTypes.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="How often you're paid" error={errors.payFrequency}>
            {(props) => (
              <Select
                {...props}
                value={form.payFrequency}
                onChange={(event) => set("payFrequency", event.target.value as Form["payFrequency"])}
              >
                {payFrequencies.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Tax code" error={errors.taxCode} hint="It's on your payslip.">
            {(props) => (
              <Select
                {...props}
                value={form.taxCode}
                onChange={(event) => set("taxCode", event.target.value as Form["taxCode"])}
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
        <Toggle
          className="mt-4"
          label="I'm repaying a student loan"
          hint="12% of income over the repayment threshold."
          checked={form.hasStudentLoan}
          onChange={(value) => set("hasStudentLoan", value)}
        />
      </Card>

      <Card>
        <CardHeader
          title="KiwiSaver"
          description="Your contributions, the government contribution and first-home withdrawals."
        />
        <Toggle
          label="I'm a KiwiSaver member"
          checked={form.kiwiSaverMember}
          onChange={(value) => set("kiwiSaverMember", value)}
        />
        {form.kiwiSaverMember ? (
          <div className="mt-4 grid gap-4 sm:grid-cols-3">
            <Field label="Contribution rate" error={errors.kiwiSaverRate}>
              {(props) => (
                <Select
                  {...props}
                  value={form.kiwiSaverRate}
                  onChange={(event) => set("kiwiSaverRate", event.target.value)}
                >
                  {kiwiSaverRates.map((rate) => (
                    <option key={rate} value={String(rate)}>
                      {Math.round(rate * 1000) / 10}%
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Current balance" hint="Optional" error={errors.kiwiSaverBalanceCents}>
              {(props) => (
                <MoneyInput
                  {...props}
                  value={form.kiwiSaverBalance}
                  onChange={(event) => set("kiwiSaverBalance", event.target.value)}
                />
              )}
            </Field>
            <Field label="Joined on" hint="For first-home eligibility" error={errors.kiwiSaverJoinedOn}>
              {(props) => (
                <Input
                  {...props}
                  type="date"
                  value={form.kiwiSaverJoinedOn}
                  onChange={(event) => set("kiwiSaverJoinedOn", event.target.value)}
                />
              )}
            </Field>
          </div>
        ) : null}
        <Toggle
          className="mt-4"
          label="I'm saving for my first home"
          checked={form.firstHomeBuyer}
          onChange={(value) => set("firstHomeBuyer", value)}
        />
      </Card>

      <Card>
        <CardHeader
          title="Household"
          description="Sizes your emergency fund and puts your costs in context."
        />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Region" error={errors.region}>
            {(props) => (
              <Select
                {...props}
                value={form.region}
                onChange={(event) => set("region", event.target.value as Form["region"])}
              >
                <option value="">Choose a region</option>
                {regions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Housing" error={errors.housingType}>
            {(props) => (
              <Select
                {...props}
                value={form.housingType}
                onChange={(event) => set("housingType", event.target.value as Form["housingType"])}
              >
                <option value="">Choose one</option>
                {housingTypes.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="People in household" error={errors.householdSize}>
            {(props) => (
              <Input
                {...props}
                inputMode="numeric"
                value={form.householdSize}
                onChange={(event) => set("householdSize", event.target.value)}
              />
            )}
          </Field>
          <Field label="Dependants" error={errors.dependants}>
            {(props) => (
              <Input
                {...props}
                inputMode="numeric"
                value={form.dependants}
                onChange={(event) => set("dependants", event.target.value)}
              />
            )}
          </Field>
        </div>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <Toggle
            label="We rely on one income"
            checked={form.singleIncomeHousehold}
            onChange={(value) => set("singleIncomeHousehold", value)}
          />
          <Field label="Date of birth" hint="Optional" error={errors.dateOfBirth}>
            {(props) => (
              <Input
                {...props}
                type="date"
                value={form.dateOfBirth}
                onChange={(event) => set("dateOfBirth", event.target.value)}
              />
            )}
          </Field>
        </div>
      </Card>

      <Card>
        <CardHeader title="Saving" description="Used when we project your goals and suggest a budget." />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Savings target (% of income)" error={errors.targetSavingsRate}>
            {(props) => (
              <Input
                {...props}
                inputMode="decimal"
                value={form.targetSavingsRate}
                onChange={(event) => set("targetSavingsRate", event.target.value)}
              />
            )}
          </Field>
          <Field label="Savings account interest rate (%)" error={errors.savingsInterestRate}>
            {(props) => (
              <Input
                {...props}
                inputMode="decimal"
                value={form.savingsInterestRate}
                onChange={(event) => set("savingsInterestRate", event.target.value)}
              />
            )}
          </Field>
        </div>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" size="lg" disabled={save.isPending}>
          {save.isPending ? "Saving" : "Save profile"}
        </Button>
      </div>
    </form>
  );
}

function Toggle({
  label,
  hint,
  checked,
  onChange,
  className,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (value: boolean) => void;
  className?: string;
}) {
  return (
    <label
      className={`flex items-center justify-between gap-3 rounded-2xl border border-line p-3 ${className ?? ""}`}
    >
      <span>
        <span className="block font-semibold">{label}</span>
        {hint ? <span className="block text-xs text-muted">{hint}</span> : null}
      </span>
      <Switch checked={checked} onCheckedChange={onChange} aria-label={label} />
    </label>
  );
}
