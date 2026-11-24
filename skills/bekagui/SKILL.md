---
name: bekagui
description: Build Minecraft inventory GUIs (Spigot/Paper/Folia plugins) with the BekaGUI library. Use when the user asks for a menu, GUI, shop, inventory screen, paged list or confirm dialog in a plugin that depends on BekaGUI (package dev.bekagui), or wants a menu written in YAML.
---

# BekaGUI

A tiny GUI library whose one big rule is: **menus never close to switch.** Same size menus are updated in place
(items and title), other sizes are opened over the old window without a close packet, so there is no flash and the
mouse does not jump. Works on Spigot, Paper and Folia, 1.14 to latest, one jar.

## Setup (once)

```java
@Override
public void onEnable() {
    BekaGUI.init(this);
}
```

Shade it and **always relocate** `dev.bekagui` (see README).

## The 6 types you need

| Type | What |
|---|---|
| `Menu` | Template. `Menu.builder()...build()`, then `menu.open(player)`. Build once, reuse. |
| `Item` | Item builder: `Item.of(Material.X).name("&a..").lore("..").glow().model(123).amount(5)` |
| `Button` | Item + click: `Button.of(item, click -> ...)`, `Button.display(item)` (not clickable) |
| `Click` | In a click handler: `player()`, `view()`, `isLeft()/isRight()/isShift()`, `open(menu)`, `back()`, `close()`, `update()`, `title(..)` |
| `View` | One player's open menu: `set(slot or key, button)`, `put/get` state, `title(..)`, `update()`, `paginate(..)`, `next()/previous()` |
| `YamlMenus` | Menus from YAML files, no Java |
| `TextInput` | Ask for text in an anvil window (opens over the menu, no close) |

## Pattern 1: static menu with a layout

```java
Menu menu = Menu.builder()
    .title("&8Shop")
    .layout(                       // 9 chars per row, 1-6 rows, '.' = empty, spaces ignored
        "#########",
        "#..D.E..#",
        "####X####")
    .item('#', Item.of(Material.GRAY_STAINED_GLASS_PANE).name(" "))
    .button('D', Item.of(Material.DIAMOND).name("&bDiamond"), click -> buy(click.player(), "diamond"))
    .button('E', Item.of(Material.EMERALD).name("&aMore"), click -> click.open(otherMenu))
    .button('X', Item.of(Material.BARRIER).name("&cClose"), Click::close)
    .build();
```

## Pattern 2: dynamic content (per player / changing)

Put anything that depends on the player or on time in `render`. It runs on open and on every `update()`.
Only changed slots are sent.

```java
Menu profile = Menu.builder()
    .title("&8Profile")
    .rows(3)
    .render(view -> {
        Player p = view.player();
        view.set(13, Button.display(Item.of(Material.GOLD_INGOT).name("&6Coins: " + coins(p))));
    })
    .refresh(20)                   // optional: re-render every second
    .build();
```

## Pattern 3: state + live update (amount picker)

```java
.render(view -> {
    int amount = view.get("amount", 1);
    view.title("&8Buy x" + amount);                          // title changes in place
    view.set('+', Button.of(Item.of(Material.LIME_DYE).name("&a+1"), click -> {
        click.view().put("amount", amount + 1);
        click.update();                                       // re-render, no reopen
    }));
})
```

State lives in the `View` (one per opening). `click.back()` returns to the previous view **with its state**.

## Pattern 4: pages

```java
List<Button> entries = ...;               // build the buttons
Menu list = Menu.builder()
    .layout(
        "xxxxxxxxx",
        "xxxxxxxxx",
        "...p.n...")
    .render(view -> {
        view.paginate('x', entries);      // fills all 'x' slots with the current page
        view.title("&8List " + (view.page() + 1) + "/" + view.pageCount());
        if (view.hasPrevious()) view.set('p', Button.of(Item.of(Material.ARROW).name("&ePrev"), c -> c.view().previous()));
        if (view.hasNext())     view.set('n', Button.of(Item.of(Material.ARROW).name("&eNext"), c -> c.view().next()));
    })
    .build();
```

## Pattern 5: confirm dialog that returns

```java
Menu confirm = Menu.builder()
    .title("&8Are you sure?")
    .layout(".........", "..Y...N..", ".........")
    .button('Y', Item.of(Material.LIME_WOOL).name("&aYes"), click -> { doIt(click.player()); click.back(); })
    .button('N', Item.of(Material.RED_WOOL).name("&cNo"), Click::back)
    .build();
```

## Pattern 6: text input (anvil)

```java
TextInput.create()
    .title("&8Search")                    // or .titleComponent(component) on Paper
    .icon(Item.of(Material.PAPER).lore("&7Type a name"))   // left slot; lore = hints
    .onSubmit((player, text) -> {         // player clicked the result slot
        if (text.isEmpty()) return;       // window stays open, they can type again
        results(text).open(player);       // switches without closing
    })
    .onCancel(player -> mainMenu.open(player))   // ESC
    .open(player);
```

The menu before the input stays in history, so `click.back()` in the next menu returns to it.
Needs Paper, or Spigot 1.21+.

## Component titles (Paper)

`.titleComponent(Component)` on `Menu.Builder`, `View` and `TextInput`, e.g. custom-font texture titles.
Strings (`title("&8..")`) work everywhere.

## Pattern 7: YAML (for non-programmers)

```java
YamlMenus menus = YamlMenus.load(this, new File(getDataFolder(), "menus"));
menus.open(player, "shop");               // menus/shop.yml
```

```yaml
title: "&8Shop"
layout: ["#########", "#..A.B..#", "#########"]
items:
  "#": { material: GRAY_STAINED_GLASS_PANE, name: " " }
  A:
    material: DIAMOND
    name: "&bDiamond for {player}"
    lore: ["&7Left: buy", "&7Right: info"]
    left-actions: ["console: give {player} diamond 1", "sound: entity.experience_orb.pickup"]
    right-actions: ["message: &7Costs 100 coins"]
  B: { material: BARRIER, name: "&cClose", actions: [close] }
```

Keys per item: `material`, `name`, `lore`, `amount`, `model`, `glow`, `slot`/`slots` (instead of a layout key),
`actions`, `left-actions`, `right-actions`. Menu keys: `title`, `layout` or `rows`, `refresh`.
Actions: `message: ..`, `command: ..` (as player), `console: ..`, `open: <id>`, `back`, `close`, `refresh`,
`title: ..`, `sound: <key like entity.experience_orb.pickup>`. `{player}` works everywhere; add PlaceholderAPI with
`menus.placeholders(PlaceholderAPI::setPlaceholders)`.

## Rules that prevent bugs

- Build `Menu`s once (fields), open many times. Do not rebuild a menu on every click; use `render` + `update()`.
- Switch menus with `click.open(menu)`, never `player.closeInventory()` + open. Close only with `click.close()`.
- Inside `render`, `view.set` builds the screen. Outside `render`, `view.set` changes the open window until the next `update()`.
- Clicks and `render` run on the player's thread (Folia safe). From async code just call `menu.open(player)`; BekaGUI hops threads.
- Do not keep `View` after the menu closed; use `onClose(view -> ...)` to save things.
- Colors: `&a`, `&l`, hex `&#FF8800`. All `Item` texts are colored automatically.
- Default click cooldown is 100 ms (stops double buys). Change with `BekaGUI.clickCooldown(ms)`.
- Items in the player's own inventory can be moved, but nothing can be moved into the menu. `.lockPlayerInventory(true)` blocks all.
- Slots: 0 = top left, row r column c = `r * 9 + c`.

## Errors you may see

- `Call BekaGUI.init(this) ...` -> add `BekaGUI.init(this)` to `onEnable`.
- `Layout row N must have 9 characters` -> fix that row (spaces do not count).
- `Key 'X' is bound but does not appear in the layout` -> typo between `layout(...)` and `button('X', ...)`.
- YAML problems are printed as `BekaGUI: menu 'file.yml' skipped: <reason>` and the file is skipped.
