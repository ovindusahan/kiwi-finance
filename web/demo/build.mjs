// Builds the web app into one self-contained HTML file that runs on recorded demo data, with no
// server. Record the data first with demo/record-fixtures.mjs.
import { build } from "esbuild";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import postcss from "postcss";
import tailwind from "@tailwindcss/postcss";

const web = path.resolve(import.meta.dirname, "..");
const out = process.argv[2] ?? path.join(web, "demo", "dist", "index.html");
const fixtures = process.argv[3] ?? path.join(web, "demo", "fixtures.json");

const bundle = await build({
  absWorkingDir: web,
  entryPoints: ["demo/main.tsx"],
  bundle: true,
  minify: true,
  write: false,
  format: "iife",
  target: "es2022",
  jsx: "automatic",
  legalComments: "none",
  alias: { "next/link": "./demo/shims/next-link.tsx", "next/navigation": "./demo/shims/next-navigation.ts" },
  define: {
    "process.env.NODE_ENV": '"production"',
    "process.env.NEXT_PUBLIC_SHOW_DEMO_ACCOUNT": '"true"',
  },
});
const js = bundle.outputFiles[0].text.replace(/<\/script/gi, "<\\/script");
const data = (await readFile(fixtures, "utf8")).replace(/<\/script/gi, "<\\/script");

const globals = await readFile(path.join(web, "src/app/globals.css"), "utf8");
const css = (
  await postcss([tailwind({ base: web })]).process(globals, { from: path.join(web, "src/app/globals.css") })
).css;

async function face(family, file, range) {
  const data = await readFile(path.join(web, "node_modules", file));
  return `@font-face{font-family:"${family}";font-style:normal;font-display:swap;font-weight:100 900;src:url(data:font/woff2;base64,${data.toString("base64")}) format("woff2-variations");unicode-range:${range}}`;
}
const latin =
  "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD";
const latinExt =
  "U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF";
const fonts = (
  await Promise.all([
    face("Inter Variable", "@fontsource-variable/inter/files/inter-latin-wght-normal.woff2", latin),
    face("Inter Variable", "@fontsource-variable/inter/files/inter-latin-ext-wght-normal.woff2", latinExt),
  ])
).join("");

const html = `<title>Kiwi Finance</title>
<meta name="description" content="Personal finance built for New Zealand.">
<style>${fonts}${css}</style>
<div id="root"></div>
<script>window.KIWI_DEMO_DATA = ${data};</script>
<script>${js}</script>
`;
await import("node:fs").then((fs) => fs.mkdirSync(path.dirname(out), { recursive: true }));
await writeFile(out, html);
console.log(`${out} ${(html.length / 1024).toFixed(0)} KB`);
