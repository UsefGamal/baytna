# Play Store listing

Everything needed to fill in the Play Console for `com.baytna.app`. Text is ready to paste; limits are Play's.

## Store settings

| Field | Value |
|---|---|
| App name (30) | Baytna: Household Budget |
| Arabic name (30) | بيتنا: ميزانية البيت |
| Category | Finance |
| Tags | Budgeting, Personal finance, Expense tracker |
| Price | Free, no ads, no in-app purchases |
| Contact email | Yusuf's support address (set in the console) |
| Privacy policy URL | `https://github.com/UsefGamal/baytna/blob/main/docs/PRIVACY_POLICY.md` (or the same file on GitHub Pages) |
| Default language | English (United States); add Arabic as a translation |

## Short description (80)

**English:** Plan and track your household money together: budgets, goals, shared accounts.

**Arabic:** خططوا وتابعوا مصروف البيت معاً: ميزانيات وأهداف وحسابات مشتركة.

## Full description (4000)

### English

Baytna is a budget app for couples who run one home together.

Record income, expenses and transfers in seconds, in Egyptian pounds, and see where the month stands: what came in, what went out, and what is left in every account.

**Plan the month**
- Set a monthly budget with category limits and zero-based planning: give every pound a job.
- Warnings before a category runs over, and an optional rollover to next month.
- Recurring rent, salary, bills and savings transfers, confirmed with one tap when they are due.

**Save for what matters**
- Savings goals with a target and date, and a clear view of progress.
- Yearly reports by month, category and person.

**Built for two**
- Shared and personal spending side by side, so each of you keeps some privacy.
- Turn on sync with an email account and invite your partner with a code. Both phones stay up to date, even after time offline.
- An activity feed shows who changed what.

**Yours, on your phone**
- Works fully offline. Without sync, nothing leaves your phone.
- Back up everything to a file and restore it on a new phone.
- Export and import transactions as CSV for spreadsheets.
- Arabic and English, light and dark themes, screen reader support.

Free, no ads, no tracking.

### Arabic

بيتنا تطبيق ميزانية للأزواج الذين يديرون بيتاً واحداً معاً.

سجّلوا الدخل والمصروفات والتحويلات في ثوانٍ بالجنيه المصري، واعرفوا وضع الشهر: ما دخل، وما خرج، وما تبقى في كل حساب.

**خطّطوا للشهر**
- ميزانية شهرية بحدود لكل فئة وتخطيط صفري: لكل جنيه مهمة.
- تنبيه قبل أن تتجاوز الفئة حدها، وإمكانية ترحيل الباقي للشهر التالي.
- الإيجار والراتب والفواتير والتحويلات للادخار المتكررة، تؤكدونها بلمسة واحدة عند موعدها.

**ادّخروا لما يهمكم**
- أهداف ادخار بمبلغ وتاريخ، مع متابعة واضحة للتقدم.
- تقارير سنوية حسب الشهر والفئة والشخص.

**مصمم لشخصين**
- المصروف المشترك والشخصي جنباً إلى جنب، ليحتفظ كل منكما بخصوصيته.
- فعّلوا المزامنة بحساب بريد إلكتروني وادعوا شريككم برمز. يبقى الهاتفان محدّثين حتى بعد العمل دون إنترنت.
- سجل النشاط يوضح من غيّر ماذا.

**بياناتكم على هاتفكم**
- يعمل بالكامل دون إنترنت. بدون المزامنة لا يغادر شيء هاتفكم.
- نسخ احتياطي كامل إلى ملف واستعادته على هاتف جديد.
- تصدير واستيراد المعاملات كملف CSV لجداول البيانات.
- العربية والإنجليزية، الوضع الفاتح والداكن، ودعم قارئ الشاشة.

مجاني، بلا إعلانات، بلا تتبع.

## Graphics

| Asset | Size | Notes |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit | Export of the launcher icon on its background color, no rounded corners (Play adds them). |
| Feature graphic | 1024 × 500 PNG/JPG | App name and tagline ("Our money, our plans.") on the brand color, one phone frame showing Home. No text near the edges. |
| Phone screenshots | 1080 × 2340 (portrait), 2 to 8 per language | List below. Use the same sample household in both languages. |

### Screenshot list

Take each in English and in Arabic (RTL), light theme, with a sample household ("Home": Cash, Bank, Savings; a month of realistic EGP entries; one goal; two recurring items). Dark theme for one or two to show it exists.

1. **Home** with this month's balance, cash flow and recent entries. Caption: "See the month at a glance."
2. **Add transaction** with the amount keypad open on an expense. Caption: "Add an expense in seconds."
3. **Budgets** with category limits, one line near its limit showing the warning. Caption: "Plan every pound."
4. **Insights** with the six-month trend and category breakdown. Caption: "Know where the money goes."
5. **Savings goal** detail with progress. Caption: "Save for what matters."
6. **Recurring** list with one item due. Caption: "Never forget rent or bills."
7. **Sync and invite** screen showing both members. Caption: "One budget, two phones."
8. **Backup and data** screen (dark theme). Caption: "Your data, backed up your way."

Arabic captions: 1 "الشهر في نظرة واحدة" · 2 "أضف مصروفاً في ثوانٍ" · 3 "لكل جنيه مهمة" · 4 "اعرف أين يذهب المال" · 5 "ادّخر لما يهمك" · 6 "لا تنسَ الإيجار أو الفواتير" · 7 "ميزانية واحدة على هاتفين" · 8 "بياناتك محفوظة بطريقتك"

## Data safety form

Answers match the current build (Firebase Auth and Firestore only; no analytics, crash reporting or ads SDKs).

| Question | Answer |
|---|---|
| Does the app collect or share user data? | Yes, collects. Not shared with third parties. |
| Encrypted in transit? | Yes (Firebase uses TLS). |
| Can users request deletion? | Yes, see "Account deletion" below. |
| **Personal info: Email address** | Collected, only when sync is turned on. Purpose: account management. Required for sync. |
| **Personal info: Name** | Collected (member display names), only with sync. Purpose: app functionality. |
| **Personal info: User IDs** | Collected (Firebase user id), only with sync. Purpose: account management. |
| **Financial info: Other financial info** | Collected (accounts, transactions, budgets, goals), only with sync. Purpose: app functionality. Processed to sync between the household's phones. |
| Location, contacts, photos, device ids, app activity, diagnostics | Not collected. |
| Data processed ephemerally? | No. |
| Collection optional? | Yes: the app works fully offline without an account. |

## Other console declarations

- **Ads:** No ads.
- **Content rating (IARC):** Utility/productivity, no violence, no user-to-public communication, no gambling. Expected rating: Everyone / 3+.
- **Target audience:** 18 and over (household finance). Not designed for children.
- **Financial features:** "Budgeting or expense tracking" only. No loans, payments, crypto, banking or investing.
- **App access:** Everything works without signing in. For review of sync, give reviewers a test email and password for a household with sample data.
- **Government / health / news apps:** No.
- **Permissions:** `POST_NOTIFICATIONS` (opt-in reminders), `INTERNET` (sync). No sensitive permissions.

## Account deletion

Play asks for an in-app path and a web link.

- **In the app:** More → Cloud sync → Delete my cloud account (asks for the password).
- **Web link for the console:** `https://github.com/UsefGamal/baytna/blob/main/docs/DELETE_ACCOUNT.md`
- **Data deleted:** the sign-in; the household's cloud data when the user is the last member. **Data kept:** shared data stays for a partner who is still in the household (it is theirs too); nothing else is retained.

## Release notes for the first Play release

**English:** First Play Store release: budgets, goals, recurring items, two-phone sync, backup and restore, CSV import and export.

**Arabic:** أول إصدار على متجر Play: ميزانيات، أهداف، بنود متكررة، مزامنة بين هاتفين، نسخ احتياطي واستعادة، واستيراد وتصدير CSV.

## Before uploading

- Build the signed AAB from a `vX.Y.Z` tag (see `RELEASING.md`). The upload key is the release key; enroll in Play App Signing when asked.
- Install the release APK on a clean phone and walk the screenshot list once in each language.
