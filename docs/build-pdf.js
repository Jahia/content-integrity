#!/usr/bin/env node
/*
 * Builds docs/embedded-tests.pdf, git-ignored, from docs/embedded-tests.md, with its screenshots: the Markdown is
 * rendered to HTML, then printed to PDF by a headless Chrome. Run it with `yarn docs:pdf`.
 *
 * - The headings get the anchors GitHub gives them, so that the summary and the links between the sections work in the PDF.
 * - The links to the other pages of the documentation point to GitHub, where they are published.
 * - Each check starts on a new page.
 *
 * CHROME_PATH sets the Chrome or Chromium executable, found in its default locations otherwise.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const {execFileSync} = require('child_process');
const {pathToFileURL} = require('url');
const {marked} = require('marked');

const DOCS = __dirname;
const SOURCE = path.join(DOCS, 'embedded-tests.md');
const PDF = path.join(DOCS, 'embedded-tests.pdf');
const PUBLISHED_DOCS = 'https://github.com/Jahia/content-integrity/blob/master/docs/';

const CHROME_LOCATIONS = [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Chromium.app/Contents/MacOS/Chromium',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser'
];

const findChrome = () => {
    const chrome = process.env.CHROME_PATH || CHROME_LOCATIONS.find(location => fs.existsSync(location));
    if (!chrome) {
        throw new Error('Chrome not found: set CHROME_PATH to the Chrome or Chromium executable');
    }

    return chrome;
};

// The anchor GitHub gives a heading: its text in lower case, without the punctuation, the spaces replaced by dashes
const slugOf = text => text.replace(/<[^>]*>/g, '').trim().toLowerCase().replace(/[^\w\- ]/g, '').replace(/ /g, '-');

const renderer = new marked.Renderer();
renderer.heading = (text, level, raw) => `<h${level} id="${slugOf(raw)}">${text}</h${level}>\n`;
renderer.link = (href, title, text) => {
    // The other pages of the documentation are not part of the PDF
    const target = href && !/^([a-z]+:|#)/.test(href) ? new URL(href, PUBLISHED_DOCS).href : href;
    return `<a href="${target}"${title ? ` title="${title}"` : ''}>${text}</a>`;
};

const STYLE = `
    @page { size: A4; margin: 15mm 12mm; }
    body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #1f2328; }
    h1 { font-size: 20pt; border-bottom: 1px solid #d1d9e0; padding-bottom: 4pt; }
    h2 { font-size: 16pt; border-bottom: 1px solid #d1d9e0; padding-bottom: 3pt; page-break-before: always; }
    h3 { font-size: 13pt; }
    h4 { font-size: 11.5pt; }
    h2, h3, h4 { page-break-after: avoid; }
    img { max-width: 100%; border: 1px solid #d1d9e0; page-break-inside: avoid; }
    p:has(> img) { page-break-inside: avoid; }
    code { font-family: SFMono-Regular, Consolas, monospace; font-size: 9pt; background: #f6f8fa; padding: 1pt 3pt; border-radius: 3pt; }
    pre { background: #f6f8fa; padding: 6pt; overflow-wrap: anywhere; white-space: pre-wrap; }
    pre code { padding: 0; }
    table { border-collapse: collapse; width: 100%; font-size: 9pt; page-break-inside: avoid; }
    th, td { border: 1px solid #d1d9e0; padding: 3pt 5pt; text-align: left; vertical-align: top; }
    th { background: #f6f8fa; }
    a { color: #0969da; text-decoration: none; }
`;

const markdown = fs.readFileSync(SOURCE, 'utf8');
const body = marked.parse(markdown, {renderer, gfm: true});
// The HTML is written to a temporary folder: its images are resolved from the docs folder
const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<base href="${pathToFileURL(DOCS + path.sep).href}">
<title>Content Integrity - Embedded tests</title>
<style>${STYLE}</style>
</head>
<body>
${body}
</body>
</html>
`;

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'content-integrity-pdf-'));
try {
    const htmlFile = path.join(work, 'embedded-tests.html');
    fs.writeFileSync(htmlFile, html);
    execFileSync(findChrome(), [
        '--headless=new',
        '--disable-gpu',
        '--no-pdf-header-footer',
        '--allow-file-access-from-files',
        `--user-data-dir=${path.join(work, 'chrome')}`,
        `--print-to-pdf=${PDF}`,
        pathToFileURL(htmlFile).href
    ], {stdio: ['ignore', 'ignore', 'pipe']});
} finally {
    fs.rmSync(work, {recursive: true, force: true});
}

console.log(`Written ${path.relative(process.cwd(), PDF)}`);
