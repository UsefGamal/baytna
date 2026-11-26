# UI/UX rules for Baytna (Android, Jetpack Compose)

A short version of the mobile UI/UX rulebook, translated for a native Compose app. Web-only items (CSS, `env()`, axe, WebView zoom) apply only in spirit.

## Check before shipping any screen

1. **Where it lives.** Five bottom tabs maximum (Home, Transactions, Budgets, Insights, More). A new feature goes on Home, inside a tab, or under More, never into a sixth tab.
2. **One primary action** per screen. Other actions are text buttons or top-bar icons.
3. **Phone first.** Works at 360, 390 and 412 dp, in English and Arabic (RTL), light and dark, and at 130% system font size. Long Arabic strings must wrap or ellipsize, never clip.
4. **Touch targets 48 dp or more** (Material minimum). Use `IconButton` or pad custom tappables with `Modifier.size(48.dp)` around a smaller visual.
5. **Insets.** The app is edge to edge. `MainScaffold` pads and consumes system bars, so screens must not add status-bar or navigation-bar padding again. Screens outside the scaffold use `safeDrawingPadding()`.
6. **Contrast 4.5:1** for text in both themes. Light-theme finance colors (income, expense, warning, transfer) and `primary` were picked to pass on the background, cards and `surfaceContainerHigh`. Check new colors before using them for text.
7. **Never color alone.** Pair color with a sign, label, icon or tick (amounts carry +/−, budget warnings carry a label, the selected color swatch shows a tick).
8. **Every icon button has a `contentDescription`.** Decorative icons next to text use `null`.
9. **Directional icons use `Icons.AutoMirrored`** so they flip in Arabic. No literal arrows like "‹" in text.
10. **Strings live in `strings.xml` and `values-ar/strings.xml`.** Language names ("English", "العربية") are the only literals.
11. **Buttons are verbs** in sentence case ("Set date", "Save", "Remove"), not "OK".
12. **Destructive actions confirm and name what goes**, for example "Delete Groceries · EGP 250?". Prefer a restore path where one exists (deleted transactions can be restored).
13. **Empty states say what to do** ("No transactions this month. Tap + to add one.").
14. **Be liberal in input.** Amounts accept Arabic-Indic digits, "," or "." or "٫" as the decimal separator, and "," as a thousands separator ("1,250.50"). See `MoneyParser`.
15. **Numbers must never look wrong.** Cap progress bars, keep transfers out of cash flow, and test the maths in `:domain`.
16. **Nothing tappable that does nothing.** A chip or row that looks pressable must navigate or act.
