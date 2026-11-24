# Notes for AI assistants

- How to use the library: [skills/bekagui/SKILL.md](skills/bekagui/SKILL.md). Copy that folder into your
  assistant's skills directory (e.g. `.claude/skills/bekagui/`) in projects that use BekaGUI.
- Code: `bekagui-core/src/main/java/dev/bekagui/`. Public API: `BekaGUI`, `Menu`, `Item`, `Button`, `Click`,
  `View`, `YamlMenus`, `Text`, `Layout`, `Pages`. Everything else is internal.
- The core rule lives in `Session`: never close the window to switch menus. Do not add `closeInventory()` to any
  switching path.
- Bytecode target is Java 8 and the API is compiled against Spigot 1.20.4 but must run on 1.14+ and on Folia:
  no `var`, `List.of`, records, switch expressions; Paper/Folia-only and `InventoryView` calls go through
  `Platform` (reflection).
- Build and test: `mvn package` (tests run in `bekagui-core`).
