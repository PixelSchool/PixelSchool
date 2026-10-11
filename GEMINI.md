# PixelSchool Guidelines

## Project Goals
- Modern, clean Android application for school diary (edu.rk.gov.ru / ElJur).
- Design system: Material 3 Expressive (https://m3.material.io/).
- Key features (v1): Gosuslugi (ESIA) auth via WebView/session intercept, Schedule/Timetable, Homework, Marks.

## Engineering Rules
- Host OS: Windows. Package manager: winget.
- Keep codebase minimal, modular and free of bloat (YAGNI, Ponytail principles).
- All git commits must be cryptographically signed via GPG and verified.
- Commit message format: `X.X.X description of changes`.
