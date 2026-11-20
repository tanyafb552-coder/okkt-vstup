"""Збирає data/data.json з базових даних і зібраних матеріалів.

python build_data.py

Вхід (data/research/):
  base.json        спеціальності, події, посилання (зібрано вручну з сайту коледжу, формат версії 2)
  programs.json    освітньо-професійні програми з документів ОПП (зібрано і стиснуто ШІ, див. programs_notes.md)
  prices.json      вартість навчання 2026/2027 з розцінок коледжу
  stats_2025.json  конкурс 2025 року з Вступ.ОСВІТА.UA (якщо є)
Вихід: data/data.json і копія в app/app/src/main/assets/data.json.
Ідентифікатор першої програми спеціальності дорівнює ідентифікатору спеціальності, щоб обране,
збережене до появи програм, залишалося дійсним.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RESEARCH = os.path.join(HERE, "research")
VERSION = 4


def load(name, default=None):
    path = os.path.join(RESEARCH, name)
    if not os.path.exists(path):
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


base = load("base.json")
programs_research = load("programs.json", {"programs": []})["programs"]
prices = load("prices.json", {"prices": []})["prices"]
stats = load("stats_2025.json", {"offers": []})["offers"]


def price_for(code, form):
    for row in prices:
        if row["specialty"] == code and row["form"] == form:
            return row["uah_per_year"]
    return None


def stats_for(code, program_name, base_class, form, single_program):
    rows = [r for r in stats if r.get("specialty") == code and r.get("base") == base_class and r.get("form") == form]
    if not single_program:
        def norm(text):
            return (text or "").replace("’", "'").replace("ʼ", "'").strip().lower()
        rows = [r for r in rows if norm(r.get("program")) == norm(program_name)]
    if len(rows) != 1:
        return None, None
    return rows[0].get("budget_places"), rows[0].get("applications")


specialties = []
for old in base["specialties"]:
    code = old["code"]
    found = [p for p in programs_research if p.get("specialty") == code]
    if not found:
        found = [{"name": old["name"], "qualification": old["qualification"]}]
    programs = []
    for index, research in enumerate(found):
        program_id = old["id"] if index == 0 else research["id"]
        name = research["name"]
        offers = []
        for offer in old["offers"]:
            if offer["form"] == "заочна" and "дизайн" in name.lower():
                continue
            budget, applications = stats_for(code, name, offer["base"], offer["form"], len(found) == 1)
            offers.append({
                "base": offer["base"],
                "form": offer["form"],
                "duration_months": offer["duration_months"],
                "price_per_year": price_for(code, offer["form"]),
                "budget_places_last_year": budget,
                "applications_last_year": applications,
            })
        programs.append({
            "id": program_id,
            "name": name,
            "qualification": research.get("qualification") or old["qualification"],
            "credits": research.get("credits"),
            "features": research.get("features") or "",
            "practice": research.get("practice") or "",
            "further_study": research.get("further_study") or "",
            "doc_url": research.get("doc_url") or "",
            "offers": offers,
            "components": research.get("components") or old.get("components", []),
            "positions": research.get("positions") or old.get("positions", []),
            "skills": research.get("skills") or [],
            "tools": research.get("tools") or [],
        })
    specialties.append({
        "id": old["id"],
        "code": code,
        "name": old["name"],
        "description": old.get("description", ""),
        "site_url": old.get("site_url", ""),
        "programs": programs,
    })

budget_places = sum(r.get("budget_places") or 0 for r in stats)
min_price = min((r["uah_per_year"] for r in prices if r.get("uah_per_year")), default=None)
program_count = sum(len(s["programs"]) for s in specialties)
college = {
    "intro": "ВСП «Одеський фаховий коледж комп'ютерних технологій» – структурний підрозділ Одеського національного "
             "університету імені І. І. Мечникова. Коледж веде свою історію від ремісничого училища «Труд», заснованого в 1864 році, "
             "де навчався письменник Ілля Ільф, і сьогодні готує фахових молодших бакалаврів з IT, інженерії та менеджменту.",
    "facts": [
        {"value": "1864", "label": "рік заснування (училище «Труд»)"},
        {"value": f"{len(specialties)}", "label": f"спеціальностей, {program_count} освітніх програм"},
        {"value": f"{budget_places}", "label": "бюджетних місць на вступі 2025 року"},
        {"value": f"від {min_price:,}".replace(",", " ") if min_price else "—", "label": "грн/рік – вартість контракту 2026/2027"},
    ],
    "advantages": [
        {"title": "Диплом ОНУ імені І. І. Мечникова",
         "text": "Коледж – структурний підрозділ університету; випускники отримують ступінь фахового молодшого бакалавра і можуть продовжити навчання на бакалавраті."},
        {"title": "Вступ після 9 класу за співбесідою",
         "text": "Вступники на основі базової середньої освіти проходять співбесіду з української мови та математики; після 11 класу можна вступати за співбесідою або НМТ."},
        {"title": "Бюджетні місця на кожній спеціальності",
         "text": f"У 2025 році бюджетні місця були на всіх шести спеціальностях, разом {budget_places}."},
        {"title": "Сучасні технології в навчанні",
         "text": "Мови програмування, бази даних, САПР SolidWorks і Fusion 360, 3D-друк, верстати з ЧПУ, робототехніка – залежно від програми."},
        {"title": "Денна і заочна форми",
         "text": "Денна форма – для всіх спеціальностей, заочна – для частини з них після 11 класу."},
        {"title": "Практика за фахом",
         "text": "Програми технічних спеціальностей містять навчальну й виробничу практику, тож перші професійні навички з'являються ще під час навчання."},
    ],
}

result = {
    "version": VERSION,
    "updated_at": base["updated_at"],
    "specialties": specialties,
    "events": base["events"],
    "links": base.get("links", []),
    "college": college,
}
text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
for path in (os.path.join(HERE, "data.json"), os.path.join(HERE, "..", "app", "app", "src", "main", "assets", "data.json")):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
print("programs:", sum(len(s["programs"]) for s in specialties), "specialties:", len(specialties))
