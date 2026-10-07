"""Renders the legal Markdown in this folder to HTML pages in docs/.

    python docs-internal/build-legal.py

The Markdown files are the source of truth; edit them, then re-run this and
commit both. Standard library only. Supports the subset the documents use:
front matter (title), headings, paragraphs, blockquotes, bullet lists (nested
by two spaces), tables, **bold**, *italic*, `code`, [links](url), and a
trailing backslash as a line break. "[PLACEHOLDER: ...]" is highlighted.
"""

from __future__ import annotations

import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HERE = ROOT / "docs" / "legal"  # Markdown sources
OUT = ROOT / "docs"  # served by GitHub Pages

PAGES = {
    "privacy-policy.md": "privacy.html",
    "terms-of-use.md": "terms.html",
}

STYLE = """
    :root {
      --bg: #f6f1ea; --text: #2a2420; --muted: #6b625a; --accent: #a65a3e;
      --line: #e3d9cc; --card: #fbf8f4; --warn-bg: #fff4d6; --warn-text: #6b4e00;
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --bg: #1d1a17; --text: #efe8df; --muted: #b3a99e; --accent: #e0957a;
        --line: #3a342e; --card: #24201c; --warn-bg: #3a3014; --warn-text: #f3d98a;
      }
    }
    * { box-sizing: border-box; }
    body { margin: 0; background: var(--bg); color: var(--text);
           font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
    main { max-width: 720px; margin: 0 auto; padding: 32px 16px 64px; }
    h1 { font-size: 1.9rem; line-height: 1.2; margin: 24px 0 8px; }
    h2 { font-size: 1.3rem; margin: 36px 0 8px; padding-top: 12px; border-top: 1px solid var(--line); }
    h3 { font-size: 1.05rem; margin: 24px 0 6px; }
    a { color: var(--accent); }
    ul { padding-left: 1.3em; }
    li + li { margin-top: 4px; }
    blockquote { margin: 0; padding: 12px 16px; border-radius: 12px;
                 background: var(--warn-bg); color: var(--warn-text); }
    blockquote p { color: var(--warn-text); margin: 0; }
    .table { overflow-x: auto; margin: 12px 0; }
    table { border-collapse: collapse; width: 100%; font-size: 0.95rem; }
    th, td { text-align: left; vertical-align: top; padding: 8px 10px; border: 1px solid var(--line); }
    th { background: var(--card); }
    code { font-size: 0.9em; background: var(--card); padding: 1px 4px; border-radius: 4px; }
    .placeholder { background: var(--warn-bg); color: var(--warn-text); border-radius: 4px;
                   padding: 0 4px; font-weight: 600; }
    footer { margin-top: 40px; color: var(--muted); font-size: 0.9rem; }
"""


def inline(text: str) -> str:
    out = html.escape(text, quote=False)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", r'<a href="\2">\1</a>', out)
    out = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])", r"<em>\1</em>", out)
    out = re.sub(r"\[PLACEHOLDER:[^\]]*\]", lambda m: f'<span class="placeholder">{m.group(0)}</span>', out)
    return out


BREAK = "\x00BR\x00"


def join_lines(lines: list[str]) -> str:
    """Joins a block's lines first, so **bold** and links may span lines."""
    parts = []
    for i, line in enumerate(lines):
        stripped = line.strip()
        hard_break = stripped.endswith("\\") and i < len(lines) - 1
        parts.append(stripped.rstrip("\\").rstrip() + (BREAK if hard_break else ""))
    joined = " ".join(parts).replace(BREAK + " ", BREAK)
    return inline(joined).replace(BREAK, "<br>\n")


def render_list(lines: list[str]) -> str:
    """Bullet lines (with continuation lines) nested by indentation."""
    items: list[tuple[int, list[str]]] = []
    for line in lines:
        m = re.match(r"^(\s*)- (.*)$", line)
        if m:
            items.append((len(m.group(1)), [m.group(2)]))
        else:
            items[-1][1].append(line.strip())

    def build(start: int, indent: int) -> tuple[str, int]:
        out, i = ["<ul>"], start
        while i < len(items) and items[i][0] >= indent:
            depth, text = items[i]
            if depth > indent:
                sub, i = build(i, depth)
                out[-1] = out[-1].removesuffix("</li>") + sub + "</li>"
                continue
            out.append(f"<li>{join_lines(text)}</li>")
            i += 1
        out.append("</ul>")
        return "".join(out), i

    return build(0, items[0][0])[0]


def render_table(lines: list[str]) -> str:
    rows = [[c.strip() for c in l.strip().strip("|").split("|")] for l in lines]
    head, body = rows[0], rows[2:]
    th = "".join(f"<th>{inline(c)}</th>" for c in head)
    trs = "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in body)
    return f'<div class="table"><table><thead><tr>{th}</tr></thead><tbody>{trs}</tbody></table></div>'


def render(md: str) -> tuple[str, str]:
    title = "Curated"
    m = re.match(r"^---\n(.*?)\n---\n", md, re.S)
    if m:
        for line in m.group(1).splitlines():
            if line.startswith("title:"):
                title = line.split(":", 1)[1].strip()
        md = md[m.end():]

    blocks: list[str] = []
    lines = md.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith("#"):
            level = len(line) - len(line.lstrip("#"))
            blocks.append(f"<h{level}>{inline(line[level:].strip())}</h{level}>")
            i += 1
        elif line.startswith(">"):
            chunk = []
            while i < len(lines) and lines[i].startswith(">"):
                chunk.append(lines[i][1:].strip())
                i += 1
            blocks.append(f"<blockquote><p>{join_lines(chunk)}</p></blockquote>")
        elif line.startswith("|"):
            chunk = []
            while i < len(lines) and lines[i].startswith("|"):
                chunk.append(lines[i])
                i += 1
            blocks.append(render_table(chunk))
        elif re.match(r"^\s*- ", line):
            chunk = []
            while i < len(lines) and lines[i].strip() and (re.match(r"^\s*- ", lines[i]) or lines[i].startswith("  ")):
                chunk.append(lines[i])
                i += 1
            blocks.append(render_list(chunk))
        else:
            chunk = []
            while i < len(lines) and lines[i].strip() and not re.match(r"^(#|>|\||\s*- )", lines[i]):
                chunk.append(lines[i])
                i += 1
            blocks.append(f"<p>{join_lines(chunk)}</p>")
    return title, "\n".join(blocks)


def page(title: str, body: str, source: str) -> str:
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex">
  <title>Curated {html.escape(title)} (draft)</title>
  <style>{STYLE}  </style>
</head>
<body>
<main>
<!-- Generated from docs/legal/{source} by docs-internal/build-legal.py. Edit the Markdown, not this file. -->
{body}
<footer>Curated · {html.escape(title)} · <a href="privacy.html">Privacy</a> · <a href="terms.html">Terms</a> · <a href="delete-account.html">Delete your account</a></footer>
</main>
</body>
</html>
"""


def main() -> None:
    for source, target in PAGES.items():
        title, body = render((HERE / source).read_text(encoding="utf-8"))
        (OUT / target).write_text(page(title, body, source), encoding="utf-8", newline="\n")
        print(f"docs/legal/{source} -> docs/{target}")


if __name__ == "__main__":
    main()
